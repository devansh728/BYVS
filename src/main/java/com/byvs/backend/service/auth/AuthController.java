package com.byvs.backend.service.auth;

import com.byvs.backend.service.dto.FeedbackRequest;
import com.byvs.backend.service.otp.OtpRateLimitException;
import com.byvs.backend.service.otp.OtpService;
import com.byvs.backend.service.referral.ReferralEventRepository;
import com.byvs.backend.service.referral.ReferralEventType;
import com.byvs.backend.service.referral.ReferralTrackingService;
import com.byvs.backend.service.security.JwtService;
import com.byvs.backend.service.service.EmailService;
import com.byvs.backend.service.sms.SmsService;
import com.byvs.backend.service.user.User;
import com.byvs.backend.service.user.UserProfile;
import com.byvs.backend.service.user.UserProfileRepository;
import com.byvs.backend.service.user.UserRepository;
import com.byvs.backend.service.util.ImageCompressionUtil;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.internal.util.StringUtils;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/auth/otp")
@Validated
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final OtpService otpService;
    private final SmsService smsService;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final ReferralTrackingService referralTrackingService;
    private final ReferralEventRepository referralEventRepository;
    private final UserProfileRepository userProfileRepository;
    private final EmailService emailService;
    private final TransactionTemplate transactionTemplate;
    private static final float COMPRESSION_QUALITY = 0.7f;
    private static final int MAX_IMAGE_WIDTH = 800;
    private static final int MAX_IMAGE_HEIGHT = 600;
    private static final long MAX_IMAGE_SIZE = 5 * 1024 * 1024;

    public record SendOtpRequest(@NotBlank @Pattern(regexp = "^\\+[1-9]\\d{1,14}$") String phone) {
    }

    public record VerifyOtpRequest(@NotBlank @Pattern(regexp = "^\\+[1-9]\\d{1,14}$") String phone,
                                   @NotBlank String otp,
                                   String referralCode,
                                   String fullName) {
    }

    public record RegistrationRequest(
            @NotBlank String fullName,
            @NotNull Integer age,
            @NotBlank @Pattern(regexp = "^\\+[1-9]\\d{1,14}$") String phone,
            @NotBlank @Email String email,
            @NotBlank String whatsappNumber,
            @NotBlank String villageTownCity,
            @NotBlank String blockName,
            @NotBlank String district,
            @NotBlank String state,
            @NotBlank String profession,
            @NotBlank String institutionName,
            @NotBlank String institutionAddress,
            String referralCode,
            MultipartFile photo
    ) {
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    public ResponseEntity<?> register(
            @Valid @RequestPart RegistrationRequest request,
            @RequestPart(required = false) MultipartFile photo
    ) {
        if (userRepository.existsByPhone(request.phone())) {
            return ResponseEntity.badRequest().body("User already exists");
        }

        return transactionTemplate.execute(status -> {
            try {

                // Create user
                User user = new User();
                user.setPhone(request.phone());
                user.setFullName(request.fullName());
                user.setReferralCode(generateUniqueReferralCode());
                user = userRepository.save(user);

                // Create profile
                UserProfile profile = new UserProfile();
                profile.setUser(user);
                profile.setAge(request.age());
                profile.setEmail(request.email());
                profile.setWhatsappNumber(request.whatsappNumber());
                profile.setVillageTownCity(request.villageTownCity());
                profile.setBlockName(request.blockName());
                profile.setDistrict(request.district());
                profile.setState(request.state());
                profile.setProfession(request.profession());
                profile.setInstitutionName(request.institutionName());
                profile.setInstitutionAddress(request.institutionAddress());

                // Handle photo upload
                if (photo != null && !photo.isEmpty()) {
                    if (!ImageCompressionUtil.isImage(photo)) {
                        throw new IllegalArgumentException("File is not a valid image");
                    }

                    if (!ImageCompressionUtil.isImageSizeValid(photo, MAX_IMAGE_SIZE)) {
                        throw new IllegalArgumentException("Image size exceeds the maximum allowed size of 5MB");
                    }
                    try {
                        byte[] compressedImage = ImageCompressionUtil.compressAndSave(
                                photo, COMPRESSION_QUALITY, MAX_IMAGE_WIDTH, MAX_IMAGE_HEIGHT
                        );
                        BufferedImage image = ImageIO.read(new ByteArrayInputStream(compressedImage));
                        profile.setPhoto(compressedImage);
                        profile.setPhotoContentType(photo.getContentType());
                        profile.setPhotoWidth(image.getWidth());
                        profile.setPhotoHeight(image.getHeight());
                    } catch (IOException e) {
                        log.warn("Photo compression produces an error");
                    }
                }
                // Generate membership ID
                String membershipId = "BYVS" + String.format("%08d", user.getId());
                profile.setMembershipId(membershipId);
                profile.setJoinedAt(LocalDateTime.now());
                userProfileRepository.save(profile);
                if (StringUtils.hasText(request.referralCode())) {
                    User finalUser = user;
                    userRepository.findByReferralCode(request.referralCode())
                            .filter(referrer -> !referrer.getId().equals(finalUser.getId())) // Prevent self-referral
                            .ifPresent(referrer -> {
                                finalUser.setReferredByCode(request.referralCode());
                                referralTrackingService.trackSignupEvent(finalUser.getId(), request.referralCode());
                            });
                }

                CompletableFuture.runAsync(() -> {
                    try {
                        emailService.sendWelcomeEmail(
                                request.email(),
                                request.fullName(),
                                membershipId
                        );
                    } catch (Exception e) {
                        log.error("Email sending failed", e);
                    }
                });

                String token = jwtService.generate(user.getPhone(), "USER");

                return ResponseEntity.ok()
                        .header("X-Membership-ID", membershipId)
                        .body(Map.of(
                                "token", token,
                                "membershipId", membershipId,
                                "message", "Registration successful. Welcome to BYVS family!"
                        ));
            } catch (Exception e) {
                status.setRollbackOnly();
                log.error("Registration failed", e);
                return ResponseEntity.internalServerError().body("Registration failed");
            }
        });
    }

    @PostMapping("/api/feedback")
    public ResponseEntity<String> sendFeedback(@RequestBody FeedbackRequest request) {
        try {
            CompletableFuture.runAsync(() -> {
                try {
                    emailService.sendFeedback(
                            request
                    );
                } catch (Exception e) {
                    log.error("Email sending failed", e);
                }
            });
            return ResponseEntity.ok("Feedback sent successfully!");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Failed to send feedback.");
        }
    }

    @PostMapping("/send")
    public ResponseEntity<?> sendOtp(@Valid @RequestBody SendOtpRequest request) {
        try {
            String otp = otpService.generateAndStore(request.phone());
            String message = "Your OTP for ReferralApp is " + otp + ". Valid for 5 minutes.";
            smsService.sendOtp(request.phone(), message);
            return ResponseEntity.accepted().build();
        } catch (OtpRateLimitException e) {
            return ResponseEntity.status(429).body(e.getMessage());
        }
    }

    @PostMapping("/verify")
    @Transactional
    public ResponseEntity<?> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        boolean ok = otpService.verifyAndInvalidate(request.phone(), request.otp());
        if (!ok) {
            return ResponseEntity.status(401).body("Invalid or expired OTP");
        }

        Optional<User> existing = userRepository.findByPhone(request.phone());

        if (existing.isEmpty()) {
            return ResponseEntity.badRequest().body("User does not exist, sign up first");
        }

        User user = existing.get();

        user.setLastLoginAt(Instant.now());
        if (user.getReferredByCode() != null) {
            referralTrackingService.trackVerificationEvent(user.getId());
        }
        userRepository.save(user);

        String role = isAdminUser(user.getPhone()) ? "ADMIN" : "USER";

        String token = jwtService.generate(user.getPhone(), role);
        return ResponseEntity.ok()
                .header("X-User-Role", role) // Add role to header
                .body(new TokenResponse(token));
    }

    @GetMapping("/check-user")
    public ResponseEntity<?> checkResponse(@Valid @RequestParam SendOtpRequest phone) {
        boolean exists = userRepository.existsByPhone(phone.phone());
        return ResponseEntity.ok(Map.of("exists", exists));

    }

    public record TokenResponse(String token) {
    }

    private String generateUniqueReferralCode() {
        String code;
        do {
            code = UUID.randomUUID().toString().replaceAll("-", "").substring(0, 8).toUpperCase();
        } while (userRepository.existsByReferralCode(code));
        return code;
    }

    @GetMapping("/user/photo")
    @Transactional
    public ResponseEntity<?> getUserPhoto(@AuthenticationPrincipal UserDetails principal) {
        if (principal == null || principal.getUsername() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Authentication required"));
        }
        User user = userRepository.findByPhone(principal.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));
        Optional<byte[]> photoData = userProfileRepository.findPhotoById(user.getId());

        if (photoData.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.IMAGE_JPEG);
        headers.setCacheControl(CacheControl.maxAge(7, TimeUnit.DAYS).cachePublic());

        return new ResponseEntity<>(photoData.get(), headers, HttpStatus.OK);
    }

    @GetMapping("/me")
    @Transactional
    public ResponseEntity<?> getCurrentUser(@AuthenticationPrincipal UserDetails principal) {
        if (principal == null || principal.getUsername() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Authentication required"));
        }
        User user = userRepository.findByPhone(principal.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        long userId = user.getId();
        UserProfile profile = userProfileRepository.findByUserId(userId)
                .orElse(new UserProfile()); // Return empty profile if not found

        String role = isAdminUser(user.getPhone()) ? "ADMIN" : "USER";

        Map<String, Object> response = new HashMap<>();
        response.put("userId", user.getId());
        response.put("fullName", user.getFullName() != null ? user.getFullName() : "");
        response.put("phone", user.getPhone() != null ? user.getPhone() : "");
        response.put("referralCode", user.getReferralCode() != null ? user.getReferralCode() : "");
        response.put("verifiedReferrals", referralEventRepository.countByReferrerUserIdAndEventType(
                user.getId(), ReferralEventType.VERIFICATION));
        response.put("lastLogin", user.getLastLoginAt() != null ? user.getLastLoginAt() : "");
        response.put("state", profile.getState() != null ? profile.getState() : "");
        response.put("district", profile.getDistrict() != null ? profile.getDistrict() : "");

        if (profile.getJoinedAt() != null) {
            response.put("joinedDate", profile.getJoinedAt().atZone(ZoneId.systemDefault()).toLocalDate().toString());
        } else {
            response.put("joinedDate", "");
        }

        response.put("membershipId", profile.getMembershipId() != null ? profile.getMembershipId() : "");
        response.put("email", profile.getEmail() != null ? profile.getEmail() : "");

        return ResponseEntity.ok()
                .header("X-User-Role", role)
                .body(response);


    }

    private boolean isAdminUser(String username) {
        return Arrays.asList("+919026562139", "+919508245925", "9026562139").contains(username);
    }
}


