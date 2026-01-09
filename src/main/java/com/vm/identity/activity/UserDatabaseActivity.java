package com.vm.identity.activity;

import java.util.Optional;
import com.vm.identity.entity.User;
import com.vm.identity.entity.UserProfile;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

import java.util.UUID;

/**
 * Temporal activity interface for database user operations.
 * Handles all database-specific user and profile management.
 */
@ActivityInterface
public interface UserDatabaseActivity {

    // User operations
    @ActivityMethod(name = "CreateUserInDatabase")
    User createUser(User user);

    @ActivityMethod(name = "UpdateUserInDatabase")
    User updateUser(UUID userId, User updatedUser);

    @ActivityMethod(name = "DeleteUserFromDatabase")
    void deleteUser(UUID userId);

    @ActivityMethod(name = "GetUserFromDatabase")
    Optional<User> getUser(UUID userId);

    @ActivityMethod(name = "CheckUsernameExists")
    boolean checkUsernameExists(String username);

    // Profile operations
    @ActivityMethod(name = "CreateUserProfileInDatabase")
    UserProfile createProfile(UserProfile profile);

    @ActivityMethod(name = "UpdateUserProfileInDatabase")
    UserProfile updateProfile(UUID userId, UserProfile updatedProfile);

    @ActivityMethod(name = "GetUserProfileFromDatabase")
    UserProfile getProfile(UUID userId);
}
