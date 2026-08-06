package com.stayease.stay.service;

import com.stayease.booking.dto.ReservationResponse;
import com.stayease.booking.service.GuestProfileService;
import com.stayease.booking.service.ReservationService;
import com.stayease.common.client.NotificationClient;
import com.stayease.common.client.PropertyClient;
import com.stayease.common.exception.ResourceNotFoundException;
import com.stayease.stay.dto.GuestReviewRequest;
import com.stayease.stay.dto.GuestReviewResponse;
import com.stayease.stay.entity.GuestReview;
import com.stayease.stay.mapper.GuestReviewMapper;
import com.stayease.stay.repository.GuestReviewRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Business logic for guest reviews.
 *
 * Posting a review notifies the people accountable for the property — its owner
 * and its assigned manager — because a review is feedback they need to see and
 * act on, and neither of them is otherwise told. Delivery is best-effort (see
 * NotificationClient): a review is still saved if notification-service is down.
 */
@Service
@Transactional
public class GuestReviewServiceImpl implements GuestReviewService {

    private static final String CATEGORY_REVIEW = "REVIEW";

    private final GuestReviewRepository repository;
    private final ReservationService reservationService;
    private final GuestProfileService guestProfileService;
    private final PropertyClient propertyClient;
    private final NotificationClient notificationClient;

    public GuestReviewServiceImpl(GuestReviewRepository repository,
                                  ReservationService reservationService,
                                  GuestProfileService guestProfileService,
                                  PropertyClient propertyClient,
                                  NotificationClient notificationClient) {
        this.repository = repository;
        this.reservationService = reservationService;
        this.guestProfileService = guestProfileService;
        this.propertyClient = propertyClient;
        this.notificationClient = notificationClient;
    }

    @Override
    public GuestReviewResponse create(GuestReviewRequest request) {
        validateReferences(request);
        GuestReviewResponse saved =
                GuestReviewMapper.toResponse(repository.save(GuestReviewMapper.toEntity(request)));
        notifyPropertyTeam(saved);
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public List<GuestReviewResponse> getAll(Long reservationId, Long guestId) {
        List<GuestReview> reviews;
        if (reservationId != null) {
            reviews = repository.findByReservationId(reservationId);
        } else if (guestId != null) {
            reviews = repository.findByGuestId(guestId);
        } else {
            reviews = repository.findAll();
        }
        return reviews.stream().map(GuestReviewMapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public GuestReviewResponse getById(Long id) {
        return GuestReviewMapper.toResponse(findOrThrow(id));
    }

    @Override
    public GuestReviewResponse update(Long id, GuestReviewRequest request) {
        GuestReview entity = findOrThrow(id);
        validateReferences(request);
        GuestReviewMapper.updateEntity(entity, request);
        return GuestReviewMapper.toResponse(repository.save(entity));
    }

    @Override
    public void delete(Long id) {
        repository.delete(findOrThrow(id));
    }

    private GuestReview findOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Guest review not found with id " + id));
    }

    // ---------------- notifications (best-effort side effects) ----------------

    /**
     * Tell the reviewed property's owner AND its assigned manager that a review
     * landed. Deduplicated because an owner who manages their own property would
     * otherwise be notified twice, and skipped entirely if the reservation or
     * property can't be resolved — a review is worth keeping even when we can't
     * work out who to tell.
     */
    private void notifyPropertyTeam(GuestReviewResponse review) {
        ReservationResponse reservation;
        try {
            reservation = reservationService.getById(review.reservationId());
        } catch (RuntimeException ex) {
            return;
        }
        propertyClient.findById(reservation.propertyId()).ifPresent(property -> {
            Set<Long> recipients = new LinkedHashSet<>();
            if (property.ownerId() != null) {
                recipients.add(property.ownerId());
            }
            if (property.managerId() != null) {
                recipients.add(property.managerId());
            }
            String message = "New review for " + property.describe() + " from "
                    + guestName(review.guestId()) + score(review) + "."
                    + comment(review)
                    + " Stay: " + reservation.checkInDate() + " to " + reservation.checkOutDate() + ".";
            for (Long recipient : recipients) {
                notificationClient.notifyUser(recipient, message, CATEGORY_REVIEW);
            }
        });
    }

    /** " — 4.5/5" when an overall score was computed, otherwise nothing. */
    private String score(GuestReviewResponse review) {
        return review.overallScore() == null ? "" : " — " + review.overallScore() + "/5";
    }

    /** The guest's words, trimmed to keep the notification readable. */
    private String comment(GuestReviewResponse review) {
        String comments = review.comments();
        if (comments == null || comments.isBlank()) {
            return "";
        }
        String trimmed = comments.strip();
        if (trimmed.length() > 160) {
            trimmed = trimmed.substring(0, 157) + "...";
        }
        return " “" + trimmed + "”";
    }

    private String guestName(Long guestId) {
        try {
            return guestProfileService.getById(guestId).name();
        } catch (RuntimeException ex) {
            return "guest #" + guestId;
        }
    }

    private void validateReferences(GuestReviewRequest request) {
        if (!reservationService.existsById(request.reservationId())) {
            throw new ResourceNotFoundException("Reservation not found with id " + request.reservationId());
        }
        if (!guestProfileService.existsById(request.guestId())) {
            throw new ResourceNotFoundException("Guest profile not found with id " + request.guestId());
        }
    }
}
