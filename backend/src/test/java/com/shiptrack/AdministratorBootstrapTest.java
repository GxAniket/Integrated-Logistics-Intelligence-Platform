package com.shiptrack;

import com.shiptrack.config.AdministratorBootstrap;
import com.shiptrack.dto.RegisterRequest;
import com.shiptrack.entity.User;
import com.shiptrack.enums.Role;
import com.shiptrack.repository.UserRepository;
import com.shiptrack.service.UserService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdministratorBootstrapTest {

    private static final String PASSWORD = "secure-bootstrap-password";

    @Test
    void createsInitialAdministratorWithConfiguredCredentials() {
        UserRepository userRepository = mock(UserRepository.class);
        UserService userService = mock(UserService.class);
        when(userRepository.findByRole(Role.ADMINISTRATOR)).thenReturn(List.of());
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.empty());

        AdministratorBootstrap bootstrap = new AdministratorBootstrap(
                userRepository,
                userService,
                " Admin@Example.com ",
                PASSWORD,
                "Initial Administrator");

        bootstrap.run(null);

        ArgumentCaptor<RegisterRequest> requestCaptor =
                ArgumentCaptor.forClass(RegisterRequest.class);
        verify(userService).createPrivilegedUser(requestCaptor.capture());
        RegisterRequest request = requestCaptor.getValue();
        assertEquals("admin@example.com", request.getEmail());
        assertEquals(PASSWORD, request.getPassword());
        assertEquals("Initial Administrator", request.getFullName());
        assertEquals(Role.ADMINISTRATOR, request.getRole());
    }

    @Test
    void doesNotReplaceOrRecreateAnExistingAdministrator() {
        UserRepository userRepository = mock(UserRepository.class);
        UserService userService = mock(UserService.class);
        when(userRepository.findByRole(Role.ADMINISTRATOR))
                .thenReturn(List.of(new User()));

        new AdministratorBootstrap(
                userRepository, userService, "", "", "").run(null);

        verify(userService, never()).createPrivilegedUser(any());
        verify(userRepository, never()).findByEmail(any());
    }

    @Test
    void refusesToStartWithAnIncompleteBootstrapConfiguration() {
        UserRepository userRepository = mock(UserRepository.class);
        when(userRepository.findByRole(Role.ADMINISTRATOR)).thenReturn(List.of());

        AdministratorBootstrap bootstrap = new AdministratorBootstrap(
                userRepository,
                mock(UserService.class),
                "admin@example.com",
                "",
                "Initial Administrator");

        assertThrows(IllegalStateException.class, () -> bootstrap.run(null));
    }

    @Test
    void refusesToUseAWeakBootstrapPassword() {
        UserRepository userRepository = mock(UserRepository.class);
        when(userRepository.findByRole(Role.ADMINISTRATOR)).thenReturn(List.of());

        AdministratorBootstrap bootstrap = new AdministratorBootstrap(
                userRepository,
                mock(UserService.class),
                "admin@example.com",
                "short-pass",
                "Initial Administrator");

        assertThrows(IllegalStateException.class, () -> bootstrap.run(null));
    }

    @Test
    void refusesToPromoteAnExistingNonAdministratorAccount() {
        UserRepository userRepository = mock(UserRepository.class);
        UserService userService = mock(UserService.class);
        when(userRepository.findByRole(Role.ADMINISTRATOR)).thenReturn(List.of());
        when(userRepository.findByEmail("existing@example.com"))
                .thenReturn(Optional.of(new User()));

        AdministratorBootstrap bootstrap = new AdministratorBootstrap(
                userRepository,
                userService,
                "existing@example.com",
                PASSWORD,
                "Initial Administrator");

        assertThrows(IllegalStateException.class, () -> bootstrap.run(null));
        verify(userService, never()).createPrivilegedUser(any());
    }
}
