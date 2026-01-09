package com.vm.identity.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "user_profiles")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    private LocalDate birthdate;

    @Column(name = "weight_goal_kg", precision = 6, scale = 2)
    @DecimalMin("0.0")
    @DecimalMax("1000.0")
    private BigDecimal weightGoalKg;

    @Column(name = "height_cm", precision = 6, scale = 2)
    @DecimalMin("0.0")
    @DecimalMax("300.0")
    private BigDecimal heightCm;

    @Column(name = "weight_goal_original", length = 50)
    private String weightGoalOriginal;

    @Column(name = "height_original", length = 50)
    private String heightOriginal;

    @Column(name = "target_blood_sugar_min")
    @Min(0)
    @Max(1000)
    private Integer targetBloodSugarMin;

    @Column(name = "target_blood_sugar_max")
    @Min(0)
    @Max(1000)
    private Integer targetBloodSugarMax;

    @Enumerated(EnumType.STRING)
    @Column(name = "activity_level", length = 20)
    private ActivityLevel activityLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "sex", length = 10)
    private Sex sex;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public enum ActivityLevel {
        SEDENTARY, LOW_ACTIVE, ACTIVE, VERY_ACTIVE
    }

    public enum Sex {
        MALE, FEMALE
    }

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
