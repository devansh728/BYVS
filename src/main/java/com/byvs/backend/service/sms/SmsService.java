package com.byvs.backend.service.sms;

public interface SmsService {
    void sendOtp(String phoneE164, String message);
}


