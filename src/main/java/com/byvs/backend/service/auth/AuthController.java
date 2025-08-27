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
import jakarta.validation.constraints.*;
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

    public record ProfileUpdateRequest(
            @Size(min = 2, max = 100, message = "Full name must be between 2 and 100 characters")
            String fullName,

            @Pattern(regexp = "^\\+[1-9]\\d{1,14}$", message = "Invalid phone number")
            String phone,

            Integer age,

            @Email(message = "Invalid email address")
            String email,

            @Pattern(regexp = "^\\+[1-9]\\d{1,14}$", message = "Invalid WhatsApp number")
            String whatsappNumber,

            @Size(max = 100, message = "Village/Town/City name cannot exceed 100 characters")
            String villageTownCity,

            @Size(max = 100, message = "Block name cannot exceed 100 characters")
            String blockName,

            @Size(max = 100, message = "District name cannot exceed 100 characters")
            String district,

            @Size(max = 100, message = "State name cannot exceed 100 characters")
            String state,

            @Size(max = 100, message = "Profession cannot exceed 100 characters")
            String profession,

            @Size(max = 255, message = "Institution name cannot exceed 255 characters")
            String institutionName,

            @Size(max = 255, message = "Institution address cannot exceed 255 characters")
            String institutionAddress,

            Boolean deletePhoto
    ) {}

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
            String referralCode
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

                        // Check if the compression returned a valid byte array
                        if (compressedImage != null && compressedImage.length > 0) {
                            BufferedImage image = ImageIO.read(new ByteArrayInputStream(photo.getBytes()));
                            profile.setPhotoData(photo.getBytes());
                            profile.setPhotoContentType(photo.getContentType());
                            profile.setPhotoWidth(image.getWidth());
                            profile.setPhotoHeight(image.getHeight());
                        } else {
                            throw new IOException("Compressed image data is empty or null");
                        }

                    } catch (IOException e) {
                        log.error("Photo compression or processing failed", e);
                        // Rollback the transaction and throw a detailed exception
                        throw new RuntimeException("Registration failed due to photo processing error", e);
                    }
                } else {
                    profile.setPhotoData(null);
                }
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
                if (e instanceof IllegalArgumentException) {
                    return ResponseEntity.badRequest().body(e.getMessage());
                }
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

    @PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    public ResponseEntity<?> update(
            @AuthenticationPrincipal UserDetails principal,
            @Valid @RequestPart ProfileUpdateRequest request,
            @RequestPart(required = false) MultipartFile photo
    ) {

        if (principal == null || principal.getUsername() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Authentication required"));
        }
        return transactionTemplate.execute(status -> {
            try {
                // Find the user and profile
                User user = userRepository.findByPhone(principal.getUsername())
                        .orElseThrow(() -> new RuntimeException("User not found"));

                long userId = user.getId();
                UserProfile existingProfile = userProfileRepository.findByUserId(userId)
                        .orElse(new UserProfile());

                // Check if phone number is being updated
                if (request.phone() != null && !request.phone().equals(user.getPhone())) {
                    if (userRepository.existsByPhone(request.phone())) {
                        throw new IllegalArgumentException("Phone number is already in use");
                    }
                    user.setPhone(request.phone());
                }

                // Update user details
                if (request.fullName() != null) {
                    user.setFullName(request.fullName());
                }
                userRepository.save(user);

                // Update profile details
                if (request.age() != null) {
                    existingProfile.setAge(request.age());
                }
                if (request.email() != null) {
                    existingProfile.setEmail(request.email());
                }
                if (request.whatsappNumber() != null) {
                    existingProfile.setWhatsappNumber(request.whatsappNumber());
                }
                if (request.villageTownCity() != null) {
                    existingProfile.setVillageTownCity(request.villageTownCity());
                }
                if (request.blockName() != null) {
                    existingProfile.setBlockName(request.blockName());
                }
                if (request.district() != null) {
                    existingProfile.setDistrict(request.district());
                }
                if (request.state() != null) {
                    existingProfile.setState(request.state());
                }
                if (request.profession() != null) {
                    existingProfile.setProfession(request.profession());
                }
                if (request.institutionName() != null) {
                    existingProfile.setInstitutionName(request.institutionName());
                }
                if (request.institutionAddress() != null) {
                    existingProfile.setInstitutionAddress(request.institutionAddress());
                }

                // Handle photo update
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

                        if (compressedImage != null && compressedImage.length > 0) {
                            BufferedImage image = ImageIO.read(new ByteArrayInputStream(compressedImage));
                            existingProfile.setPhotoData(compressedImage);
                            existingProfile.setPhotoContentType(photo.getContentType());
                            existingProfile.setPhotoWidth(image.getWidth());
                            existingProfile.setPhotoHeight(image.getHeight());
                        } else {
                            throw new IOException("Compressed image data is empty or null");
                        }
                    } catch (IOException e) {
                        log.error("Photo compression or processing failed", e);
                        throw new RuntimeException("Profile update failed due to photo processing error", e);
                    }
                } else if (request.deletePhoto() != null && request.deletePhoto()) {
                    // Allows clients to explicitly request photo deletion by passing a flag
                    existingProfile.setPhotoData(null);
                    existingProfile.setPhotoContentType(null);
                    existingProfile.setPhotoWidth(null);
                    existingProfile.setPhotoHeight(null);
                }

                userProfileRepository.save(existingProfile);

                // Generate new token if phone number was updated
                String newToken = null;
                if (request.phone() != null && !request.phone().equals(user.getPhone())) {
                    newToken = jwtService.generate(user.getPhone(), "USER");
                }

                Map<String, Object> responseBody = new HashMap<>();
                responseBody.put("message", "Profile updated successfully");
                if (newToken != null) {
                    responseBody.put("token", newToken);
                    responseBody.put("message", "Profile updated successfully. A new token has been generated due to phone number change.");
                }

                return ResponseEntity.ok().body(responseBody);

            } catch (Exception e) {
                status.setRollbackOnly();
                log.error("Profile update failed", e);
                if (e instanceof IllegalArgumentException) {
                    return ResponseEntity.badRequest().body(e.getMessage());
                }
                return ResponseEntity.internalServerError().body("Profile update failed");
            }
        });
    }

    @GetMapping("/user/photo")
    @Transactional
    public ResponseEntity<?> getUserPhoto(@AuthenticationPrincipal UserDetails principal) {
        if (principal == null || principal.getUsername() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Authentication required"));
        }
        User user = userRepository.findByPhone(principal.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));
        Optional<byte[]> photoData = userProfileRepository.findPhotoDataById(user.getId());

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
        return Arrays.asList("+919508245925", "9508245925").contains(username);
    }
}


