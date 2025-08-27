package com.byvs.backend.service.user;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Type;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "user_profile")
public class UserProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "photo_path")
    private String photoPath;

    @Column(name = "age")
    private Integer age;

    @Column(name = "whatsapp_number")
    private String whatsappNumber;

    @Column(name = "email")
    private String email;

    @Column(name = "village_town_city")
    private String villageTownCity;

    @Column(name = "block_name")
    private String blockName;

    @Column(name = "district")
    private String district;

    @Column(name = "state")
    private String state;

    @Column(name = "profession")
    private String profession;

    @Column(name = "institution_name")
    private String institutionName;

    @Column(name = "institution_address")
    private String institutionAddress;

    @Column(name = "joined_at")
    private LocalDateTime joinedAt;

    @Column(name = "membership_id")
    private String membershipId;
    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "photo", columnDefinition = "BYTEA")
    private byte[] photoData;

    @Column(name = "photo_content_type")
    private String photoContentType;

    @Column(name = "photo_width")
    private Integer photoWidth;

    @Column(name = "photo_height")
    private Integer photoHeight;
}