package com.stayease.booking.service;

import com.stayease.booking.dto.ReservationRequest;
import com.stayease.booking.dto.ReservationResponse;
import com.stayease.booking.entity.Reservation;
import com.stayease.booking.enums.ReservationStatus;
import com.stayease.booking.mapper.ReservationMapper;
import com.stayease.booking.repository.ReservationRepository;
import com.stayease.common.client.NotificationClient;
import com.stayease.common.client.PropertyClient;
import com.stayease.common.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Business logic for reservations.
 *
 * Collaborators by constructor injection:
 *  - ReservationRepository (own data)
 *  - PropertyClient        (validate propertyId, and resolve the property's
 *                           manager/owner — remote calls to property-service)
 *  - GuestProfileService   (validate guestId, and resolve the guest's user id)
 *  - NotificationClient    (tell people what happened — best-effort)
 *
 * A reservation can't exist without a real property AND a real guest, so we
 * check both before saving. The dates are validated too, then the mapper
 * computes nights + totalAmount.
 *
 * Notifications close the loop between a guest and a property manager:
 *   guest requests  → the property's manager is told there's something to approve
 *   manager approves → the guest gets a confirmation naming the property and dates
 *   manager rejects  → the guest is told, so they can look elsewhere
 * All of them are best-effort side effects (see NotificationClient); a
 * notification never decides whether a booking succeeds.
 */
@Service
@Transactional
public class ReservationServiceImpl implements ReservationService {

    private static final String CATEGORY_BOOKING = "BOOKING";
    /** Written as an escape so the source compiles under any platform encoding. */
    private static final String RUPEE = "\u20B9";

    private final ReservationRepository repository;
    private final PropertyClient propertyClient;
    private final GuestProfileService guestProfileService;
    private final NotificationClient notificationClient;

    public ReservationServiceImpl(ReservationRepository repository,
                                  PropertyClient propertyClient,
                                  GuestProfileService guestProfileService,
                                  NotificationClient notificationClient) {
        this.repository = repository;
        this.propertyClient = propertyClient;
        this.guestProfileService = guestProfileService;
        this.notificationClient = notificationClient;
    }

    @Override
    public ReservationResponse create(ReservationRequest request) {
        validateReferencesAndDates(request);
        // A NEW booking can never start in the past. Only enforced on create: an
        // existing reservation still has to be editable (and correctable) after
        // its stay has begun.
        if (request.checkInDate().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException(
                    "checkInDate " + request.checkInDate() + " is in the past — choose today or a later date");
        }
        ReservationResponse saved =
                ReservationMapper.toResponse(repository.save(ReservationMapper.toEntity(request)));
        notifyManagerOfRequest(saved);
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReservationResponse> getAll(Long propertyId, Long guestId) {
        List<Reservation> reservations;
        if (propertyId != null) {
            reservations = repository.findByPropertyId(propertyId);
        } else if (guestId != null) {
            reservations = repository.findByGuestId(guestId);
        } else {
            reservations = repository.findAll();
        }
        return reservations.stream().map(ReservationMapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ReservationResponse getById(Long id) {
        return ReservationMapper.toResponse(findOrThrow(id));
    }

    @Override
    public ReservationResponse update(Long id, ReservationRequest request) {
        Reservation reservation = findOrThrow(id);
        validateReferencesAndDates(request);
        ReservationMapper.updateEntity(reservation, request);
        return ReservationMapper.toResponse(repository.save(reservation));
    }

    @Override
    public ReservationResponse approve(Long id) {
        Reservation reservation = findOrThrow(id);
        if (reservation.getStatus() != ReservationStatus.PENDING) {
            throw new IllegalArgumentException("Only a pending reservation can be approved");
        }
        // Hold the dates now (they were left AVAILABLE while pending). This
        // throws if any night was taken in the meantime, so the transaction
        // rolls back and the reservation stays PENDING.
        propertyClient.markRangeBooked(
                reservation.getPropertyId(), reservation.getCheckInDate(), reservation.getCheckOutDate());
        reservation.setStatus(ReservationStatus.CONFIRMED);
        ReservationResponse saved = ReservationMapper.toResponse(repository.save(reservation));
        notifyGuestOfDecision(saved, true);
        return saved;
    }

    @Override
    public ReservationResponse reject(Long id) {
        Reservation reservation = findOrThrow(id);
        if (reservation.getStatus() != ReservationStatus.PENDING) {
            throw new IllegalArgumentException("Only a pending reservation can be rejected");
        }
        reservation.setStatus(ReservationStatus.CANCELLED);
        ReservationResponse saved = ReservationMapper.toResponse(repository.save(reservation));
        notifyGuestOfDecision(saved, false);
        return saved;
    }

    @Override
    public void delete(Long id) {
        repository.delete(findOrThrow(id));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsById(Long id) {
        return id != null && repository.existsById(id);
    }

    private Reservation findOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Reservation not found with id " + id));
    }

    // ---------------- notifications (best-effort side effects) ----------------

    /**
     * A new request needs a human decision, so tell the property's manager. Falls
     * back to the owner when no manager is assigned — otherwise a request on an
     * unmanaged property would sit unseen. Only PENDING requests are announced;
     * a reservation created already-CONFIRMED (e.g. by an admin) has nothing to
     * approve.
     */
    private void notifyManagerOfRequest(ReservationResponse reservation) {
        if (reservation.status() != ReservationStatus.PENDING) {
            return;
        }
        propertyClient.findById(reservation.propertyId()).ifPresent(property -> {
            Long recipient = property.managerId() != null ? property.managerId() : property.ownerId();
            notificationClient.notifyUser(
                    recipient,
                    "New booking request for " + property.describe() + ": "
                            + guestName(reservation.guestId()) + ", "
                            + reservation.checkInDate() + " to " + reservation.checkOutDate()
                            + " (" + nights(reservation) + ", " + guests(reservation) + ")"
                            + ", total " + RUPEE + reservation.totalAmount()
                            + ". Approve or reject it from your Reservations screen.",
                    CATEGORY_BOOKING);
        });
    }

    /**
     * Tell the guest how their request went. On approval the message carries the
     * property details and everything they need for the stay (dates, nights,
     * guests, total, and the property's check-in/check-out times when set), so the
     * notification alone confirms the booking.
     */
    private void notifyGuestOfDecision(ReservationResponse reservation, boolean approved) {
        Long guestUserId = guestUserId(reservation.guestId());
        if (guestUserId == null) {
            return;
        }
        String property = propertyClient.findById(reservation.propertyId())
                .map(PropertyClient.PropertySummary::describe)
                .orElse("the property");

        if (!approved) {
            notificationClient.notifyUser(
                    guestUserId,
                    "Your booking request for " + property + " (" + reservation.checkInDate()
                            + " to " + reservation.checkOutDate() + ") was declined. "
                            + "Those dates are still free to book elsewhere.",
                    CATEGORY_BOOKING);
            return;
        }

        String times = propertyClient.findById(reservation.propertyId())
                .map(p -> {
                    if (p.checkInTime() == null && p.checkOutTime() == null) {
                        return "";
                    }
                    return " Check-in from " + shortTime(p.checkInTime())
                            + ", check-out by " + shortTime(p.checkOutTime()) + ".";
                })
                .orElse("");

        notificationClient.notifyUser(
                guestUserId,
                "Booking confirmed! " + property + " is yours from " + reservation.checkInDate()
                        + " to " + reservation.checkOutDate()
                        + " (" + nights(reservation) + ", " + guests(reservation) + ")"
                        + ", total " + RUPEE + reservation.totalAmount() + "." + times,
                CATEGORY_BOOKING);
    }

    private String nights(ReservationResponse reservation) {
        int n = reservation.nights();
        return n + (n == 1 ? " night" : " nights");
    }

    private String guests(ReservationResponse reservation) {
        int g = reservation.guestCount();
        return g + (g == 1 ? " guest" : " guests");
    }

    /** "14:00:00" → "14:00"; absent times read as "the property's usual time". */
    private String shortTime(String time) {
        if (time == null || time.length() < 5) {
            return "the usual time";
        }
        return time.substring(0, 5);
    }

    /** The guest's own login id, so the notification lands in THEIR inbox. */
    private Long guestUserId(Long guestId) {
        try {
            return guestProfileService.getById(guestId).userId();
        } catch (RuntimeException ex) {
            return null; // guest profile vanished — nothing to notify
        }
    }

    private String guestName(Long guestId) {
        try {
            return guestProfileService.getById(guestId).name();
        } catch (RuntimeException ex) {
            return "guest #" + guestId;
        }
    }

    /** All the create/update guards in one place. */
    private void validateReferencesAndDates(ReservationRequest request) {
        if (!propertyClient.existsById(request.propertyId())) {
            throw new ResourceNotFoundException("Property not found with id " + request.propertyId());
        }
        if (!guestProfileService.existsById(request.guestId())) {
            throw new ResourceNotFoundException("Guest profile not found with id " + request.guestId());
        }
        // checkOutDate must be strictly after checkInDate (so nights >= 1).
        if (!request.checkOutDate().isAfter(request.checkInDate())) {
            throw new IllegalArgumentException("checkOutDate must be after checkInDate");
        }
    }
}
