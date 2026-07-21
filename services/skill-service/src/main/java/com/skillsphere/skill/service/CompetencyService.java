package com.skillsphere.skill.service;

import com.skillsphere.skill.dto.CompetencyFrameworkDTO;
import com.skillsphere.skill.dto.SkillGapDTO;
import com.skillsphere.skill.entity.CompetencyFramework;
import com.skillsphere.skill.entity.Employee;
import com.skillsphere.skill.entity.EmployeeSkill;
import com.skillsphere.skill.exception.ResourceNotFoundException;
import com.skillsphere.skill.repository.CompetencyFrameworkRepository;
import com.skillsphere.skill.repository.EmployeeRepository;
import com.skillsphere.skill.repository.EmployeeSkillRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CompetencyService {

    private final CompetencyFrameworkRepository frameworkRepository;
    private final EmployeeRepository employeeRepository;
    private final EmployeeSkillRepository employeeSkillRepository;

    public CompetencyService(CompetencyFrameworkRepository frameworkRepository,
                             EmployeeRepository employeeRepository,
                             EmployeeSkillRepository employeeSkillRepository) {
        this.frameworkRepository = frameworkRepository;
        this.employeeRepository = employeeRepository;
        this.employeeSkillRepository = employeeSkillRepository;
    }

    public List<CompetencyFrameworkDTO> getFrameworkByRole(String role) {
        String cleanRole = role != null ? role.trim() : "Tech Lead";
        return frameworkRepository.findByRoleNameIgnoreCase(cleanRole).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public List<SkillGapDTO> calculateSkillGaps(UUID employeeId) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with id: " + employeeId));

        // Use employee's roleTitle (e.g. "Tech Lead") as target role
        String targetRole = employee.getRoleTitle();
        List<CompetencyFramework> requirements = frameworkRepository.findByRoleNameIgnoreCase(targetRole);

        // Fetch employee's current skills
        List<EmployeeSkill> actualSkills = employeeSkillRepository.findByEmployeeId(employeeId);
        Map<UUID, EmployeeSkill> actualSkillMap = actualSkills.stream()
                .collect(Collectors.toMap(EmployeeSkill::getSkillId, Function.identity()));

        List<SkillGapDTO> gaps = new ArrayList<>();
        for (CompetencyFramework req : requirements) {
            int actualLevel = 0;
            if (actualSkillMap.containsKey(req.getSkillId())) {
                actualLevel = actualSkillMap.get(req.getSkillId()).getProficiency();
            }

            int gapValue = req.getRequiredLevel() - actualLevel;
            // Gap is 0 if employee has equal or higher proficiency than required
            int gap = Math.max(gapValue, 0);

            gaps.add(new SkillGapDTO(
                    req.getSkillId(),
                    req.getSkill() != null ? req.getSkill().getName() : "Unknown Skill",
                    req.getSkill() != null ? req.getSkill().getCategory().name() : "TECHNICAL",
                    req.getRequiredLevel(),
                    actualLevel,
                    gap
            ));
        }

        return gaps;
    }

    private CompetencyFrameworkDTO mapToDTO(CompetencyFramework cf) {
        return new CompetencyFrameworkDTO(
                cf.getId(),
                cf.getRoleName(),
                cf.getSkillId(),
                cf.getSkill() != null ? cf.getSkill().getName() : "Unknown Skill",
                cf.getSkill() != null ? cf.getSkill().getCategory().name() : "TECHNICAL",
                cf.getRequiredLevel()
        );
    }
}
