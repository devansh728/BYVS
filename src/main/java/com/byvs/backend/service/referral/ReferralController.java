package com.byvs.backend.service.referral;

import com.byvs.backend.service.user.User;
import com.byvs.backend.service.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/referrals")
@Validated
public class ReferralController {

    private final UserRepository userRepository;
    private final ReferralEventRepository referralEventRepository;
    private final ReferralTrackingService referralTrackingService;
    private final RateLimitService rateLimitService;

    public ReferralController(UserRepository userRepository,
                              ReferralEventRepository referralEventRepository,
                              ReferralTrackingService referralTrackingService,
                              RateLimitService rateLimitService) {
        this.userRepository = userRepository;
        this.referralEventRepository = referralEventRepository;
        this.referralTrackingService = referralTrackingService;
        this.rateLimitService = rateLimitService;
    }

    @GetMapping("/share-link")
    @Transactional
    public ResponseEntity<?> getShareLink(
            @AuthenticationPrincipal UserDetails principal,
            @RequestParam(defaultValue = "https://localhost:5173/") String baseUrl,
            @RequestParam(required = false) String source) {

        User user = userRepository.findByPhone(principal.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        String shareLink = referralTrackingService.generateShareLink(user, baseUrl, source);

        return ResponseEntity.ok(Map.of(
                "shareLink", shareLink,
                "referralCode", user.getReferralCode()
        ));
    }

    @PostMapping("/track/share")
    @Transactional
    public ResponseEntity<?> trackShare(
            @AuthenticationPrincipal UserDetails principal,
            @RequestParam(required = false) String source,
            HttpServletRequest request) {

        User user = userRepository.findByPhone(principal.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        referralTrackingService.trackShareEvent(user, source, request);

        return ResponseEntity.accepted().build();
    }

    @GetMapping("/track/click")
    @Transactional
    public ResponseEntity<?> trackClick(
            @RequestParam String code,
            @RequestParam(required = false) String source,
            HttpServletRequest request) {
        if (!userRepository.existsByReferralCode(code)) {
            return ResponseEntity.badRequest().body("Invalid referral code");
        }
        rateLimitService.checkRateLimit(
                "click:" + request.getRemoteAddr(),
                10,
                Duration.ofHours(1)
        );

        referralTrackingService.trackClickEvent(code, source, request);
        return ResponseEntity.status(302)
                .header("Location", "/register?ref=" + code)
                .build();
    }

    @GetMapping("/history")
    @Transactional
    public Page<ReferralEvent> getReferralHistory(
            @AuthenticationPrincipal UserDetails principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        User user = userRepository.findByPhone(principal.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        Pageable pageable = PageRequest.of(page, size);
        return referralEventRepository.findByReferrerUserIdOrderByOccurredAtDesc(user.getId(), pageable);
    }

    @GetMapping("/leaderboard/daily")
    @Transactional
    public ResponseEntity<Page<Map<String, Object>>> getDailyLeaderboard(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Instant start = today.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant end = today.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);

        Pageable pageable = PageRequest.of(page, size);
        Page<LeaderboardProjection> statsPage = referralEventRepository
                .getLeaderboardStats(start, end, pageable);

        List<Map<String, Object>> enrichedStats = enrichWithUserDetails(statsPage.getContent(), start, end);

        Page<Map<String, Object>> enrichedPage = new PageImpl<>(
                enrichedStats,
                pageable,
                statsPage.getTotalElements()
        );

        return ResponseEntity.ok(enrichedPage);
    }

    @GetMapping("/leaderboard/weekly")
    @Transactional
    public ResponseEntity<?> getWeeklyLeaderboard(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate sunday = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        Instant start = monday.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant end = sunday.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);

        Pageable pageable = PageRequest.of(page, size);
        Page<LeaderboardProjection> statsPage = referralEventRepository
                .getLeaderboardStats(start, end, pageable);

        List<Map<String, Object>> enrichedStats = enrichWithUserDetails(statsPage.getContent(), start, end);

        Page<Map<String, Object>> enrichedPage = new PageImpl<>(
                enrichedStats,
                pageable,
                statsPage.getTotalElements()
        );

        return ResponseEntity.ok(enrichedPage);
    }

    @GetMapping("/total-referralVer")
    @Transactional
    public long getTotalReferralsVerified(@AuthenticationPrincipal UserDetails principal){
        User user = userRepository.findByPhone(principal.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));
        return referralEventRepository.countVerifiedReferrals(user.getId());
    }

    @GetMapping("/userStats")
    @Transactional
    public ResponseEntity<?> getTotalReferralsSign(@AuthenticationPrincipal UserDetails principal){
        User user = userRepository.findByPhone(principal.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        Long referrerUserId = user.getId();

        Long totalReferralsSign = referralEventRepository.countByReferrerUserIdAndEventType(referrerUserId, ReferralEventType.SIGNUP);
        int userRank = referralTrackingService.getUserRank(referrerUserId);
        return ResponseEntity.ok(Map.of("totalShares",totalReferralsSign,"currentRank",userRank));
    }

    @GetMapping("/leaderboard/monthly")
    @Transactional
    public ResponseEntity<?> getMonthlyLeaderboard(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Instant start = today.withDayOfMonth(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant end = today.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC); // Include today

        Pageable pageable = PageRequest.of(page, size);
        Page<LeaderboardProjection> statsPage = referralEventRepository
                .getLeaderboardStats(start, end, pageable);
        List<Map<String, Object>> enrichedStats = enrichWithUserDetails(statsPage.getContent(), start, end);
        Page<Map<String, Object>> enrichedPage = new PageImpl<>(
                enrichedStats,
                pageable,
                statsPage.getTotalElements()
        );

        return ResponseEntity.ok(enrichedPage);
    }

    private List<Map<String, Object>> enrichWithUserDetails(List<LeaderboardProjection> stats, Instant start, Instant end) {
        if (stats == null) {
            return new ArrayList<>();
        }

        return stats.stream()
                .filter(Objects::nonNull)
                .map(stat -> {
                    User user = userRepository.findById(stat.getReferrerUserId())
                            .orElseThrow(() -> new RuntimeException("User not found for ID: " + stat.getReferrerUserId()));
                    Map<String, Object> userDetails = new HashMap<>();

                    long rank = referralEventRepository.countUsersAbove(
                            stat.getReferrerUserId(),
                            start,
                            end
                    ) + 1;
                    userDetails.put("rank", rank);
                    userDetails.put("userId", user.getId());
                    userDetails.put("name", user.getFullName());
                    String referralCode = user.getReferralCode();
                    if (referralCode != null) {
                        userDetails.put("referralCode", referralCode);
                    }

                    Long verifications = stat.getVerifications();
                    if (verifications != null) {
                        userDetails.put("verifiedReferrals", verifications);
                    }

                    Long signups = stat.getSignups();
                    if (signups != null) {
                        userDetails.put("totalSignups", signups);
                    }

                    if (signups != null && verifications != null) {
                        double conversionRate = calculateConversionRate(signups, verifications);
                        userDetails.put("conversionRate", conversionRate);
                    }

                    return userDetails;
                })
                .collect(Collectors.toList());
    }

    private double calculateConversionRate(long signups, long verifications) {
        return signups > 0 ? (verifications * 100.0 / signups) : 0;
    }

}