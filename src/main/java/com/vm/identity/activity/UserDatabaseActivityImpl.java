package com.vm.identity.activity;

import java.util.Optional;
import com.vm.identity.entity.User;
import com.vm.identity.entity.UserProfile;
import com.vm.identity.repository.UserProfileRepository;
import com.vm.identity.repository.UserRepository;
import io.temporal.failure.ApplicationFailure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Implementation of UserDatabaseActivity for Temporal workflows.
 * Handles database user and profile operations.
 */
@Component
public class UserDatabaseActivityImpl implements UserDatabaseActivity {
    private static final Logger log = LoggerFactory.getLogger(UserDatabaseActivityImpl.class);

    private final UserRepository userRepository;
    private final UserProfileRepository profileRepository;

    public UserDatabaseActivityImpl(
            UserRepository userRepository,
            UserProfileRepository profileRepository) {
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
    }

    @Override
    @Transactional
    public User createUser(User user) {
        log.info("Activity: Creating user in database: {}", user.getUsername());
        try {
            return userRepository.save(user);
        } catch (Exception e) {
            log.error("Activity failed: create user in database", e);
            throw ApplicationFailure.newFailure(
                    "Failed to create user in database",
                    "DatabaseCreateFailed",
                    Map.of("username", user.getUsername(), "error", e.getMessage()));
        }
    }

    @Override
    @Transactional
    public User updateUser(UUID userId, User updatedUser) {
        log.info("Activity: Updating user in database: {}", userId);
        try {
            User existing = userRepository.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

            // Update fields
            if (updatedUser.getEmail() != null) {
                existing.setEmail(updatedUser.getEmail());
            }
            if (updatedUser.getFirstName() != null) {
                existing.setFirstName(updatedUser.getFirstName());
            }
            if (updatedUser.getLastName() != null) {
                existing.setLastName(updatedUser.getLastName());
            }

            return userRepository.save(existing);
        } catch (Exception e) {
            log.error("Activity failed: update user in database: {}", userId, e);
            throw ApplicationFailure.newFailure(
                    "Failed to update user in database",
                    "DatabaseUpdateFailed",
                    Map.of("userId", userId.toString(), "error", e.getMessage()));
        }
    }

    @Override
    @Transactional
    public void deleteUser(UUID userId) {
        log.info("Activity: Soft deleting user from database: {}", userId);
        try {
            User user = userRepository.findById(userId, true)
                    .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
            user.setDeleted(true);
            user.setDeletedAt(java.time.Instant.now());
            userRepository.save(user);
        } catch (Exception e) {
            log.error("Activity failed: soft delete user from database: {}", userId, e);
            throw ApplicationFailure.newFailure(
                    "Failed to soft delete user from database",
                    "DatabaseDeleteFailed",
                    Map.of("userId", userId.toString(), "error", e.getMessage()));
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> getUser(UUID userId) {
        log.info("Activity: Fetching user from database: {}", userId);
        return userRepository.findById(userId);
    };

    @Override
    @Transactional(readOnly = true)
    public boolean checkUsernameExists(String username) {
        log.info("Activity: Checking if username exists in database: {}", username);
        try {
            return userRepository.existsByUsername(username);
        } catch (Exception e) {
            log.error("Activity failed: check username exists in database: {}", username, e);
            throw ApplicationFailure.newFailure(
                    "Failed to check username existence in database",
                    "DatabaseCheckFailed",
                    Map.of("username", username, "error", e.getMessage()));
        }
    }

    @Override
    @Transactional(readOnly = true)
    public boolean checkEmailExists(String email) {
        log.info("Activity: Checking if email exists in database: email={}", email);
        try {
            return userRepository.existsByEmailExcludingDeleted(email);
        } catch (Exception e) {
            log.error("Activity failed: check email exists in database", e);
            throw ApplicationFailure.newFailure(
                    "Failed to check email existence in database",
                    "DatabaseCheckFailed",
                    Map.of("email", email, "error", e.getMessage()));
        }
    }

    @Override
    @Transactional(readOnly = true)
    public boolean checkEmailOrUsernameExists(String email, String username) {
        log.info("Activity: Checking if email or username exists in database: email={}, username={}", email, username);
        try {
            return userRepository.existsByEmailOrUsernameExcludingDeleted(email, username);
        } catch (Exception e) {
            log.error("Activity failed: check email or username exists in database", e);
            throw ApplicationFailure.newFailure(
                    "Failed to check email or username existence in database",
                    "DatabaseCheckFailed",
                    Map.of("email", email, "username", username, "error", e.getMessage()));
        }
    }

    @Override
    @Transactional
    public UserProfile createProfile(UserProfile profile) {
        log.info("Activity: Creating user profile in database for user: {}", profile.getUserId());
        try {
            return profileRepository.save(profile);
        } catch (Exception e) {
            log.error("Activity failed: create user profile in database", e);
            throw ApplicationFailure.newFailure(
                    "Failed to create user profile",
                    "ProfileCreateFailed",
                    Map.of("userId", profile.getUserId().toString(), "error", e.getMessage()));
        }
    }

    @Override
    @Transactional
    public UserProfile updateProfile(UUID userId, UserProfile updatedProfile) {
        log.info("Activity: Updating user profile in database for user: {}", userId);
        try {
            UserProfile existing = profileRepository.findByUserId(userId)
                    .orElseThrow(() -> new IllegalArgumentException("User profile not found for user: " + userId));

            // Update fields
            if (updatedProfile.getBirthdate() != null) {
                existing.setBirthdate(updatedProfile.getBirthdate());
            }
            if (updatedProfile.getWeightGoalKg() != null) {
                existing.setWeightGoalKg(updatedProfile.getWeightGoalKg());
            }
            if (updatedProfile.getHeightCm() != null) {
                existing.setHeightCm(updatedProfile.getHeightCm());
            }
            if (updatedProfile.getWeightGoalOriginal() != null) {
                existing.setWeightGoalOriginal(updatedProfile.getWeightGoalOriginal());
            }
            if (updatedProfile.getHeightOriginal() != null) {
                existing.setHeightOriginal(updatedProfile.getHeightOriginal());
            }
            if (updatedProfile.getTargetBloodSugarMin() != null) {
                existing.setTargetBloodSugarMin(updatedProfile.getTargetBloodSugarMin());
            }
            if (updatedProfile.getTargetBloodSugarMax() != null) {
                existing.setTargetBloodSugarMax(updatedProfile.getTargetBloodSugarMax());
            }
            if (updatedProfile.getActivityLevel() != null) {
                existing.setActivityLevel(updatedProfile.getActivityLevel());
            }
            if (updatedProfile.getSex() != null) {
                existing.setSex(updatedProfile.getSex());
            }

            return profileRepository.save(existing);
        } catch (Exception e) {
            log.error("Activity failed: update user profile in database: {}", userId, e);
            throw ApplicationFailure.newFailure(
                    "Failed to update user profile",
                    "ProfileUpdateFailed",
                    Map.of("userId", userId.toString(), "error", e.getMessage()));
        }
    }

    @Override
    @Transactional(readOnly = true)
    public UserProfile getProfile(UUID userId) {
        log.info("Activity: Fetching user profile from database for user: {}", userId);
        try {
            return profileRepository.findByUserId(userId)
                    .orElse(null); // Profile is optional
        } catch (Exception e) {
            log.error("Activity failed: get user profile from database: {}", userId, e);
            throw ApplicationFailure.newFailure(
                    "Failed to get user profile from database",
                    "ProfileGetFailed",
                    Map.of("userId", userId.toString(), "error", e.getMessage()));
        }
    }
}
