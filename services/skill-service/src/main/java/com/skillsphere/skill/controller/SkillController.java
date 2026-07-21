package com.skillsphere.skill.controller;

import com.skillsphere.skill.dto.EmployeeSkillDTO;
import com.skillsphere.skill.dto.MapSkillRequest;
import com.skillsphere.skill.dto.SkillDTO;
import com.skillsphere.skill.dto.SkillEmployeeDTO;
import com.skillsphere.skill.service.SkillService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class SkillController {

    private final SkillService skillService;

    public SkillController(SkillService skillService) {
        this.skillService = skillService;
    }

    @GetMapping("/skills")
    public ResponseEntity<List<SkillDTO>> getSkills() {
        return ResponseEntity.ok(skillService.getAllSkills());
    }

    @PostMapping("/skills")
    public ResponseEntity<SkillDTO> createSkill(@Valid @RequestBody SkillDTO dto) {
        SkillDTO created = skillService.createSkill(dto);
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }

    @PostMapping("/employees/{id}/skills")
    public ResponseEntity<EmployeeSkillDTO> mapEmployeeSkill(
             @PathVariable UUID id,
             @Valid @RequestBody MapSkillRequest request) {
        EmployeeSkillDTO mapped = skillService.mapEmployeeSkill(id, request);
        return ResponseEntity.ok(mapped);
    }

    @GetMapping("/employees/{id}/skills")
    public ResponseEntity<List<EmployeeSkillDTO>> getEmployeeSkills(@PathVariable UUID id) {
        return ResponseEntity.ok(skillService.getEmployeeSkills(id));
    }

    @GetMapping("/skills/{skillId}/employees")
    public ResponseEntity<List<SkillEmployeeDTO>> getEmployeesWithSkill(@PathVariable UUID skillId) {
        return ResponseEntity.ok(skillService.getEmployeesWithSkill(skillId));
    }
}
