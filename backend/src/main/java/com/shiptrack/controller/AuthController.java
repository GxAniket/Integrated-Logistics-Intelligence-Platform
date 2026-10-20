
package com.shiptrack.controller;

import com.shiptrack.dto.AuthResponse;
import com.shiptrack.dto.ForgotPasswordRequest;
import com.shiptrack.dto.LoginRequest;
import com.shiptrack.dto.RegisterRequest;
import com.shiptrack.dto.ResetPasswordRequest;
import com.shiptrack.dto.UserDto;
import com.shiptrack.entity.User;
import com.shiptrack.security.JwtUtils;
import com.shiptrack.service.PasswordRecoveryService;
import com.shiptrack.service.UserService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UserService userService;
    private final JwtUtils jwtUtils;
    private final PasswordRecoveryService passwordRecoveryService;

    public AuthController(
            AuthenticationManager authenticationManager,
            UserService userService,
            JwtUtils jwtUtils,
            PasswordRecoveryService passwordRecoveryService) {
        this.authenticationManager = authenticationManager;
        this.userService = userService;
        this.jwtUtils = jwtUtils;
        this.passwordRecoveryService = passwordRecoveryService;
    }

    @PostMapping({"/api/auth/register", "/auth/register"})
    public ResponseEntity<UserDto> register(
            @RequestBody RegisterRequest request) {

        UserDto registeredUser = userService.registerPublicUser(request);
        return ResponseEntity.ok(registeredUser);
    }

    @PostMapping({"/api/auth/login", "/auth/login"})
    public ResponseEntity<AuthResponse> login(
            @RequestBody LoginRequest request) {

        String email = request.getEmail();

        Authentication authentication =
                authenticationManager.authenticate(
                        new UsernamePasswordAuthenticationToken(
                                email,
                                request.getPassword()
                        )
                );

        User user = userService.getUserByEmail(email);

        String token = jwtUtils.generateToken(
                user.getId(),
                user.getEmail(),
                user.getRole().name()
        );

        return ResponseEntity.ok(
                new AuthResponse(
                        token,
                        user.getId(),
                        user.getEmail(),
                        user.getRole()
                )
        );
    }

    @PostMapping({"/api/auth/forgot-password", "/auth/forgot-password"})
    public ResponseEntity<Void> forgotPassword(
            @RequestBody ForgotPasswordRequest request) {
        passwordRecoveryService.sendVerificationCode(request.getEmail());
        return ResponseEntity.noContent().build();
    }

    @PostMapping({"/api/auth/reset-password", "/auth/reset-password"})
    public ResponseEntity<Void> resetPassword(
            @RequestBody ResetPasswordRequest request) {
        passwordRecoveryService.resetPassword(
                request.getEmail(),
                request.getOtp(),
                request.getNewPassword());
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<String> handlePasswordRecoveryError(
            ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode())
                .body(exception.getReason());
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<String> handleBadCredentials(
            BadCredentialsException exception) {

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body("Invalid email or password");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<String> handleInvalidRequestBody(
            HttpMessageNotReadableException exception) {

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body("Request body is missing or invalid");
    }
}
