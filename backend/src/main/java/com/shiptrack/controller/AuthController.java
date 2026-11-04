
package com.shiptrack.controller;

import com.shiptrack.dto.AuthResponse;
import com.shiptrack.dto.LoginRequest;
import com.shiptrack.dto.RegisterRequest;
import com.shiptrack.dto.UserDto;
import com.shiptrack.entity.User;
import com.shiptrack.security.JwtUtils;
import com.shiptrack.service.UserService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping
public class AuthController {

    private static final String ADMIN_EMAIL = "rekhapatil08@gmail.com";
    private static final String ADMIN_ROLE = "ADMINISTRATOR";

    private final AuthenticationManager authenticationManager;
    private final UserService userService;
    private final JwtUtils jwtUtils;

    public AuthController(
            AuthenticationManager authenticationManager,
            UserService userService,
            JwtUtils jwtUtils) {

        this.authenticationManager = authenticationManager;
        this.userService = userService;
        this.jwtUtils = jwtUtils;
    }

    @PostMapping({"/api/auth/register", "/auth/register"})
    public ResponseEntity<UserDto> register(
            @RequestBody RegisterRequest request) {

        // Public registration must never create Administrator accounts.
        if (request.getRole() != null
                && ADMIN_ROLE.equals(request.getRole().name())) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Administrator accounts cannot be created through public registration."
            );
        }

        UserDto registeredUser = userService.registerUser(request);
        return ResponseEntity.ok(registeredUser);
    }

    @PostMapping({"/api/auth/login", "/auth/login"})
    public ResponseEntity<AuthResponse> login(
            @RequestBody LoginRequest request) {

        if (request.getEmail() == null
                || request.getPassword() == null) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Email and password are required."
            );
        }

        String email = request.getEmail().trim().toLowerCase();

        // Verify the email and password first.
        Authentication authentication =
                authenticationManager.authenticate(
                        new UsernamePasswordAuthenticationToken(
                                email,
                                request.getPassword()
                        )
                );

        User user = userService.getUserByEmail(email);
        String actualRole = user.getRole().name();

        // Only the authorized email may have Administrator access.
        if (ADMIN_ROLE.equals(actualRole)
                && !ADMIN_EMAIL.equalsIgnoreCase(user.getEmail())) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Administrator access is restricted."
            );
        }

        // The authorized email must be assigned Administrator role.
        if (ADMIN_EMAIL.equalsIgnoreCase(user.getEmail())
                && !ADMIN_ROLE.equals(actualRole)) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "The authorized account is not configured as an Administrator."
            );
        }

        String token = jwtUtils.generateToken(
                user.getId(),
                user.getEmail(),
                actualRole
        );

        AuthResponse response = new AuthResponse(
                token,
                user.getId(),
                user.getEmail(),
                user.getRole()
        );

        return ResponseEntity.ok(response);
    }
}
