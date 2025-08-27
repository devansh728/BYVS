package com.byvs.backend.service.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserProfileRepository extends JpaRepository<UserProfile,Long> {
    Optional<UserProfile> findByUser(User user);
    Optional<UserProfile> findByUserId(Long userId);
    @Query("SELECT u.photoData FROM UserProfile u WHERE u.id = :id")
    Optional<byte[]> findPhotoDataById(@Param("id") Long id);

    @Query("SELECT u.photoContentType FROM UserProfile u WHERE u.id = :id")
    Optional<String> findPhotoContentTypeById(@Param("id") Long id);
}
