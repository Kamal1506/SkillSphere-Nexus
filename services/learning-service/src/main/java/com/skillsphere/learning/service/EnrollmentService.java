package com.skillsphere.learning.service;

import com.skillsphere.learning.client.SkillServiceClient;
import com.skillsphere.learning.dto.CompleteEnrollmentRequest;
import com.skillsphere.learning.dto.CourseEnrollmentRequest;
import com.skillsphere.learning.dto.EnrollmentDTO;
import com.skillsphere.learning.entity.Course;
import com.skillsphere.learning.entity.Enrollment;
import com.skillsphere.learning.entity.EnrollmentStatus;
import com.skillsphere.learning.entity.LearningPath;
import com.skillsphere.learning.entity.LearningPathCourse;
import com.skillsphere.learning.entity.LearningPathProgress;
import com.skillsphere.learning.exception.DuplicateResourceException;
import com.skillsphere.learning.exception.ResourceNotFoundException;
import com.skillsphere.learning.repository.CourseRepository;
import com.skillsphere.learning.repository.EnrollmentRepository;
import com.skillsphere.learning.repository.LearningPathRepository;
import com.skillsphere.learning.repository.LearningPathProgressRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class EnrollmentService {

    private final EnrollmentRepository enrollmentRepository;
    private final CourseRepository courseRepository;
    private final LearningPathRepository learningPathRepository;
    private final LearningPathProgressRepository learningPathProgressRepository;
    private final SkillServiceClient skillServiceClient;

    public EnrollmentService(EnrollmentRepository enrollmentRepository,
                             CourseRepository courseRepository,
                             LearningPathRepository learningPathRepository,
                             LearningPathProgressRepository learningPathProgressRepository,
                             SkillServiceClient skillServiceClient) {
        this.enrollmentRepository = enrollmentRepository;
        this.courseRepository = courseRepository;
        this.learningPathRepository = learningPathRepository;
        this.learningPathProgressRepository = learningPathProgressRepository;
        this.skillServiceClient = skillServiceClient;
    }

    public List<EnrollmentDTO> getEnrollmentsByEmployee(UUID employeeId) {
        if (!skillServiceClient.employeeExists(employeeId)) {
            throw new ResourceNotFoundException("Employee not found with id: " + employeeId);
        }
        return enrollmentRepository.findByEmployeeId(employeeId).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public EnrollmentDTO createEnrollment(UUID employeeId, CourseEnrollmentRequest request) {
        if (!skillServiceClient.employeeExists(employeeId)) {
            throw new ResourceNotFoundException("Employee not found with id: " + employeeId);
        }

        if (request.courseId() == null && request.learningPathId() == null) {
            throw new IllegalArgumentException("Either Course ID or Learning Path ID must be provided");
        }
        if (request.courseId() != null && request.learningPathId() != null) {
            throw new IllegalArgumentException("Cannot enroll in both a course and a learning path simultaneously");
        }

        Enrollment enrollment = new Enrollment();
        enrollment.setEmployeeId(employeeId);
        enrollment.setStatus(EnrollmentStatus.ENROLLED);
        enrollment.setProgressPercent(0);
        enrollment.setEnrolledAt(Instant.now());
        enrollment.setUpdatedAt(Instant.now());

        if (request.courseId() != null) {
            Course course = courseRepository.findById(request.courseId())
                    .orElseThrow(() -> new ResourceNotFoundException("Course not found with id: " + request.courseId()));
            
            enrollmentRepository.findByEmployeeIdAndCourseId(employeeId, request.courseId())
                    .ifPresent(existing -> {
                        throw new DuplicateResourceException("Employee is already enrolled in this course");
                    });

            enrollment.setCourseId(request.courseId());
            enrollment.setCourse(course);
        } else {
            LearningPath path = learningPathRepository.findById(request.learningPathId())
                    .orElseThrow(() -> new ResourceNotFoundException("Learning path not found with id: " + request.learningPathId()));

            enrollmentRepository.findByEmployeeIdAndLearningPathId(employeeId, request.learningPathId())
                    .ifPresent(existing -> {
                        throw new DuplicateResourceException("Employee is already enrolled in this learning path");
                    });

            enrollment.setLearningPathId(request.learningPathId());
            enrollment.setLearningPath(path);
        }

        Enrollment saved = enrollmentRepository.save(enrollment);
        return mapToDTO(saved);
    }

    @Transactional
    public EnrollmentDTO updateProgress(UUID id, int progressPercent) {
        Enrollment enrollment = enrollmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Enrollment not found with id: " + id));

        enrollment.setProgressPercent(progressPercent);
        enrollment.setUpdatedAt(Instant.now());

        if (progressPercent == 100) {
            enrollment.setStatus(EnrollmentStatus.COMPLETED);
            enrollment.setCompletedAt(Instant.now());
        } else if (progressPercent > 0) {
            enrollment.setStatus(EnrollmentStatus.IN_PROGRESS);
            enrollment.setCompletedAt(null);
        } else {
            enrollment.setStatus(EnrollmentStatus.ENROLLED);
            enrollment.setCompletedAt(null);
        }

        Enrollment saved = enrollmentRepository.save(enrollment);
        
        // Recalculate learning path progresses if this was a course enrollment
        if (saved.getCourseId() != null) {
            recalculateLearningPathsProgress(saved.getEmployeeId(), saved.getCourseId());
        }

        return mapToDTO(saved);
    }

    @Transactional
    public EnrollmentDTO completeEnrollment(UUID id, CompleteEnrollmentRequest request) {
        Enrollment enrollment = enrollmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Enrollment not found with id: " + id));

        enrollment.setProgressPercent(100);
        enrollment.setStatus(EnrollmentStatus.COMPLETED);
        enrollment.setCompletedAt(Instant.now());
        enrollment.setFinalScore(request.finalScore());
        enrollment.setUpdatedAt(Instant.now());

        Enrollment saved = enrollmentRepository.save(enrollment);

        // Recalculate learning path progresses if this was a course enrollment
        if (saved.getCourseId() != null) {
            recalculateLearningPathsProgress(saved.getEmployeeId(), saved.getCourseId());
        }

        return mapToDTO(saved);
    }

    @Transactional
    public int calculatePathProgress(UUID employeeId, UUID pathId) {
        LearningPath path = learningPathRepository.findById(pathId)
                .orElseThrow(() -> new ResourceNotFoundException("Learning path not found with id: " + pathId));

        List<LearningPathCourse> pathCourses = path.getLearningPathCourses();
        if (pathCourses.isEmpty()) {
            savePathProgress(employeeId, pathId, 0);
            return 0;
        }

        List<UUID> courseIds = pathCourses.stream()
                .map(LearningPathCourse::getCourseId)
                .collect(Collectors.toList());

        List<Enrollment> enrollments = enrollmentRepository.findByEmployeeId(employeeId);
        long completedCount = enrollments.stream()
                .filter(e -> e.getCourseId() != null && courseIds.contains(e.getCourseId()))
                .filter(e -> e.getStatus() == EnrollmentStatus.COMPLETED)
                .count();

        int progressPercent = (int) ((completedCount * 100) / courseIds.size());
        savePathProgress(employeeId, pathId, progressPercent);
        return progressPercent;
    }

    private void savePathProgress(UUID employeeId, UUID pathId, int progressPercent) {
        LearningPathProgress progress = learningPathProgressRepository.findByEmployeeIdAndPathId(employeeId, pathId)
                .orElseGet(() -> {
                    LearningPathProgress p = new LearningPathProgress();
                    p.setEmployeeId(employeeId);
                    p.setPathId(pathId);
                    return p;
                });
        progress.setProgressPercent(progressPercent);
        progress.setUpdatedAt(Instant.now());
        learningPathProgressRepository.save(progress);
    }

    private void recalculateLearningPathsProgress(UUID employeeId, UUID courseId) {
        List<LearningPath> allPaths = learningPathRepository.findAll();
        for (LearningPath lp : allPaths) {
            boolean hasCourse = lp.getLearningPathCourses().stream()
                    .anyMatch(lpc -> lpc.getCourseId().equals(courseId));
            if (hasCourse) {
                calculatePathProgress(employeeId, lp.getId());
            }
        }
    }

    public Map<String, Object> getDashboardStats(UUID employeeId) {
        if (!skillServiceClient.employeeExists(employeeId)) {
            throw new ResourceNotFoundException("Employee not found with id: " + employeeId);
        }
        
        long activeCount = enrollmentRepository.countActiveEnrollmentsByEmployeeId(employeeId);
        long completedCourses = enrollmentRepository.countCompletedCoursesByEmployeeId(employeeId);
        long completedPaths = enrollmentRepository.countCompletedPathsByEmployeeId(employeeId);

        Map<String, Object> stats = new HashMap<>();
        stats.put("activeEnrollmentsCount", activeCount);
        stats.put("completedCoursesCount", completedCourses);
        stats.put("completedLearningPathsCount", completedPaths);
        return stats;
    }

    private EnrollmentDTO mapToDTO(Enrollment e) {
        return new EnrollmentDTO(
                e.getId(),
                e.getEmployeeId(),
                e.getCourseId(),
                e.getCourse() != null ? e.getCourse().getTitle() : null,
                e.getLearningPathId(),
                e.getLearningPath() != null ? e.getLearningPath().getName() : null,
                e.getStatus().name(),
                e.getProgressPercent(),
                e.getFinalScore(),
                e.getEnrolledAt(),
                e.getCompletedAt(),
                e.getUpdatedAt()
        );
    }
}
