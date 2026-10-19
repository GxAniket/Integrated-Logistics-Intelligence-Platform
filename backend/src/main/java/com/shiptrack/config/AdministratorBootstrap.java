package com.shiptrack.config;

import com.shiptrack.dto.RegisterRequest;
import com.shiptrack.entity.User;
import com.shiptrack.enums.Role;
import com.shiptrack.repository.UserRepository;
import com.shiptrack.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
@ConditionalOnProperty(
        name = "shiptrack.bootstrap-admin.enabled",
        havingValue = "true")
public class AdministratorBootstrap implements ApplicationRunner {

    private static final Logger logger =
            LoggerFactory.getLogger(AdministratorBootstrap.class);

    private final UserRepository userRepository;
    private final UserService userService;
    private final String email;
    private final String password;
    private final String fullName;

    public AdministratorBootstrap(
            UserRepository userRepository,
            UserService userService,
            @Value("${shiptrack.bootstrap-admin.email:}") String email,
            @Value("${shiptrack.bootstrap-admin.password:}") String password,
            @Value("${shiptrack.bootstrap-admin.full-name:}") String fullName) {
        this.userRepository = userRepository;
        this.userService = userService;
        this.email = email;
        this.password = password;
        this.fullName = fullName;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!userRepository.findByRole(Role.ADMINISTRATOR).isEmpty()) {
            logger.info("Administrator bootstrap skipped because an administrator already exists.");
            return;
        }

        String normalizedEmail = email == null
                ? ""
                : email.trim().toLowerCase(Locale.ROOT);
        String normalizedName = fullName == null ? "" : fullName.trim();

        if (normalizedEmail.isBlank()
                || password == null
                || password.isBlank()
                || normalizedName.isBlank()) {
            throw new IllegalStateException(
                    "Administrator bootstrap is enabled, but SHIPTRACK_BOOTSTRAP_ADMIN_EMAIL, "
                            + "SHIPTRACK_BOOTSTRAP_ADMIN_PASSWORD, and "
                            + "SHIPTRACK_BOOTSTRAP_ADMIN_FULL_NAME must all be configured.");
        }
        if (password.length() < 12) {
            throw new IllegalStateException(
                    "The bootstrap administrator password must contain at least 12 characters.");
        }

        User existingUser = userRepository.findByEmail(normalizedEmail).orElse(null);
        if (existingUser != null) {
            throw new IllegalStateException(
                    "The bootstrap administrator email is already registered to a non-administrator account.");
        }

        RegisterRequest request = new RegisterRequest();
        request.setFullName(normalizedName);
        request.setEmail(normalizedEmail);
        request.setPassword(password);
        request.setRole(Role.ADMINISTRATOR);
        userService.createPrivilegedUser(request);

        logger.info("Created the initial administrator account for {}.", normalizedEmail);
    }
}
