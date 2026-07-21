package com.skillsphere.skill.service;

import com.skillsphere.skill.dto.EmployeeSkillDTO;
import com.skillsphere.skill.dto.MapSkillRequest;
import com.skillsphere.skill.dto.SkillDTO;
import com.skillsphere.skill.dto.SkillEmployeeDTO;
import com.skillsphere.skill.entity.Employee;
import com.skillsphere.skill.entity.EmployeeSkill;
import com.skillsphere.skill.entity.Skill;
import com.skillsphere.skill.entity.SkillCategory;
import com.skillsphere.skill.exception.DuplicateResourceException;
import com.skillsphere.skill.exception.ResourceNotFoundException;
import com.skillsphere.skill.repository.EmployeeRepository;
import com.skillsphere.skill.repository.EmployeeSkillRepository;
import com.skillsphere.skill.repository.SkillRepository;
import com.skillsphere.skill.repository.AppUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class SkillService {

    private final SkillRepository skillRepository;
    private final EmployeeSkillRepository employeeSkillRepository;
    private final EmployeeRepository employeeRepository;
    private final AppUserRepository appUserRepository;

    public SkillService(SkillRepository skillRepository,
                        EmployeeSkillRepository employeeSkillRepository,
                        EmployeeRepository employeeRepository,
                        AppUserRepository appUserRepository) {
        this.skillRepository = skillRepository;
        this.employeeSkillRepository = employeeSkillRepository;
        this.employeeRepository = employeeRepository;
        this.appUserRepository = appUserRepository;
    }

    public List<SkillDTO> getAllSkills() {
        return skillRepository.findAll().stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public SkillDTO createSkill(SkillDTO dto) {
        if (skillRepository.findByNameIgnoreCase(dto.name()).isPresent()) {
            throw new DuplicateResourceException("Skill with name '" + dto.name() + "' already exists");
        }

        SkillCategory category;
        try {
            category = SkillCategory.valueOf(dto.category().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid skill category. Supported: TECHNICAL, DOMAIN, SOFT");
        }

        Skill skill = new Skill();
        skill.setName(dto.name());
        skill.setCategory(category);
        skill.setCreatedAt(Instant.now());

        Skill saved = skillRepository.save(skill);
        return mapToDTO(saved);
    }

    @Transactional
    public EmployeeSkillDTO mapEmployeeSkill(UUID employeeId, MapSkillRequest request) {
        validateUserPermission(employeeId);
        if (!employeeRepository.existsById(employeeId)) {
            throw new ResourceNotFoundException("Employee not found with id: " + employeeId);
        }

        Skill skill = skillRepository.findById(request.skillId())
                .orElseThrow(() -> new ResourceNotFoundException("Skill not found with id: " + request.skillId()));

        EmployeeSkill employeeSkill = employeeSkillRepository.findByEmployeeIdAndSkillId(employeeId, request.skillId())
                .orElseGet(() -> {
                    EmployeeSkill newEs = new EmployeeSkill();
                    newEs.setEmployeeId(employeeId);
                    newEs.setSkillId(request.skillId());
                    return newEs;
                });

        employeeSkill.setProficiency(request.proficiency());
        employeeSkill.setVerified(request.verified() != null ? request.verified() : false);
        employeeSkill.setUpdatedAt(Instant.now());

        EmployeeSkill saved = employeeSkillRepository.save(employeeSkill);
        // Force attachment of the skill entity for returning the DTO
        saved.setSkill(skill);

        return mapToEmployeeSkillDTO(saved);
    }

    public List<EmployeeSkillDTO> getEmployeeSkills(UUID employeeId) {
        if (!employeeRepository.existsById(employeeId)) {
            throw new ResourceNotFoundException("Employee not found with id: " + employeeId);
        }
        return employeeSkillRepository.findByEmployeeId(employeeId).stream()
                .map(this::mapToEmployeeSkillDTO)
                .collect(Collectors.toList());
    }

    public long countDistinctSkillsTracked() {
        return employeeSkillRepository.countDistinctSkillsTracked();
    }

    @Transactional(readOnly = true)
    public List<SkillEmployeeDTO> getEmployeesWithSkill(UUID skillId) {
        if (!skillRepository.existsById(skillId)) {
            throw new ResourceNotFoundException("Skill not found with id: " + skillId);
        }
        return employeeSkillRepository.findBySkillId(skillId).stream()
                .map(es -> {
                    Employee emp = employeeRepository.findById(es.getEmployeeId())
                            .orElseThrow(() -> new ResourceNotFoundException("Employee not found with id: " + es.getEmployeeId()));
                    return new SkillEmployeeDTO(
                            emp.getId(),
                            emp.getName(),
                            emp.getRoleTitle(),
                            emp.getDepartment(),
                            es.getProficiency(),
                            es.getVerified()
                    );
                })
                .collect(Collectors.toList());
    }

    private SkillDTO mapToDTO(Skill skill) {
        return new SkillDTO(
                skill.getId(),
                skill.getName(),
                skill.getCategory().name(),
                skill.getCreatedAt()
        );
    }

    private EmployeeSkillDTO mapToEmployeeSkillDTO(EmployeeSkill es) {
        return new EmployeeSkillDTO(
                es.getEmployeeId(),
                es.getSkillId(),
                es.getSkill() != null ? es.getSkill().getName() : "Unknown Skill",
                es.getSkill() != null ? es.getSkill().getCategory().name() : "TECHNICAL",
                es.getProficiency(),
                es.getVerified(),
                es.getUpdatedAt()
        );
    }

    private void validateUserPermission(UUID employeeId) {
        org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            String email = auth.getName();
            com.skillsphere.skill.entity.AppUser caller = appUserRepository.findByEmail(email)
                    .orElseThrow(() -> new org.springframework.security.access.AccessDeniedException("User context not found."));
            
            if (caller.getRole() == com.skillsphere.skill.entity.Role.EMPLOYEE) {
                if (caller.getEmployeeId() == null || !caller.getEmployeeId().equals(employeeId)) {
                    throw new org.springframework.security.access.AccessDeniedException("Employees can only map or assess skills on their own profile.");
                }
            }
        }
    }
}
