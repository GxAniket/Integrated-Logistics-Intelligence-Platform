
package com.shiptrack;

import com.shiptrack.entity.User;
import com.shiptrack.enums.Role;
import com.shiptrack.repository.UserRepository;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AdminAccountInitializer implements ApplicationRunner {

    private static final String ADMIN_EMAIL = "rekhapatil08@gmail.com";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminAccountInitializer(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        User admin = userRepository.findByEmail(ADMIN_EMAIL)
                .orElse(null);

        if (admin != null) {
            if (admin.getRole() != Role.ADMINISTRATOR) {
                admin.setRole(Role.ADMINISTRATOR);
                userRepository.save(admin);
            }

            System.out.println(
                    "Authorized Administrator account verified."
            );
            return;
        }

        String password = System.getenv("SHIPTRACK_ADMIN_PASSWORD");

        if (password == null || password.isBlank()) {
            System.out.println(
                    "Admin account not created. Set SHIPTRACK_ADMIN_PASSWORD "
                    + "and restart the backend."
            );
            return;
        }

        User newAdmin = new User();
        newAdmin.setFullName("Rekha Patil");
        newAdmin.setEmail(ADMIN_EMAIL);
        newAdmin.setPasswordHash(passwordEncoder.encode(password));
        newAdmin.setRole(Role.ADMINISTRATOR);

        userRepository.save(newAdmin);

        System.out.println(
                "Authorized Administrator account created."
        );
    }
    
}
