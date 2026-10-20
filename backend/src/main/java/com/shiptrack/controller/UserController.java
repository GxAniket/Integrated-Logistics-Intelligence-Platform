package com.shiptrack.controller;

import com.shiptrack.dto.RegisterRequest;
import com.shiptrack.dto.UserDto;
import com.shiptrack.entity.User;
import com.shiptrack.enums.Role;
import com.shiptrack.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public ResponseEntity<List<UserDto>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    @GetMapping("/me")
    public ResponseEntity<UserDto> getMe() {
        UserDto currentUser = userService.getCurrentUser();
        return ResponseEntity.ok(currentUser);
    }

    @GetMapping("/operators")
    @PreAuthorize("hasAnyRole('BUSINESS_CLIENT', 'ADMINISTRATOR')")
    public ResponseEntity<List<UserDto>> getOperators() {
        return ResponseEntity.ok(userService.getUsersByRole(Role.LOGISTICS_OPERATOR));
    }

    @GetMapping("/customers")
    @PreAuthorize("hasAnyRole('BUSINESS_CLIENT', 'ADMINISTRATOR')")
    public ResponseEntity<List<UserDto>> getCustomers() {
        return ResponseEntity.ok(userService.getUsersByRole(Role.CUSTOMER));
    }

    @PostMapping("/privileged")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public ResponseEntity<UserDto> createPrivilegedUser(@RequestBody RegisterRequest request) {
        return ResponseEntity.ok(userService.registerPrivilegedUser(request));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRATOR')")
    public ResponseEntity<UserDto> getUserById(@PathVariable Long id) {
        User user = userService.getUserById(id);
        UserDto dto = new UserDto(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getRole(),
                user.getRegisterId(),
                user.getPhoneNumber(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
        return ResponseEntity.ok(dto);
    }
}
