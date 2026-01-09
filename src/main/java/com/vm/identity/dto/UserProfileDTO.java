package com.vm.identity.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO for user profile data.
 * Used in both requests and responses.
 * All fields are optional for partial updates.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileDTO {

    private LocalDate birthdate;

    @DecimalMin(value = "0.0", message = "Weight goal must be positive")
    @DecimalMax(value = "1000.0", message = "Weight goal must be less than 1000kg")
    private BigDecimal weightGoalKg;

    @DecimalMin(value = "0.0", message = "Height must be positive")
    @DecimalMax(value = "300.0", message = "Height must be less than 300cm")
    private BigDecimal heightCm;

    private String dietaryPreference;
    private String activityLevel;
    private String healthGoal;
}
