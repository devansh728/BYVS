package com.byvs.backend.service.referral;

import com.byvs.backend.service.user.User;
import com.byvs.backend.service.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Slf4j
public class ReferralTrackingService {

    private final ReferralEventRepository referralEventRepository;
    private final UserRepository userRepository;

    public ReferralTrackingService(ReferralEventRepository referralEventRepository,
                                   UserRepository userRepository) {
        this.referralEventRepository = referralEventRepository;
        this.userRepository = userRepository;
    }

    public String generateShareLink(User user, String baseUrl, String source) {
        // Track share event
        trackShareEvent(user, source, null);

        // Generate unique tracking link
        return baseUrl + "?ref=" + user.getReferralCode() +
                (source != null ? "&source=" + source : "");
    }

    @Transactional
    public void trackShareEvent(User user, String source, HttpServletRequest request) {
        ReferralEvent event = new ReferralEvent(user.getId(), ReferralEventType.SHARE);

        if (request != null) {
            event.setUserAgent(request.getHeader("User-Agent"));
            event.setIpAddress(request.getRemoteAddr());
        }

        if (source != null) {
            event.setReferralSource(source);
        }

        referralEventRepository.save(event);
    }

    @Transactional
    public void trackClickEvent(String referralCode, String source, HttpServletRequest request) {
        User referrer = userRepository.findByReferralCode(referralCode)
                .orElseThrow(() -> new RuntimeException("Invalid referral code"));

        ReferralEvent event = new ReferralEvent(referrer.getId(), ReferralEventType.LINK_CLICK);

        if (request != null) {
            event.setUserAgent(request.getHeader("User-Agent"));
            event.setIpAddress(request.getRemoteAddr());

            // Store cookie for attribution if user signs up later
            String clickId = UUID.randomUUID().toString();
            // In practice, you'd set this in the response cookie
        }

        if (source != null) {
            event.setReferralSource(source);
        }

        referralEventRepository.save(event);
    }

    @Transactional
    public void trackSignupEvent(Long referredUserId, String referralCode) {
        User referrer = userRepository.findByReferralCode(referralCode)
                .orElseThrow(() -> new RuntimeException("Invalid referral code"));

        // Prevent self-referral
        if (referrer.getId().equals(referredUserId)) {
            throw new IllegalArgumentException("Self-referrals are not allowed");
        }

        // Ensure we don't duplicate events
        if (!referralEventRepository.existsByReferredUserIdAndEventType(referredUserId, ReferralEventType.SIGNUP)) {
            ReferralEvent event = new ReferralEvent(referrer.getId(), ReferralEventType.SIGNUP);
            event.setReferredUserId(referredUserId);
            referralEventRepository.save(event);
        }
    }

    public int getUserRank(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (user.getVerifiedReferralsCount() == 0) {
            return (int) (userRepository.countByVerifiedReferralsCountGreaterThan(0) + 1);
        }

        return (int) (userRepository.countByVerifiedReferralsCountGreaterThan(
                        user.getVerifiedReferralsCount()
                ) + 1);
    }

    @Transactional
    public void trackVerificationEvent(Long referredUserId) {
        User referredUser = userRepository.findById(referredUserId)
                .orElseThrow(() -> new IllegalArgumentException("Referred user not found"));
        referralEventRepository.findByReferredUserIdAndEventType(referredUserId, ReferralEventType.SIGNUP)
                .ifPresentOrElse(
                        signupEvent -> {
                            User referrer = userRepository.findById(signupEvent.getReferrerUserId())
                                    .filter(r -> !r.getId().equals(referredUserId))
                                    .orElseThrow(() -> new IllegalStateException("Invalid referrer"));
                            if (referrer.getId().equals(referredUserId)) {
                                throw new IllegalStateException("Self-referral detected");
                            }
                            if (!referralEventRepository.existsByReferredUserIdAndEventType(
                                    referredUserId, ReferralEventType.VERIFICATION)) {

                                ReferralEvent event = new ReferralEvent(
                                        referrer.getId(),
                                        ReferralEventType.VERIFICATION
                                );
                                event.setReferredUserId(referredUserId);
                                referralEventRepository.save(event);
                                referrer.incrementVerifiedReferrals();
                                userRepository.save(referrer);
                            }
                        },
                        () -> log.warn("No signup event found for user {}", referredUserId)
                );
    }
}