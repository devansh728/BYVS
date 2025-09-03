package com.byvs.backend.service.auth;

import com.byvs.backend.service.user.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/api/office-bearer")
public class OfficeBearerController {

    private final OfficeBearerRepository appRepository;
    private final UserProfileRepository userProfileRepository;
    private final UserRepository userRepository;
    private final TaskRepository taskRepository;

    public record OfficeBearerRequest(
            @NotBlank(message = "District is required")
            String district,

            @NotBlank(message = "State is required")
            String state,

            String position,

            @NotBlank(message = "Contact details are required")
            @Pattern(regexp = "^\\+[1-9]\\d{1,14}$", message = "Invalid contact number format")
            String contactDetails,

            @NotBlank(message = "Social work description is required")
            @Size(min = 50, max = 1000, message = "Description must be between 50-1000 characters")
            String socialWorkDescription
    ) {}

    @PostMapping("/apply")
    @Transactional
    public ResponseEntity<?> apply(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody @Valid OfficeBearerRequest request
    ) {
        User user = userRepository.findByPhone(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Check if already applied
        if (appRepository.existsByUserAndApprovedFalse(user)) {
            return ResponseEntity.badRequest().body("Pending application already exists");
        }

        // Check if already approved
        if (appRepository.existsByUserAndApprovedTrue(user)) {
            return ResponseEntity.badRequest().body("Already an office bearer");
        }

        UserProfile profile = userProfileRepository.findByUser(user)
                .orElseThrow(() -> new RuntimeException("Complete your profile first"));

        OfficeBearerApplication application = new OfficeBearerApplication();
        application.setUser(user);
        application.setDistrict(request.district());
        application.setState(request.state());
        application.setPosition(request.position());
        application.setContactDetails(request.contactDetails());
        application.setSocialWorkDescription(request.socialWorkDescription());
        application.setAppliedAt(LocalDateTime.now());

        appRepository.save(application);

        return ResponseEntity.ok(Map.of(
                "message", "Application submitted successfully",
                "applicationId", application.getId()
        ));
    }

    @GetMapping("/status")
    public ResponseEntity<?> getStatus(@AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepository.findByPhone(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        Optional<OfficeBearerApplication> application = appRepository.findByUser(user);

        if (application.isEmpty()) {
            return ResponseEntity.ok(Map.of("status", "NOT_APPLIED"));
        }

        return ResponseEntity.ok(Map.of(
                "status", application.get().getApproved() ? "APPROVED" : "PENDING",
                "application", application.get()
        ));
    }
    @GetMapping("/approved-office-bearers")
    @Cacheable(value = "approvedOfficeBearers", key = "#district")
    public ResponseEntity<?> getAllApprovedOfficeBearer(@RequestParam(required = false) String district){
        try{
            log.info("API call received to fetch approved office bearers for district: {}", district);
            if (district == null || district.trim().isEmpty()) {
                log.warn("Bad Request: District parameter is null or empty.");
                return ResponseEntity.badRequest().body("District parameter cannot be empty.");
            }
            List<OfficeBearerApplication> applications = appRepository.findByDistrictAndApprovedTrue(district);

            List<Map<String, Object>> userDataList = applications.stream()
                    .map(application -> Map.of(
                            "position", application.getPosition() != null ? application.getPosition() : "Unknown",
                            "userData", application.getUser()
                    ))
                    .collect(Collectors.toList());
            if (userDataList.isEmpty()) {
                log.info("No approved office bearers found for district: {}. Returning an empty list.", district);
            } else {
                log.info("Successfully fetched {} approved office bearers for district: {}.", userDataList.size(), district);
            }

            return ResponseEntity.ok(userDataList);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Internal Server issue");
        }
    }

    @GetMapping("/get-tasks")
    public ResponseEntity<List<Task>> getTheTask(@AuthenticationPrincipal UserDetails principal){
        User user = userRepository.findByPhone(principal.getUsername()).orElseThrow(() -> new RuntimeException("User not found"));
        List<Task> tasks = taskRepository.findByAssignedTo(user);
        return ResponseEntity.ok(tasks);
    }
}