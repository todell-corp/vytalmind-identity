package com.vm.identity.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for user responses.
 * Includes workflow execution metadata.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {

    private String userId;
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private String status;
    private String workflowId;
}
