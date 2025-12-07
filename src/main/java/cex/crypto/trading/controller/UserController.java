package cex.crypto.trading.controller;

import cex.crypto.trading.domain.User;
import cex.crypto.trading.dto.ApiResponse;
import cex.crypto.trading.dto.CreateUserRequest;
import cex.crypto.trading.dto.UserResponse;
import cex.crypto.trading.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller for User management
 * Handles user creation, query, and deletion operations
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/users")
@Validated
@Tag(name = "User Management", description = "APIs for user operations")
public class UserController {

    @Autowired
    private UserService userService;

    /**
     * Create a new user
     *
     * @param request the create user request
     * @return API response with created user details
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new user", description = "Register a new user account")
    public ApiResponse<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        log.info("Creating user: username={}", request.getUsername());
        User user = userService.createUser(request);
        UserResponse response = UserResponse.fromUser(user);
        return ApiResponse.success("User created successfully", response);
    }

    /**
     * Get user by ID
     *
     * @param userId the user ID
     * @return API response with user details
     */
    @GetMapping("/{userId}")
    @Operation(summary = "Get user by ID", description = "Retrieve user details by user ID")
    public ApiResponse<UserResponse> getUser(
            @Parameter(description = "User ID", required = true)
            @PathVariable @NotNull Long userId) {
        log.debug("Getting user: userId={}", userId);
        User user = userService.getUserById(userId);
        UserResponse response = UserResponse.fromUser(user);
        return ApiResponse.success(response);
    }

    /**
     * Delete user
     *
     * @param userId the user ID
     * @return API response
     */
    @DeleteMapping("/{userId}")
    @Operation(summary = "Delete user", description = "Delete a user account")
    public ApiResponse<Void> deleteUser(
            @Parameter(description = "User ID", required = true)
            @PathVariable @NotNull Long userId) {
        log.info("Deleting user: userId={}", userId);
        userService.deleteUser(userId);
        return ApiResponse.success("User deleted successfully", null);
    }
}
