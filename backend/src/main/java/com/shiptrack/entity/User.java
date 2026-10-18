package com.shiptrack.entity;

import com.shiptrack.enums.Role;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_code", nullable = false, unique = true, length = 30)
    private String userCode;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Role role;

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @Column(name = "register_id", unique = true, length = 20)
    private String registerId;

    @Column(name = "password_reset_otp_hash", length = 255)
    private String passwordResetOtpHash;

    @Column(name = "password_reset_otp_expires_at")
    private LocalDateTime passwordResetOtpExpiresAt;

    @Column(name = "password_reset_otp_attempts")
    private Integer passwordResetOtpAttempts;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (userCode == null || userCode.isBlank()) {
            userCode = UUID.randomUUID().toString().replace("-", "").substring(0, 30);
        }
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public User() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUserCode() {
        return userCode;
    }

    public void setUserCode(String userCode) {
        this.userCode = userCode;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getRegisterId() {
        return registerId;
    }

    public void setRegisterId(String registerId) {
        this.registerId = registerId;
    }

    public String getPasswordResetOtpHash() {
        return passwordResetOtpHash;
    }

    public void setPasswordResetOtpHash(String passwordResetOtpHash) {
        this.passwordResetOtpHash = passwordResetOtpHash;
    }

    public LocalDateTime getPasswordResetOtpExpiresAt() {
        return passwordResetOtpExpiresAt;
    }

    public void setPasswordResetOtpExpiresAt(LocalDateTime passwordResetOtpExpiresAt) {
        this.passwordResetOtpExpiresAt = passwordResetOtpExpiresAt;
    }

    public int getPasswordResetOtpAttempts() {
        return passwordResetOtpAttempts == null ? 0 : passwordResetOtpAttempts.intValue();
    }

    public void setPasswordResetOtpAttempts(int passwordResetOtpAttempts) {
        this.passwordResetOtpAttempts = passwordResetOtpAttempts;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
