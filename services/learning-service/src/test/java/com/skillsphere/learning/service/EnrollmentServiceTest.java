package com.skillsphere.learning.service;

import com.skillsphere.learning.client.SkillServiceClient;
import com.skillsphere.learning.dto.CompleteEnrollmentRequest;
import com.skillsphere.learning.dto.CourseEnrollmentRequest;
import com.skillsphere.learning.dto.EnrollmentDTO;
import com.skillsphere.learning.entity.*;
import com.skillsphere.learning.exception.DuplicateResourceException;
import com.skillsphere.learning.exception.ResourceNotFoundException;
import com.skillsphere.learning.repository.CourseRepository;
import com.skillsphere.learning.repository.EnrollmentRepository;
import com.skillsphere.learning.repository.LearningPathRepository;
import com.skillsphere.learning.repository.LearningPathProgressRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class EnrollmentServiceTest {

    @Mock
    private EnrollmentRepository enrollmentRepository;

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private LearningPathRepository learningPathRepository;

    @Mock
    private LearningPathProgressRepository learningPathProgressRepository;

    @Mock
    private SkillServiceClient skillServiceClient;

    private EnrollmentService enrollmentService;

    @BeforeEach
    void setUp() {
        enrollmentService = new EnrollmentService(
                enrollmentRepository, courseRepository, learningPathRepository, learningPathProgressRepository, skillServiceClient
        );
    }

    @Test
    void createEnrollment_Course_Success() {
        UUID employeeId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        CourseEnrollmentRequest request = new CourseEnrollmentRequest(courseId, null);

        when(skillServiceClient.employeeExists(employeeId)).thenReturn(true);
        
        Course course = new Course();
        course.setId(courseId);
        course.setTitle("Java Programming");
        course.setCategory(CourseCategory.TECHNICAL);
        course.setDurationHours(20);
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));
        
        when(enrollmentRepository.findByEmployeeIdAndCourseId(employeeId, courseId)).thenReturn(Optional.empty());

        Enrollment saved = new Enrollment();
        saved.setId(UUID.randomUUID());
        saved.setEmployeeId(employeeId);
        saved.setCourseId(courseId);
        saved.setCourse(course);
        saved.setStatus(EnrollmentStatus.ENROLLED);
        saved.setProgressPercent(0);

        when(enrollmentRepository.save(any(Enrollment.class))).thenReturn(saved);

        EnrollmentDTO result = enrollmentService.createEnrollment(employeeId, request);

        assertNotNull(result);
        assertEquals(employeeId, result.employeeId());
        assertEquals(courseId, result.courseId());
        assertEquals("Java Programming", result.courseTitle());
        assertEquals("ENROLLED", result.status());
        assertEquals(0, result.progressPercent());
        verify(enrollmentRepository, times(1)).save(any(Enrollment.class));
    }

    @Test
    void createEnrollment_EmployeeNotFound_ThrowsException() {
        UUID employeeId = UUID.randomUUID();
        CourseEnrollmentRequest request = new CourseEnrollmentRequest(UUID.randomUUID(), null);

        when(skillServiceClient.employeeExists(employeeId)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> enrollmentService.createEnrollment(employeeId, request));
    }

    @Test
    void createEnrollment_AlreadyEnrolled_ThrowsException() {
        UUID employeeId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        CourseEnrollmentRequest request = new CourseEnrollmentRequest(courseId, null);

        when(skillServiceClient.employeeExists(employeeId)).thenReturn(true);
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(new Course()));
        when(enrollmentRepository.findByEmployeeIdAndCourseId(employeeId, courseId)).thenReturn(Optional.of(new Enrollment()));

        assertThrows(DuplicateResourceException.class, () -> enrollmentService.createEnrollment(employeeId, request));
    }

    @Test
    void updateProgress_Completes_Success() {
        UUID id = UUID.randomUUID();

        Enrollment enrollment = new Enrollment();
        enrollment.setId(id);
        enrollment.setEmployeeId(UUID.randomUUID());
        enrollment.setStatus(EnrollmentStatus.IN_PROGRESS);
        enrollment.setProgressPercent(50);

        when(enrollmentRepository.findById(id)).thenReturn(Optional.of(enrollment));

        Enrollment saved = new Enrollment();
        saved.setId(id);
        saved.setEmployeeId(enrollment.getEmployeeId());
        saved.setStatus(EnrollmentStatus.COMPLETED);
        saved.setProgressPercent(100);

        when(enrollmentRepository.save(any(Enrollment.class))).thenReturn(saved);

        EnrollmentDTO result = enrollmentService.updateProgress(id, 100);

        assertNotNull(result);
        assertEquals("COMPLETED", result.status());
        assertEquals(100, result.progressPercent());
        verify(enrollmentRepository, times(1)).save(enrollment);
    }

    @Test
    void completeEnrollment_WithScore_Success() {
        UUID id = UUID.randomUUID();
        CompleteEnrollmentRequest request = new CompleteEnrollmentRequest(85);

        Enrollment enrollment = new Enrollment();
        enrollment.setId(id);
        enrollment.setEmployeeId(UUID.randomUUID());
        enrollment.setStatus(EnrollmentStatus.IN_PROGRESS);
        enrollment.setProgressPercent(50);

        when(enrollmentRepository.findById(id)).thenReturn(Optional.of(enrollment));

        Enrollment saved = new Enrollment();
        saved.setId(id);
        saved.setEmployeeId(enrollment.getEmployeeId());
        saved.setStatus(EnrollmentStatus.COMPLETED);
        saved.setProgressPercent(100);
        saved.setFinalScore(85);

        when(enrollmentRepository.save(any(Enrollment.class))).thenReturn(saved);

        EnrollmentDTO result = enrollmentService.completeEnrollment(id, request);

        assertNotNull(result);
        assertEquals("COMPLETED", result.status());
        assertEquals(100, result.progressPercent());
        assertEquals(85, result.finalScore());
        verify(enrollmentRepository, times(1)).save(enrollment);
    }

    @Test
    void calculatePathProgress_WithCompletedCourses() {
        UUID employeeId = UUID.randomUUID();
        UUID pathId = UUID.randomUUID();
        UUID courseId1 = UUID.randomUUID();
        UUID courseId2 = UUID.randomUUID();

        LearningPath path = new LearningPath();
        path.setId(pathId);
        path.setName("Fullstack Path");
        
        LearningPathCourse lpc1 = new LearningPathCourse(pathId, courseId1, 1);
        LearningPathCourse lpc2 = new LearningPathCourse(pathId, courseId2, 2);
        path.setLearningPathCourses(List.of(lpc1, lpc2));

        when(learningPathRepository.findById(pathId)).thenReturn(Optional.of(path));

        Enrollment e1 = new Enrollment();
        e1.setCourseId(courseId1);
        e1.setStatus(EnrollmentStatus.COMPLETED);

        Enrollment e2 = new Enrollment();
        e2.setCourseId(courseId2);
        e2.setStatus(EnrollmentStatus.IN_PROGRESS);

        when(enrollmentRepository.findByEmployeeId(employeeId)).thenReturn(List.of(e1, e2));
        when(learningPathProgressRepository.findByEmployeeIdAndPathId(employeeId, pathId)).thenReturn(Optional.empty());

        int progress = enrollmentService.calculatePathProgress(employeeId, pathId);

        assertEquals(50, progress);
        verify(learningPathProgressRepository, times(1)).save(any(LearningPathProgress.class));
    }
}
