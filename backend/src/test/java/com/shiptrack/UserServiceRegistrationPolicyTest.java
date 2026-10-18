package com.shiptrack;

import com.shiptrack.dto.RegisterRequest;
import com.shiptrack.enums.Role;
import com.shiptrack.repository.UserRepository;
import com.shiptrack.service.UserServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class UserServiceRegistrationPolicyTest {

    @Test
    void publicRegistrationRejectsPrivilegedRolesBeforeDatabaseAccess() {
        UserRepository userRepository = mock(UserRepository.class);
        UserServiceImpl userService = new UserServiceImpl(
                userRepository,
                mock(PasswordEncoder.class),
                mock(JavaMailSender.class));

        for (Role role : new Role[]{Role.ADMINISTRATOR, Role.SUPPORT_AGENT}) {
            RegisterRequest request = new RegisterRequest();
            request.setEmail("privileged@example.com");
            request.setPassword("secure-password");
            request.setRole(role);

            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> userService.registerUser(request));

            assertEquals(
                    "Administrator and support accounts can only be created by an existing administrator.",
                    exception.getMessage());
        }
        verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
