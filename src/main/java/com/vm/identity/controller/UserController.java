package com.vm.identity.controller;

import com.vm.identity.dto.UserCreateRequest;
import com.vm.identity.dto.UserResponse;
import com.vm.identity.dto.UserUpdateRequest;
import com.vm.identity.service.UserService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody UserCreateRequest request) {
        log.info("Received request to create user with username: {}", request.getUsername());
        try {
            UserResponse response = userService.createUser(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            log.error("Error creating user with username: {}", request.getUsername(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PutMapping("/{userId}")
    public ResponseEntity<UserResponse> updateUser(
            @PathVariable String userId,
            @Valid @RequestBody UserUpdateRequest request) {
        log.info("Received request to update user: {}", userId);
        try {
            UserResponse response = userService.updateUser(userId, request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error updating user: {}", userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<UserResponse> deleteUser(@PathVariable String userId) {
        log.info("Received request to delete user: {}", userId);
        try {
            UserResponse response = userService.deleteUser(userId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error deleting user: {}", userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
