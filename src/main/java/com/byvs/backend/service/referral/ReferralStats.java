package com.byvs.backend.service.referral;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ReferralStats {
    private Long referrerUserId;
    private long totalEvents;
    private long signups;
    private long verifications;
}