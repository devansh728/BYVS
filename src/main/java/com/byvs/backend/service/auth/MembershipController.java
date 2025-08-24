package com.byvs.backend.service.auth;

import com.byvs.backend.service.user.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

// MembershipController.java
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/membership")
public class MembershipController {

    private final UserProfileRepository userProfileRepository;
    private final UserRepository userRepository;
    private final OfficeBearerRepository officeBearerAppRepository;
    @GetMapping("/id-card")
    public ResponseEntity<Map<String, Object>> getIdCardData(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        User user = userRepository.findByPhone(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        UserProfile profile = userProfileRepository.findByUser(user)
                .orElseThrow(() -> new RuntimeException("Profile not found"));

        String membershipId = "BYVS" + String.format("%08d", user.getId());
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy");

        return ResponseEntity.ok(Map.of(
                "membershipId", membershipId,
                "fullName", user.getFullName(),
                "photoPath", profile.getPhotoPath(),
                "district", profile.getDistrict(),
                "state", profile.getState(),
                "joinDate", user.getCreatedAt().atZone(ZoneId.systemDefault()).toLocalDate().toString(),
                "isOfficeBearer", officeBearerAppRepository.existsByUserAndApprovedTrue(user)
        ));
    }

    @GetMapping("/certificate")
    public ResponseEntity<Map<String, Object>> getCertificateData(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        User user = userRepository.findByPhone(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        OfficeBearerApplication application = officeBearerAppRepository.findByUserAndApprovedTrue(user)
                .orElseThrow(() -> new RuntimeException("Not an office bearer"));

        return ResponseEntity.ok(Map.of(
                "fullName", user.getFullName(),
                "district", application.getDistrict(),
                "state", application.getState(),
                "approvalDate", application.getApprovedAt().toLocalDate().toString(),
                "socialWork", application.getSocialWorkDescription()
        ));
    }
}