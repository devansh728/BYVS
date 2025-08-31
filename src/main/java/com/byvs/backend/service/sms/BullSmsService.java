package com.byvs.backend.service.sms;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Service
@Slf4j
public class BullSmsService {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${bulksms.api.url}")
    private String apiUrl;

    @Value("${bulksms.user}")
    private String user;

    @Value("${bulksms.key}")
    private String key;

    @Value("${bulksms.sender.id}")
    private String senderId;

    @Value("${bulksms.accusage}")
    private String accUsage;

    @Value("${bulksms.entity.id}")
    private String entityId;

    @Value("${bulksms.temp.id}")
    private String tempId;

    public void sendOtpSms(String mobileNumber, String otp) {
        try {

            String cleanedMobileNumber = mobileNumber;
            if (mobileNumber.startsWith("+91")) {
                cleanedMobileNumber = mobileNumber.substring(3); // Removes the first 3 characters "+91"
            }

            String message = "Dear Customer, Your OTP is " + otp + " for BYVS Login, Please do not share this OTP. Regards";

            String finalUrl = apiUrl +
                    "user=" + user +
                    "&key=" + key +
                    "&mobile=" + cleanedMobileNumber +
                    "&message=" + message +
                    "&senderid=" + senderId +
                    "&accusage=" + accUsage +
                    "&entityid=" + entityId +
                    "&tempid=" + tempId;

            log.info("Sending OTP to mobile: {}", cleanedMobileNumber);
            String response = restTemplate.getForObject(finalUrl, String.class);
            log.info("BulkSMS API response: {}", response);

        } catch (Exception e) {
            log.error("Failed to send OTP to {}: {}", mobileNumber, e.getMessage());
        }
    }
}