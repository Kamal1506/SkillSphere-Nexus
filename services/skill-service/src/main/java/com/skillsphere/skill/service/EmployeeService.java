package com.skillsphere.skill.service;

import com.skillsphere.skill.dto.EmployeeDTO;
import com.skillsphere.skill.entity.Employee;
import com.skillsphere.skill.exception.ResourceNotFoundException;
import com.skillsphere.skill.repository.EmployeeRepository;
import com.skillsphere.skill.repository.AppUserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final AppUserRepository appUserRepository;

    public EmployeeService(EmployeeRepository employeeRepository, AppUserRepository appUserRepository) {
        this.employeeRepository = employeeRepository;
        this.appUserRepository = appUserRepository;
    }

    public Page<EmployeeDTO> getAllEmployees(String department, Pageable pageable) {
        Page<Employee> employeePage;
        if (department != null && !department.isBlank()) {
            employeePage = employeeRepository.findByDepartmentIgnoreCase(department, pageable);
        } else {
            employeePage = employeeRepository.findAll(pageable);
        }
        return employeePage.map(this::mapToDTO);
    }

    public EmployeeDTO getEmployeeById(UUID id) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with id: " + id));
        return mapToDTO(employee);
    }

    @Transactional
    public EmployeeDTO createEmployee(EmployeeDTO dto) {
        Employee employee = new Employee();
        employee.setName(dto.name());
        employee.setEmail(dto.email());
        employee.setRoleTitle(dto.roleTitle());
        employee.setDepartment(dto.department());
        employee.setExperienceYears(dto.experienceYears());
        employee.setRating(dto.rating());
        employee.setCreatedAt(Instant.now());
        employee.setUpdatedAt(Instant.now());

        Employee saved = employeeRepository.save(employee);
        return mapToDTO(saved);
    }

    @Transactional
    public EmployeeDTO updateEmployee(UUID id, EmployeeDTO dto) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with id: " + id));

        // Enforce HR Managers can only change details/ratings for standard employees, not other HRs or Admins.
        appUserRepository.findByEmployeeId(id).ifPresent(targetUser -> {
            if (targetUser.getRole() == com.skillsphere.skill.entity.Role.HR_MANAGER || targetUser.getRole() == com.skillsphere.skill.entity.Role.ADMIN) {
                org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
                if (auth != null) {
                    boolean isCallerHR = auth.getAuthorities().stream()
                            .anyMatch(a -> a.getAuthority().equals("ROLE_HR_MANAGER"));
                    if (isCallerHR) {
                        throw new org.springframework.security.access.AccessDeniedException("HR Managers can only update standard employee profiles.");
                    }
                }
            }
        });

        employee.setName(dto.name());
        employee.setEmail(dto.email());
        employee.setRoleTitle(dto.roleTitle());
        employee.setDepartment(dto.department());
        employee.setExperienceYears(dto.experienceYears());
        employee.setRating(dto.rating());

        Employee saved = employeeRepository.save(employee);
        return mapToDTO(saved);
    }

    public long countEmployees() {
        return employeeRepository.count();
    }

    @Transactional
    public void deleteEmployee(UUID id) {
        if (!employeeRepository.existsById(id)) {
            throw new ResourceNotFoundException("Employee not found with id: " + id);
        }
        
        // Delete associated AppUser
        appUserRepository.findByEmployeeId(id).ifPresent(appUserRepository::delete);

        // Delete employee
        employeeRepository.deleteById(id);
    }

    public EmployeeDTO mapToDTO(Employee employee) {
        return new EmployeeDTO(
                employee.getId(),
                employee.getName(),
                employee.getEmail(),
                employee.getRoleTitle(),
                employee.getDepartment(),
                employee.getExperienceYears(),
                employee.getRating(),
                employee.getCreatedAt(),
                employee.getUpdatedAt()
        );
    }
}
