package com.vm.identity.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for profile responses.
 * Includes workflow execution metadata.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProfileResponse {

    private String userId;
    private UserProfileDTO profile;
    private String status;
    private String workflowId;
}
