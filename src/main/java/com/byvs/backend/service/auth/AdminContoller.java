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
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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
            @NotBlank(message = "Title is required")
            String title,

            @NotBlank(message = "Description is required")
            String description,

            @NotNull(message = "Reward coins is required")
            @Min(value = 1, message = "Reward coins must be at least 1")
            Integer rewardCoins,

            @NotNull(message = "Assignee ID is required")
            Long assigneeId,

            @NotNull(message = "Deadline is required")
            @Future(message = "Deadline must be in the future")
            LocalDateTime deadline
    ) {}

    @GetMapping("/office-bearer-applications")
    public ResponseEntity<Page<OfficeBearerApplication>> getApplications(
            @RequestParam(defaultValue = "false") Boolean approved,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
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
            @RequestParam(defaultValue = "10") int size
    ) {
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
                    user.getFullName()
            );
            log.info("Initiated welcome email sending for {}", profile.getEmail());
        }catch(Exception e){
            log.error("Error while initiating welcome email for {}", profile.getEmail(), e);
        }

        return ResponseEntity.ok(Map.of(
                "message", "Application approved successfully",
                "userId", user.getId(),
                "email", profile.getEmail(),
                "name", user.getFullName()
        ));
    }

    @PostMapping("/tasks")
    public ResponseEntity<Task> createTask(@RequestBody @Valid TaskRequest request) {
        User admin = userRepository.findByPhone("+919026562139").orElse(null);
        User assignee = userRepository.findById(request.assigneeId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        Task task = new Task();
        task.setTitle(request.title());
        task.setDescription(request.description());
        task.setRewardCoins(request.rewardCoins());
        task.setDeadline(request.deadline());
        task.setAssignedBy(admin);
        task.setAssignedTo(assignee);
        task.setCreatedAt(LocalDateTime.now());

        task = taskRepository.save(task);

        return ResponseEntity.ok(task);
    }
    @GetMapping("/all-tasks")
    public ResponseEntity<List<Task>> getAllTask(){
        return ResponseEntity.ok(taskRepository.findAll());

    }

}
