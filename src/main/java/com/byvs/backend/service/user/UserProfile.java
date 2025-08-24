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
public class UserProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "user_id")
    private User user;

    private String photoPath; // Store path to compressed image
    private Integer age;
    private String whatsappNumber;
    private String email;
    private String villageTownCity;
    private String blockName;
    private String district;
    private String state;
    private String profession;
    private String institutionName;
    private String institutionAddress;
    private LocalDateTime joinedAt;
    private String membershipId;
    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(columnDefinition = "BYTEA")
    private byte[] photo;
    private String photoContentType;
    private Integer photoWidth;
    private Integer photoHeight;
}
