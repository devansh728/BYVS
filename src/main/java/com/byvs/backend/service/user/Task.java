package com.byvs.backend.service.user;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class Task {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;
    private String description;
    private Integer rewardCoins;
    private LocalDateTime createdAt;
    private LocalDateTime deadline;

    @ManyToOne
    private User assignedBy; // Admin who assigned the task

    @ManyToOne
    private User assignedTo;

    private Boolean completed = false;
}
