package com.byvs.backend.service.auth;

import com.byvs.backend.service.service.EmailService;
import com.byvs.backend.service.user.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('ADMIN')")
public class AdminContoller {
    private final OfficeBearerRepository officeBearerAppRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final UserProfileRepository userProfileRepository;
    private final TaskRepository taskRepository;

    public record TaskRequest(
            @NotBlank(message = "Title is required") String title,

            @NotBlank(message = "Description is required") String description,

            @NotNull(message = "Reward coins is required") @Min(value = 1, message = "Reward coins must be at least 1") Integer rewardCoins,

            @NotNull(message = "Assignee ID is required") String assigneeIdGmail,

            Boolean assignToAll,

            @NotNull(message = "Deadline is required") @Future(message = "Deadline must be in the future") LocalDateTime deadline) {
    }

    @GetMapping("/office-bearer-applications")
    public ResponseEntity<Page<OfficeBearerApplication>> getApplications(
            @RequestParam(defaultValue = "false") Boolean approved,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<OfficeBearerApplication> applications;

        if (approved) {
            applications = officeBearerAppRepository.findByApprovedTrue(pageable);
        } else {
            applications = officeBearerAppRepository.findByApprovedFalse(pageable);
        }

        return ResponseEntity.ok(applications);
    }

    @GetMapping("/all-task")
    public ResponseEntity<Page<Task>> getAllTask(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Task> applications;

        applications = taskRepository.findAll(pageable);

        return ResponseEntity.ok(applications);
    }

    @PutMapping("/office-bearer-applications/{id}/approve")
    @Transactional
    public ResponseEntity<?> approveApplication(@PathVariable Long id) {
        OfficeBearerApplication application = officeBearerAppRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Application not found"));

        if (application.getApproved()) {
            return ResponseEntity.badRequest().body("Application already approved");
        }

        application.setApproved(true);
        application.setApprovedAt(LocalDateTime.now());
        officeBearerAppRepository.save(application);

        // Send approval email
        User user = application.getUser();
        UserProfile profile = userProfileRepository.findByUser(user)
                .orElseThrow(() -> new RuntimeException("Profile not found"));
        try {
            emailService.sendOfficeBearerApprovalEmail(
                    profile.getEmail(),
                    profile.getDistrict(),
                    profile.getState(),
                    application.getPosition(),
                    user.getFullName());
            log.info("Initiated welcome email sending for {}", profile.getEmail());
        } catch (Exception e) {
            log.error("Error while initiating welcome email for {}", profile.getEmail(), e);
        }

        return ResponseEntity.ok(Map.of(
                "message", "Application approved successfully",
                "userId", user.getId(),
                "email", profile.getEmail(),
                "name", user.getFullName()));
    }

    @PostMapping("/tasks")
    public ResponseEntity<?> createTask(@AuthenticationPrincipal UserDetails principal,
            @RequestBody @Valid TaskRequest request) {
        if (principal == null || principal.getUsername() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(List.of("Authentication required"));
        }
        User admin = userRepository.findByPhone(principal.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));
        String role = isAdminUser(admin.getPhone()) ? "ADMIN" : "USER";
        if (!role.equals("ADMIN")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(List.of("YOU ARE NOT AN ADMIN"));
        }
        List<Task> createdTasks = new ArrayList<>();
        if (!request.assignToAll) {
            UserProfile Profile = userProfileRepository.findByEmail(request.assigneeIdGmail)
                    .orElseThrow(() -> new RuntimeException("Profile not found"));
            User assignee = userRepository.findById(Profile.getUser().getId())
                    .orElseThrow(() -> new RuntimeException("User not found"));
            Task task = new Task();
            task.setTitle(request.title());
            task.setDescription(request.description());
            task.setRewardCoins(request.rewardCoins());
            task.setDeadline(request.deadline());
            task.setAssignedBy(admin);
            task.setAssignedTo(assignee);
            task.setCreatedAt(LocalDateTime.now());
            createdTasks.add(taskRepository.save(task));
        } else {
            List<OfficeBearerApplication> application = officeBearerAppRepository.findAllApprovedWithUser();
            List<User> assignees = application.stream()
                    .map(OfficeBearerApplication::getUser)
                    .toList();
            for (User assignee : assignees) {
                Task task = new Task();
                task.setTitle(request.title());
                task.setDescription(request.description());
                task.setRewardCoins(request.rewardCoins());
                task.setDeadline(request.deadline());
                task.setAssignedBy(admin);
                task.setAssignedTo(assignee);
                task.setCreatedAt(LocalDateTime.now());
                createdTasks.add(taskRepository.save(task));
            }
        }

        return ResponseEntity.ok(createdTasks);
    }

    @GetMapping("/all-tasks")
    public ResponseEntity<List<Task>> getAllTask() {
        return ResponseEntity.ok(taskRepository.findAll());

    }

    @GetMapping("/office-bearer-applications/approved")
    public ResponseEntity<?> getApprovedOfficeBearer(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<OfficeBearerApplication> applicationsPage = officeBearerAppRepository.findByApprovedTrue(pageable);
        List<OfficeBearerApplication> applications = applicationsPage.getContent();

        List<User> assignees = applications.stream()
                .map(OfficeBearerApplication::getUser)
                .toList();

        Map<User, UserProfile> userProfileMap = userProfileRepository.findByUserIn(assignees)
                .stream()
                .collect(Collectors.toMap(UserProfile::getUser, profile -> profile));

        Map<String, Object> response = new HashMap<>();
        response.put("currentPage", applicationsPage.getNumber());
        response.put("totalPages", applicationsPage.getTotalPages());
        response.put("totalElements", applicationsPage.getTotalElements());
        response.put("pageSize", applicationsPage.getSize());
        response.put("userProfiles", userProfileMap);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/office-bearer-applications/approved/certificate-upload")
    public ResponseEntity<?> uploadCertificateForApprovedOfficeBearer(@RequestParam @NotNull Long userId,
            @RequestPart(required = false) MultipartFile certificate ) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        OfficeBearerApplication application = officeBearerAppRepository.findByUserAndApprovedTrue(user)
                .orElseThrow(() -> new RuntimeException("No approved office bearer application found for this user"));

        if(!application.getApproved()) {
            return ResponseEntity.badRequest().body("User is not an approved office bearer");
        }

        UserProfile profile = userProfileRepository.findByUser(user)
                .orElseThrow(() -> new RuntimeException("Profile not found"));

        try {
            user.setCertificateOfficeBearer(certificate.getBytes());
            userRepository.save(user);
        } catch (java.io.IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "message", "Failed to upload certificate",
                    "error", e.getMessage()
            ));
        }

        return ResponseEntity.ok(Map.of(
                "message", "Certificate uploaded successfully",
                "userId", user.getId(),
                "email", profile.getEmail(),
                "name", user.getFullName(),
                "certificateUrl", certificate.getOriginalFilename()));
    }

    private boolean isAdminUser(String username) {
        return Arrays
                .asList("+919278101008", "9278101008", "8960384718", "+918960384718", "+919026562139", "9026562139")
                .contains(username);
    }

}
