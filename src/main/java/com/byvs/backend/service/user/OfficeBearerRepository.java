package com.byvs.backend.service.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface OfficeBearerRepository extends JpaRepository<OfficeBearerApplication,Long> {
    Page<OfficeBearerApplication> findByApprovedTrue(Pageable pageable);

    Page<OfficeBearerApplication> findByApprovedFalse(Pageable pageable);

    Optional<OfficeBearerApplication> findByDistrict(String district);

    boolean existsByUserAndApprovedFalse(User user);

    boolean existsByUserAndApprovedTrue(User user);

    Optional<OfficeBearerApplication> findByUser(User user);

    Optional<OfficeBearerApplication> findByUserAndApprovedTrue(User user);
    List<OfficeBearerApplication> findByDistrictAndApprovedTrue(String district);

    @Query("SELECT o FROM OfficeBearerApplication o JOIN FETCH o.user WHERE o.approved = true")
    List<OfficeBearerApplication> findAllApprovedWithUser();

}

