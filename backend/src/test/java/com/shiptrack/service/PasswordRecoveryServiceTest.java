package com.shiptrack.service;

import com.shiptrack.entity.User;
import com.shiptrack.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PasswordRecoveryServiceTest {

    private static final String EMAIL = "customer@example.com";

    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private JavaMailSender mailSender;
    private MutableClock clock;
    private User user;
    private PasswordRecoveryService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        mailSender = mock(JavaMailSender.class);
        clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        user = new User();
        user.setEmail(EMAIL);
        user.setPasswordHash("old-hash");
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("new-password")).thenReturn("encoded-new-password");
        service = new PasswordRecoveryService(
                userRepository, passwordEncoder, mailSender, "smtp.example.com", clock,
                new MockEnvironment(), false);
    }

    @Test
    void codeResetsPasswordOnceAndCannotBeReused() {
        service.sendVerificationCode(EMAIL);
        String code = sentCode();

        service.resetPassword(EMAIL, code, "new-password");

        assertEquals("encoded-new-password", user.getPasswordHash());
        verify(userRepository).save(user);
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.resetPassword(EMAIL, code, "another-password"));
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void expiredCodeCannotResetPassword() {
        service.sendVerificationCode(EMAIL);
        String code = sentCode();
        clock.advanceSeconds(601);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.resetPassword(EMAIL, code, "new-password"));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void missingMailConfigurationIsReportedInsteadOfReturningSuccess() {
        PasswordRecoveryService unconfiguredService = new PasswordRecoveryService(
                userRepository, passwordEncoder, mailSender, " ", clock,
                new MockEnvironment(), false);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> unconfiguredService.sendVerificationCode(EMAIL));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatusCode());
    }

    @Test
    void localProfileLogsCodeWithoutSendingMailAndSupportsPasswordReset() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local");
        PasswordRecoveryService localService = new PasswordRecoveryService(
                userRepository, passwordEncoder, null, "", clock, environment, true);
        ch.qos.logback.classic.Logger logger =
                (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(PasswordRecoveryService.class);
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender =
                new ch.qos.logback.core.read.ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            localService.sendVerificationCode(EMAIL);

            String loggedCode = appender.list.stream()
                    .map(event -> event.getFormattedMessage())
                    .filter(message -> message.contains("Local password recovery code"))
                    .map(message -> message.replaceAll(".*: ([0-9]{6})$", "$1"))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Local verification code was not logged"));
            localService.resetPassword(EMAIL, loggedCode, "new-password");

            assertEquals("encoded-new-password", user.getPasswordHash());
            verify(userRepository).save(user);
            verify(mailSender, never()).send(any(SimpleMailMessage.class));
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void productionProfileCannotUseLocalCodeLogging() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local", "prod");
        PasswordRecoveryService productionService = new PasswordRecoveryService(
                userRepository, passwordEncoder, null, "", clock, environment, true);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> productionService.sendVerificationCode(EMAIL));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatusCode());
    }

    @Test
    void mailDeliveryFailureDoesNotCreateAWorkingCode() {
        org.springframework.mail.MailSendException deliveryFailure =
                new org.springframework.mail.MailSendException("delivery failed");
        org.mockito.Mockito.doThrow(deliveryFailure).when(mailSender).send(any(SimpleMailMessage.class));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.sendVerificationCode(EMAIL));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatusCode());
        assertThrows(
                ResponseStatusException.class,
                () -> service.resetPassword(EMAIL, "000000", "new-password"));
    }

    private String sentCode() {
        org.mockito.ArgumentCaptor<SimpleMailMessage> messageCaptor =
                org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(messageCaptor.capture());
        Matcher matcher = Pattern.compile("verification code is ([0-9]{6})")
                .matcher(messageCaptor.getValue().getText());
        if (!matcher.find()) {
            throw new AssertionError("Email did not contain a six-digit verification code");
        }
        return matcher.group(1);
    }

    private static final class MutableClock extends Clock {
        private final AtomicReference<Instant> instant;

        private MutableClock(Instant initialInstant) {
            instant = new AtomicReference<>(initialInstant);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant.get();
        }

        private void advanceSeconds(long seconds) {
            instant.updateAndGet(current -> current.plusSeconds(seconds));
        }
    }
}
