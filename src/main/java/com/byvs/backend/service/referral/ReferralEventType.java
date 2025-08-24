package com.byvs.backend.service.referral;

public enum ReferralEventType {
    SHARE,          // When user shares their referral link
    LINK_CLICK,     // When someone clicks the referral link
    SIGNUP,         // When referred user signs up
    VERIFICATION,   // When referred user verifies phone/email
}