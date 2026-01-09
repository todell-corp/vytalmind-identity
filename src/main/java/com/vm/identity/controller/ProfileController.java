package com.vm.identity.controller;

import com.vm.identity.dto.ProfileResponse;
import com.vm.identity.dto.UserProfileDTO;
import com.vm.identity.service.ProfileService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/profiles")
public class ProfileController {

    private static final Logger log = LoggerFactory.getLogger(ProfileController.class);

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping("/{userId}")
    public ResponseEntity<ProfileResponse> getProfile(@PathVariable String userId) {
        log.info("Received request to get profile for user: {}", userId);
        try {
            ProfileResponse response = profileService.getProfile(userId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting profile for user: {}", userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PutMapping("/{userId}")
    public ResponseEntity<ProfileResponse> updateProfile(
            @PathVariable String userId,
            @Valid @RequestBody UserProfileDTO request) {
        log.info("Received request to update profile for user: {}", userId);
        try {
            ProfileResponse response = profileService.updateProfile(userId, request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error updating profile for user: {}", userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
