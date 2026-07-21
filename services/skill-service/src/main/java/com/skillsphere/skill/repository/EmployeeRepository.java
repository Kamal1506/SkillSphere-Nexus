package com.skillsphere.skill.repository;

import com.skillsphere.skill.entity.Employee;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface EmployeeRepository extends JpaRepository<Employee, UUID> {
    Optional<Employee> findByEmail(String email);
    Page<Employee> findByDepartmentIgnoreCase(String department, Pageable pageable);
}
