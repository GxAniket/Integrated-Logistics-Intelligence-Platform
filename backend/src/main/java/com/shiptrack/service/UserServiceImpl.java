
package com.shiptrack.service;

import com.shiptrack.dto.RegisterRequest;
import com.shiptrack.dto.UserDto;
import com.shiptrack.entity.User;
import com.shiptrack.enums.Role;
import com.shiptrack.exception.ResourceNotFoundException;
import com.shiptrack.repository.UserRepository;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;

@Service
public class UserServiceImpl implements UserService {

    private static final String REGISTER_ID_PREFIX = "BUS-";
    private static final String REGISTER_ID_CHARS = "0123456789ABCDEF";
    private static final int REGISTER_ID_SUFFIX_LENGTH = 8;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserServiceImpl(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public UserDto registerUser(RegisterRequest request) {

        if (request.getEmail() == null
                || request.getEmail().isBlank()) {
            throw new IllegalArgumentException("Email is required");
        }

        if (request.getPassword() == null
                || request.getPassword().isBlank()) {
            throw new IllegalArgumentException("Password is required");
        }

        if (request.getConfirmPassword() != null
                && !request.getConfirmPassword().equals(request.getPassword())) {
            throw new IllegalArgumentException(
                    "Password and confirm password do not match"
            );
        }

        if (request.getRole() == null) {
            throw new IllegalArgumentException("Role is required");
        }

        // Block Administrator account creation through public registration.
        if (request.getRole() == Role.ADMINISTRATOR) {
            throw new IllegalArgumentException(
                    "Administrator accounts cannot be created through public registration."
            );
        }

        String email = request.getEmail().trim().toLowerCase();

        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException(
                    "Email is already registered!"
            );
        }

        User user = new User();
        user.setFullName(request.getFullName());
        user.setEmail(email);
        user.setPasswordHash(
                passwordEncoder.encode(request.getPassword())
        );
        user.setRole(request.getRole());
        user.setPhoneNumber(request.getPhoneNumber());

        // Generate a register ID only for Business Client accounts.
        if (request.getRole() == Role.BUSINESS_CLIENT) {
            user.setRegisterId(generateUniqueRegisterId());
        }

        User savedUser = userRepository.save(user);
        return mapToDto(savedUser);
    }

    private synchronized String generateUniqueRegisterId() {
        String candidate;

        do {
            candidate = REGISTER_ID_PREFIX + randomSuffix();
        } while (userRepository.existsByRegisterId(candidate));

        return candidate;
    }

    private String randomSuffix() {
        StringBuilder sb =
                new StringBuilder(REGISTER_ID_SUFFIX_LENGTH);

        for (int i = 0; i < REGISTER_ID_SUFFIX_LENGTH; i++) {
            sb.append(
                    REGISTER_ID_CHARS.charAt(
                            RANDOM.nextInt(REGISTER_ID_CHARS.length())
                    )
            );
        }

        return sb.toString();
    }

    @Override
    public UserDto getCurrentUser() {
        String email = SecurityContextHolder.getContext()
                .getAuthentication()
                .getName();

        User user = getUserByEmail(email);
        return mapToDto(user);
    }

    @Override
    public User getUserByEmail(String email) {
        return userRepository.findByEmail(email.trim().toLowerCase())
                .orElseThrow(
                        () -> new ResourceNotFoundException(
                                "User not found with email: " + email
                        )
                );
    }

    @Override
    public User getUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(
                        () -> new ResourceNotFoundException(
                                "User not found with ID: " + id
                        )
                );
    }

    private UserDto mapToDto(User user) {
        return new UserDto(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getRole(),
                user.getRegisterId(),
                user.getPhoneNumber(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
