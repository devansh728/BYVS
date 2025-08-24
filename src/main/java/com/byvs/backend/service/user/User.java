package com.byvs.backend.service.user;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "app_user", indexes = {
        @Index(name = "idx_user_phone", columnList = "phone"),
        @Index(name = "idx_user_referral_code", columnList = "referral_code")
})
@Getter
@Setter
@NoArgsConstructor
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Encrypted or hashed in DB via column-level encryption / pgcrypto; store normalized E.164
    @Column(name = "phone", nullable = false, unique = true, length = 32)
    private String phone;

    @Column(name = "referral_code", nullable = false, unique = true, length = 16)
    private String referralCode;

    @Column(name = "referred_by_code", length = 16)
    private String referredByCode;

    @Column(name = "full_name", length = 128)
    private String fullName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "is_verified", nullable = false)
    private boolean verified = false;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "verified_referrals_count", columnDefinition = "integer default 0")
    private int verifiedReferralsCount = 0;

    @Column(name = "avatar_url")
    private String avatarUrl;

    public void incrementVerifiedReferrals() {
        this.verifiedReferralsCount++;
    }
}


