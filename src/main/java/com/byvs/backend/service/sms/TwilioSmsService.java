    package com.byvs.backend.service.sms;

    import com.twilio.Twilio;
    import com.twilio.exception.ApiException;
    import com.twilio.rest.api.v2010.account.Message;
    import com.twilio.type.PhoneNumber;
    import org.springframework.beans.factory.annotation.Value;
    import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
    import org.springframework.stereotype.Service;

    @Service
    @ConditionalOnProperty(prefix = "twilio", name = "enabled", havingValue = "true")
    public class TwilioSmsService implements SmsService {

        public TwilioSmsService(
            @Value("${twilio.accountSid}") String accountSid,
            @Value("${twilio.authToken}") String authToken
        ) {
            Twilio.init(accountSid, authToken);
        }

        @Value("${twilio.fromNumber:+10000000000}")
        private String fromNumber;

        @Override
        public void sendOtp(String phoneE164, String message) {
            try {
                Message.creator(new PhoneNumber(phoneE164), new PhoneNumber(fromNumber), message).create();
            } catch (ApiException ex) {
                throw new RuntimeException("Failed to send SMS: " + ex.getMessage(), ex);
            }
        }
    }


