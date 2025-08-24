package com.byvs.backend.service.sms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnMissingBean(TwilioSmsService.class)
public class NoopSmsService implements SmsService {
    private static final Logger log = LoggerFactory.getLogger(NoopSmsService.class);

    @Override
    public void sendOtp(String phoneE164, String message) {
        log.info("[NOOP SMS] to={} message={}", phoneE164, message);
    }
}


