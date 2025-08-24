package com.byvs.backend.service.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByPhone(String phone);
    Optional<User> findByReferralCode(String referralCode);
    boolean existsByReferralCode(String referralCode);

    boolean existsByPhone(String phone);
    long countByVerifiedReferralsCountGreaterThan(int count);
    @Transactional
    @Modifying
    @Query("UPDATE User u SET u.verified = true WHERE u.phone = :phone")
    void markAsVerified(String phone);

    @Transactional
    @Modifying
    @Query("UPDATE User u SET u.lastLoginAt = CURRENT_TIMESTAMP WHERE u.id = :userId")
    void updateLastLogin(Long userId);
}


