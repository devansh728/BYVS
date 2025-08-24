package com.byvs.backend.service.referral;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "referral_events", indexes = {
        @Index(name = "idx_referrer_user", columnList = "referrerUserId"),
        @Index(name = "idx_referred_user", columnList = "referredUserId"),
        @Index(name = "idx_event_type", columnList = "eventType"),
        @Index(name = "idx_occurred_at", columnList = "occurredAt")
})
@Getter
@Setter
@NoArgsConstructor
public class ReferralEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "referrer_user_id", nullable = false)
    private Long referrerUserId;

    @Column(name = "referred_user_id")
    private Long referredUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 32)
    private ReferralEventType eventType;

    @CreationTimestamp
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "tracking_id", nullable = false, unique = true)
    private String trackingId = UUID.randomUUID().toString();

    // Additional metadata fields
    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "referral_source")
    private String referralSource; // e.g., "WHATSAPP", "EMAIL", etc.

    public ReferralEvent(Long referrerUserId, ReferralEventType eventType) {
        this.referrerUserId = referrerUserId;
        this.eventType = eventType;
    }
}