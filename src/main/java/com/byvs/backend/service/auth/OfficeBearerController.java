package com.byvs.backend.service.auth;

import com.byvs.backend.service.user.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequiredArgsConstructor
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

    @GetMapping("/get-tasks")
    public ResponseEntity<List<Task>> getTheTask(@AuthenticationPrincipal UserDetails principal){
        User user = userRepository.findByPhone(principal.getUsername()).orElseThrow(() -> new RuntimeException("User not found"));
        List<Task> tasks = taskRepository.findByAssignedTo(user);
        return ResponseEntity.ok(tasks);
    }
}