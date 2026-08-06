package com.stayease.housekeeping.service;

import com.stayease.booking.service.ReservationService;
import com.stayease.common.client.NotificationClient;
import com.stayease.common.client.PropertyClient;
import com.stayease.common.exception.ResourceNotFoundException;
import com.stayease.housekeeping.dto.TurnoverAssignmentRequest;
import com.stayease.housekeeping.dto.TurnoverAssignmentResponse;
import com.stayease.housekeeping.entity.TurnoverAssignment;
import com.stayease.housekeeping.enums.HousekeeperStatus;
import com.stayease.housekeeping.enums.TurnoverStatus;
import com.stayease.housekeeping.mapper.TurnoverAssignmentMapper;
import com.stayease.housekeeping.repository.TurnoverAssignmentRepository;
import com.stayease.iam.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * Turnover assignments tie together a property, the two reservations either
 * side of the cleaning, and the housekeeping user doing the work — so this
 * service validates all of the ids that were supplied.
 *
 * It also closes the loop between the manager and the housekeeper, who never
 * speak directly:
 *   manager assigns a turnover  → the HOUSEKEEPER is told there's work for them,
 *                                 with the property and the deadlines;
 *   housekeeper marks it done   → the property's MANAGER is told, because only
 *                                 they can verify the work and set the overall
 *                                 status (see {@link #setManagerStatus}).
 * Nothing is sent for the checklist items a housekeeper ticks off while working
 * (see TurnoverChecklistServiceImpl) — those are progress notes on one job, and a
 * notification per item would bury the completion message that actually needs
 * acting on. The single "work finished" message is the signal.
 *
 * All of it is best-effort (see NotificationClient): an assignment is still saved
 * when notification-service is down.
 */
@Service
@Transactional
public class TurnoverAssignmentServiceImpl implements TurnoverAssignmentService {

    private static final String CATEGORY_HOUSEKEEPING = "HOUSEKEEPING";

    private final TurnoverAssignmentRepository repository;
    private final PropertyClient propertyClient;
    private final UserService userService;
    private final ReservationService reservationService;
    private final NotificationClient notificationClient;

    public TurnoverAssignmentServiceImpl(TurnoverAssignmentRepository repository,
                                         PropertyClient propertyClient,
                                         UserService userService,
                                         ReservationService reservationService,
                                         NotificationClient notificationClient) {
        this.repository = repository;
        this.propertyClient = propertyClient;
        this.userService = userService;
        this.reservationService = reservationService;
        this.notificationClient = notificationClient;
    }

    @Override
    public TurnoverAssignmentResponse create(TurnoverAssignmentRequest request) {
        validateReferences(request);
        TurnoverAssignment saved = repository.save(TurnoverAssignmentMapper.toEntity(request));
        notifyHousekeeperIfNewlyAssigned(saved, null);
        return TurnoverAssignmentMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TurnoverAssignmentResponse> getAll(Long propertyId, Long assignedToId) {
        List<TurnoverAssignment> list;
        if (propertyId != null) {
            list = repository.findByPropertyId(propertyId);
        } else if (assignedToId != null) {
            list = repository.findByAssignedToId(assignedToId);
        } else {
            list = repository.findAll();
        }
        return list.stream().map(TurnoverAssignmentMapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public TurnoverAssignmentResponse getById(Long id) {
        return TurnoverAssignmentMapper.toResponse(findOrThrow(id));
    }

    @Override
    public TurnoverAssignmentResponse update(Long id, TurnoverAssignmentRequest request) {
        TurnoverAssignment entity = findOrThrow(id);
        validateReferences(request);
        // Remember who held the job BEFORE the mapper overwrites it, so a genuine
        // reassignment can be told apart from an edit to the schedule.
        Long previousAssigneeId = entity.getAssignedToId();
        TurnoverAssignmentMapper.updateEntity(entity, request);
        TurnoverAssignment saved = repository.save(entity);
        notifyHousekeeperIfNewlyAssigned(saved, previousAssigneeId);
        return TurnoverAssignmentMapper.toResponse(saved);
    }

    @Override
    public TurnoverAssignmentResponse setHousekeeperStatus(Long id, HousekeeperStatus status) {
        TurnoverAssignment entity = findOrThrow(id);
        HousekeeperStatus previous = entity.getHousekeeperStatus();
        entity.setHousekeeperStatus(status);
        TurnoverAssignment saved = repository.save(entity);
        // Only the transition INTO completed is news; re-saving COMPLETED (or
        // going back to PENDING) must not notify the manager again.
        if (status == HousekeeperStatus.COMPLETED && previous != HousekeeperStatus.COMPLETED) {
            notifyManagerOfCompletion(saved);
        }
        return TurnoverAssignmentMapper.toResponse(saved);
    }

    @Override
    public TurnoverAssignmentResponse setManagerStatus(Long id, TurnoverStatus status) {
        TurnoverAssignment entity = findOrThrow(id);
        // The manager may only set the overall status after the housekeeper has
        // finished (verify-after-work-done rule).
        if (entity.getHousekeeperStatus() != HousekeeperStatus.COMPLETED) {
            throw new IllegalArgumentException(
                    "The housekeeper must mark their work Completed before the manager status can be set");
        }
        entity.setStatus(status);
        return TurnoverAssignmentMapper.toResponse(repository.save(entity));
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

    private TurnoverAssignment findOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Turnover assignment not found with id " + id));
    }

    // ---------------- notifications (best-effort side effects) ----------------

    /**
     * Tell the housekeeper they have a turnover to do. This is the whole point of
     * the manager→housekeeper direction: the housekeeper doesn't watch the
     * turnover list, so without this they'd only find out by chance.
     *
     * Sent when the job lands on someone new — on create, and on an update that
     * moves it to a different housekeeper. An unassigned turnover has nobody to
     * tell, and an update that only shifts the deadlines leaves the assignee
     * unchanged, so it stays quiet.
     */
    private void notifyHousekeeperIfNewlyAssigned(TurnoverAssignment turnover, Long previousAssigneeId) {
        Long assigneeId = turnover.getAssignedToId();
        if (assigneeId == null || Objects.equals(assigneeId, previousAssigneeId)) {
            return;
        }
        notificationClient.notifyUser(
                assigneeId,
                "New turnover assigned to you: " + describeProperty(turnover.getPropertyId())
                        + scheduled(turnover) + deadlines(turnover)
                        + " Mark it Completed once the clean is done.",
                CATEGORY_HOUSEKEEPING);
    }

    /**
     * Tell the property's manager the clean is finished, because the ball is now
     * in their court: only they can verify it and set the turnover's overall
     * status. Falls back to the owner when the property has no manager, so a
     * finished clean is never announced to nobody.
     */
    private void notifyManagerOfCompletion(TurnoverAssignment turnover) {
        propertyClient.findById(turnover.getPropertyId()).ifPresent(property -> {
            Long recipient = property.managerId() != null ? property.managerId() : property.ownerId();
            notificationClient.notifyUser(
                    recipient,
                    housekeeperName(turnover.getAssignedToId()) + " has completed the turnover for "
                            + property.describe() + scheduled(turnover)
                            + " Verify the work and set the turnover status.",
                    CATEGORY_HOUSEKEEPING);
        });
    }

    /** The property named for a human, or "property #7" when it can't be fetched. */
    private String describeProperty(Long propertyId) {
        return propertyClient.findById(propertyId)
                .map(PropertyClient.PropertySummary::describe)
                .orElse("property #" + propertyId);
    }

    /** " on 2027-01-10." — or just "." when no date was scheduled. */
    private String scheduled(TurnoverAssignment turnover) {
        return turnover.getAssignedDate() == null ? "." : " on " + turnover.getAssignedDate() + ".";
    }

    /** " Start by 14:00, complete by 16:00." — omitted when neither was set. */
    private String deadlines(TurnoverAssignment turnover) {
        if (turnover.getStartByTime() == null && turnover.getCompleteByTime() == null) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        if (turnover.getStartByTime() != null) {
            text.append(" Start by ").append(turnover.getStartByTime().toLocalTime()).append('.');
        }
        if (turnover.getCompleteByTime() != null) {
            text.append(" Complete by ").append(turnover.getCompleteByTime().toLocalTime()).append('.');
        }
        return text.toString();
    }

    /** The housekeeper by name, so the manager knows who to follow up with. */
    private String housekeeperName(Long userId) {
        if (userId == null) {
            return "The housekeeper";
        }
        try {
            return userService.getById(userId).name();
        } catch (RuntimeException ex) {
            return "Housekeeper #" + userId;
        }
    }

    private void validateReferences(TurnoverAssignmentRequest request) {
        if (!propertyClient.existsById(request.propertyId())) {
            throw new ResourceNotFoundException("Property not found with id " + request.propertyId());
        }
        if (request.assignedToId() != null && !userService.existsById(request.assignedToId())) {
            throw new ResourceNotFoundException("Assignee (user) not found with id " + request.assignedToId());
        }
        if (request.checkOutReservationId() != null
                && !reservationService.existsById(request.checkOutReservationId())) {
            throw new ResourceNotFoundException(
                    "Check-out reservation not found with id " + request.checkOutReservationId());
        }
        if (request.checkInReservationId() != null
                && !reservationService.existsById(request.checkInReservationId())) {
            throw new ResourceNotFoundException(
                    "Check-in reservation not found with id " + request.checkInReservationId());
        }
    }
}
