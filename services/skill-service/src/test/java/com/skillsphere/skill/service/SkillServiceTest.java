package com.skillsphere.skill.service;

import com.skillsphere.skill.dto.EmployeeSkillDTO;
import com.skillsphere.skill.dto.MapSkillRequest;
import com.skillsphere.skill.entity.EmployeeSkill;
import com.skillsphere.skill.entity.Skill;
import com.skillsphere.skill.entity.SkillCategory;
import com.skillsphere.skill.exception.ResourceNotFoundException;
import com.skillsphere.skill.repository.EmployeeRepository;
import com.skillsphere.skill.repository.EmployeeSkillRepository;
import com.skillsphere.skill.repository.SkillRepository;
import com.skillsphere.skill.repository.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class SkillServiceTest {

    @Mock
    private SkillRepository skillRepository;

    @Mock
    private EmployeeSkillRepository employeeSkillRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private AppUserRepository appUserRepository;

    private SkillService skillService;

    @BeforeEach
    void setUp() {
        skillService = new SkillService(skillRepository, employeeSkillRepository, employeeRepository, appUserRepository);
    }

    @Test
    void mapEmployeeSkill_Success() {
        UUID employeeId = UUID.randomUUID();
        UUID skillId = UUID.randomUUID();
        MapSkillRequest request = new MapSkillRequest(skillId, 8, true);

        Skill skill = new Skill();
        skill.setId(skillId);
        skill.setName("Java");
        skill.setCategory(SkillCategory.TECHNICAL);

        when(employeeRepository.existsById(employeeId)).thenReturn(true);
        when(skillRepository.findById(skillId)).thenReturn(Optional.of(skill));
        when(employeeSkillRepository.findByEmployeeIdAndSkillId(employeeId, skillId)).thenReturn(Optional.empty());

        EmployeeSkill savedEs = new EmployeeSkill(employeeId, skillId, 8, true);
        savedEs.setSkill(skill);
        when(employeeSkillRepository.save(any(EmployeeSkill.class))).thenReturn(savedEs);

        EmployeeSkillDTO result = skillService.mapEmployeeSkill(employeeId, request);

        assertNotNull(result);
        assertEquals(employeeId, result.employeeId());
        assertEquals(skillId, result.skillId());
        assertEquals("Java", result.skillName());
        assertEquals("TECHNICAL", result.category());
        assertEquals(8, result.proficiency());
        assertTrue(result.verified());
        verify(employeeSkillRepository, times(1)).save(any(EmployeeSkill.class));
    }

    @Test
    void mapEmployeeSkill_EmployeeNotFound_ThrowsException() {
        UUID employeeId = UUID.randomUUID();
        UUID skillId = UUID.randomUUID();
        MapSkillRequest request = new MapSkillRequest(skillId, 8, true);

        when(employeeRepository.existsById(employeeId)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> skillService.mapEmployeeSkill(employeeId, request));
    }

    @Test
    void mapEmployeeSkill_DifferentEmployee_ThrowsAccessDenied() {
        UUID employeeId = UUID.randomUUID();
        UUID callerEmployeeId = UUID.randomUUID();
        UUID skillId = UUID.randomUUID();
        MapSkillRequest request = new MapSkillRequest(skillId, 8, true);

        // Setup security context with standard EMPLOYEE caller
        org.springframework.security.core.Authentication auth = mock(org.springframework.security.core.Authentication.class);
        when(auth.getName()).thenReturn("caller@skillsphere.com");
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getPrincipal()).thenReturn("caller@skillsphere.com");

        org.springframework.security.core.context.SecurityContext securityContext = mock(org.springframework.security.core.context.SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(auth);
        org.springframework.security.core.context.SecurityContextHolder.setContext(securityContext);

        com.skillsphere.skill.entity.AppUser caller = new com.skillsphere.skill.entity.AppUser();
        caller.setEmail("caller@skillsphere.com");
        caller.setRole(com.skillsphere.skill.entity.Role.EMPLOYEE);
        caller.setEmployeeId(callerEmployeeId);

        when(appUserRepository.findByEmail("caller@skillsphere.com")).thenReturn(Optional.of(caller));

        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> 
            skillService.mapEmployeeSkill(employeeId, request)
        );

        // Clear security context after test
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }
}
