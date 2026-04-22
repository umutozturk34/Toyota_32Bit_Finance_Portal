package com.finance.backend.controller;
import com.finance.backend.dto.ApiResponse;
import com.finance.backend.dto.CreateUserRequest;
import com.finance.backend.dto.UpdateUserRequest;
import com.finance.backend.dto.UserDTO;
import com.finance.backend.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;
    @GetMapping
    public ApiResponse<List<UserDTO>> getAllUsers() {
        List<UserDTO> users = userService.getAllUsers();
        return ApiResponse.success("Users retrieved successfully", users);
    }
    @GetMapping("/{id}")
    public ApiResponse<UserDTO> getUserById(@PathVariable Long id) {
        UserDTO user = userService.getUserById(id);
        return ApiResponse.success("User retrieved successfully", user);
    }
    @GetMapping("/email/{email}")
    public ApiResponse<UserDTO> getUserByEmail(@PathVariable String email) {
        UserDTO user = userService.getUserByEmail(email);
        return ApiResponse.success("User retrieved successfully", user);
    }
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UserDTO> createUser(@Valid @RequestBody CreateUserRequest request) {
        UserDTO user = userService.createUser(request);
        return ApiResponse.success("User created successfully", user);
    }
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<UserDTO> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserRequest request) {
        UserDTO user = userService.updateUser(id, request);
        return ApiResponse.success("User updated successfully", user);
    }
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
    }
}
