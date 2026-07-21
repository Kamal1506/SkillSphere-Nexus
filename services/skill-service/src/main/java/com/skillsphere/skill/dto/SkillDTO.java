package com.skillsphere.skill.dto;

import java.time.Instant;
import java.util.UUID;

public record SkillDTO(
    UUID id,
    String name,
    String category,
    Instant createdAt
) {}
