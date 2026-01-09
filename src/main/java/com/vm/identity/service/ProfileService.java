package com.vm.identity.service;

import com.vm.identity.dto.ProfileResponse;
import com.vm.identity.dto.UserProfileDTO;
import com.vm.identity.entity.UserProfile;
import com.vm.identity.repository.UserProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ProfileService {

    private static final Logger log = LoggerFactory.getLogger(ProfileService.class);

    private final UserProfileRepository profileRepository;

    public ProfileService(UserProfileRepository profileRepository) {
        this.profileRepository = profileRepository;
    }

    @Transactional(readOnly = true)
    public ProfileResponse getProfile(String userId) {
        log.info("Getting profile for user: {}", userId);

        try {
            UUID userUuid = UUID.fromString(userId);

            UserProfile profile = profileRepository.findByUserId(userUuid)
                    .orElseThrow(() -> new RuntimeException("Profile not found for user: " + userId));

            UserProfileDTO profileDTO = convertToDTO(profile);

            ProfileResponse response = new ProfileResponse();
            response.setUserId(userId);
            response.setProfile(profileDTO);
            response.setStatus("Profile retrieved successfully");

            log.info("Profile retrieved successfully for user: {}", userId);
            return response;

        } catch (Exception e) {
            log.error("Failed to get profile for user: {}", userId, e);
            throw new RuntimeException("Failed to get profile: " + e.getMessage(), e);
        }
    }

    @Transactional
    public ProfileResponse updateProfile(String userId, UserProfileDTO profileDTO) {
        log.info("Updating profile for user: {}", userId);

        try {
            UUID userUuid = UUID.fromString(userId);

            UserProfile profile = profileRepository.findByUserId(userUuid)
                    .orElseThrow(() -> new RuntimeException("Profile not found for user: " + userId));

            // Update fields from DTO
            if (profileDTO.getBirthdate() != null) {
                profile.setBirthdate(profileDTO.getBirthdate());
            }

            if (profileDTO.getWeightGoalKg() != null) {
                profile.setWeightGoalKg(profileDTO.getWeightGoalKg());
            }

            if (profileDTO.getHeightCm() != null) {
                profile.setHeightCm(profileDTO.getHeightCm());
            }

            if (profileDTO.getActivityLevel() != null) {
                try {
                    profile.setActivityLevel(UserProfile.ActivityLevel.valueOf(profileDTO.getActivityLevel().toUpperCase()));
                } catch (IllegalArgumentException e) {
                    log.warn("Invalid activity level: {}, skipping", profileDTO.getActivityLevel());
                }
            }

            // Note: dietaryPreference and healthGoal not in entity yet
            if (profileDTO.getDietaryPreference() != null) {
                log.info("Dietary preference provided: {}, but field not in entity", profileDTO.getDietaryPreference());
            }

            if (profileDTO.getHealthGoal() != null) {
                log.info("Health goal provided: {}, but field not in entity", profileDTO.getHealthGoal());
            }

            UserProfile updatedProfile = profileRepository.save(profile);

            UserProfileDTO updatedDTO = convertToDTO(updatedProfile);

            ProfileResponse response = new ProfileResponse();
            response.setUserId(userId);
            response.setProfile(updatedDTO);
            response.setStatus("Profile updated successfully");

            log.info("Profile updated successfully for user: {}", userId);
            return response;

        } catch (Exception e) {
            log.error("Failed to update profile for user: {}", userId, e);
            throw new RuntimeException("Failed to update profile: " + e.getMessage(), e);
        }
    }

    private UserProfileDTO convertToDTO(UserProfile profile) {
        UserProfileDTO dto = new UserProfileDTO();
        dto.setBirthdate(profile.getBirthdate());
        dto.setWeightGoalKg(profile.getWeightGoalKg());
        dto.setHeightCm(profile.getHeightCm());

        if (profile.getActivityLevel() != null) {
            dto.setActivityLevel(profile.getActivityLevel().name());
        }

        // Note: dietaryPreference and healthGoal are not in UserProfile entity yet

        return dto;
    }
}
