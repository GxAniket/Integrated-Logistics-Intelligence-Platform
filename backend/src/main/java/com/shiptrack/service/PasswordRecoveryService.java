package com.shiptrack.service;

import com.shiptrack.entity.User;
import com.shiptrack.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class PasswordRecoveryService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PasswordRecoveryService.class);
    private static final Duration CODE_LIFETIME = Duration.ofMinutes(10);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender mailSender;
    private final String mailHost;
    private final Clock clock;
    private final Environment environment;
    private final boolean localCodeLoggingEnabled;
    private final Map<String, VerificationCode> verificationCodes = new ConcurrentHashMap<>();

    @Autowired
    public PasswordRecoveryService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            ObjectProvider<JavaMailSender> mailSenderProvider,
            @Value("${spring.mail.host:}") String mailHost,
            Environment environment,
            @Value("${shiptrack.password-recovery.log-code:false}") boolean localCodeLoggingEnabled) {
        this(userRepository, passwordEncoder, mailSenderProvider.getIfAvailable(), mailHost,
                Clock.systemUTC(), environment, localCodeLoggingEnabled);
    }

    PasswordRecoveryService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JavaMailSender mailSender,
            String mailHost,
            Clock clock,
            Environment environment,
            boolean localCodeLoggingEnabled) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.mailSender = mailSender;
        this.mailHost = mailHost;
        this.clock = clock;
        this.environment = environment;
        this.localCodeLoggingEnabled = localCodeLoggingEnabled;
    }

    public synchronized void sendVerificationCode(String email) {
        String normalizedEmail = normalizeEmail(email);
        boolean logCodeLocally = localCodeLoggingEnabled
                && environment.acceptsProfiles(Profiles.of("local & !prod & !production"));
        if (!logCodeLocally && (mailSender == null || mailHost == null || mailHost.isBlank())) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Password recovery email is not configured. Set spring.mail.host and the required SMTP credentials.");
        }

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "No account was found for this email address"));

        String code = String.format(Locale.ROOT, "%06d", RANDOM.nextInt(1_000_000));
        if (logCodeLocally) {
            LOGGER.warn("Local password recovery code for {}: {}", normalizedEmail, code);
        } else {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(user.getEmail());
            message.setSubject("ShipTrack Pro password recovery");
            message.setText("Your ShipTrack Pro verification code is " + code
                    + ". It expires in 10 minutes and can only be used once.");

            try {
                mailSender.send(message);
            } catch (MailException exception) {
                throw new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "Unable to deliver the verification email. Check the SMTP host and credentials.",
                        exception);
            }
        }

        verificationCodes.put(
                normalizedEmail,
                new VerificationCode(hashCode(code), clock.instant().plus(CODE_LIFETIME)));
    }

    @Transactional
    public synchronized void resetPassword(String email, String otp, String newPassword) {
        String normalizedEmail = normalizeEmail(email);
        if (otp == null || !otp.matches("[0-9]{6}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired verification code");
        }
        if (newPassword == null || newPassword.length() < 8) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be at least 8 characters long");
        }

        VerificationCode verificationCode = verificationCodes.get(normalizedEmail);
        if (verificationCode == null
                || !clock.instant().isBefore(verificationCode.expiresAt())
                || !MessageDigest.isEqual(verificationCode.digest(), hashCode(otp))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired verification code");
        }

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "No account was found for this email address"));
        verificationCodes.remove(normalizedEmail, verificationCode);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    private String normalizeEmail(String email) {
        if (email == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A valid email address is required");
        }
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        if (!normalizedEmail.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A valid email address is required");
        }
        return normalizedEmail;
    }

    private byte[] hashCode(String code) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(code.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private record VerificationCode(byte[] digest, Instant expiresAt) {
    }
}
