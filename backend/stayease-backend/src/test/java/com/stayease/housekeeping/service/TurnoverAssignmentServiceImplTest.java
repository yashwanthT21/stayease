package com.stayease.housekeeping.service;

import com.stayease.booking.service.ReservationService;
import com.stayease.common.client.NotificationClient;
import com.stayease.common.client.PropertyClient;
import com.stayease.housekeeping.dto.TurnoverAssignmentRequest;
import com.stayease.housekeeping.entity.TurnoverAssignment;
import com.stayease.housekeeping.enums.HousekeeperStatus;
import com.stayease.housekeeping.enums.TurnoverStatus;
import com.stayease.housekeeping.repository.TurnoverAssignmentRepository;
import com.stayease.iam.dto.UserResponse;
import com.stayease.iam.enums.UserRole;
import com.stayease.iam.enums.UserStatus;
import com.stayease.iam.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for turnover assignments, focused on the manager ⇄ housekeeper
 * conversation.
 *
 * The two of them never talk directly, so the notifications ARE the handover:
 * a housekeeper who isn't told has no reason to look at the turnover list, and a
 * manager who isn't told can't verify work they don't know is finished. The rules
 * worth pinning down are the ones that keep that channel useful — notify on a real
 * change of assignee, and only on the transition INTO completed, so the inbox
 * doesn't fill with repeats of things the recipient already acted on.
 */
@ExtendWith(MockitoExtension.class)
class TurnoverAssignmentServiceImplTest {

    private static final Long PROPERTY_ID = 3L;
    private static final Long HOUSEKEEPER_ID = 40L;
    private static final Long MANAGER_ID = 7L;
    private static final Long OWNER_ID = 5L;
    private static final String HOUSEKEEPING = "HOUSEKEEPING";

    @Mock
    private TurnoverAssignmentRepository repository;

    @Mock
    private PropertyClient propertyClient;

    @Mock
    private UserService userService;

    @Mock
    private ReservationService reservationService;

    @Mock
    private NotificationClient notificationClient;

    @InjectMocks
    private TurnoverAssignmentServiceImpl service;

    private TurnoverAssignmentRequest request(Long assignedToId) {
        return new TurnoverAssignmentRequest(PROPERTY_ID, null, null, assignedToId,
                LocalDate.of(2027, 1, 10), null, null, null);
    }

    private TurnoverAssignment entity(Long id, Long assignedToId, HousekeeperStatus housekeeperStatus) {
        TurnoverAssignment turnover = new TurnoverAssignment();
        turnover.setId(id);
        turnover.setPropertyId(PROPERTY_ID);
        turnover.setAssignedToId(assignedToId);
        turnover.setAssignedDate(LocalDate.of(2027, 1, 10));
        turnover.setStatus(TurnoverStatus.PENDING);
        turnover.setHousekeeperStatus(housekeeperStatus);
        return turnover;
    }

    /** A property with a manager, which is who a completion message should reach. */
    private PropertyClient.PropertySummary property(Long managerId) {
        return new PropertyClient.PropertySummary(
                PROPERTY_ID, OWNER_ID, managerId, "Sea Breeze Villa", "Kochi", null, null);
    }

    private UserResponse housekeeper() {
        return new UserResponse(HOUSEKEEPER_ID, "Meera Clean", "meera@example.com",
                "555-0111", UserRole.HOUSEKEEPING, UserStatus.ACTIVE);
    }

    @Test
    @DisplayName("create: assigning a turnover tells the housekeeper, naming the property")
    void createNotifiesTheAssignedHousekeeper() {
        when(propertyClient.existsById(PROPERTY_ID)).thenReturn(true);
        when(userService.existsById(HOUSEKEEPER_ID)).thenReturn(true);
        when(repository.save(any(TurnoverAssignment.class))).thenAnswer(call -> call.getArgument(0));
        when(propertyClient.findById(PROPERTY_ID)).thenReturn(Optional.of(property(MANAGER_ID)));

        service.create(request(HOUSEKEEPER_ID));

        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(notificationClient).notifyUser(eq(HOUSEKEEPER_ID), message.capture(), eq(HOUSEKEEPING));
        assertThat(message.getValue())
                .contains("Sea Breeze Villa (Kochi)")
                .contains("2027-01-10");
    }

    @Test
    @DisplayName("create: an unassigned turnover has nobody to notify")
    void createWithoutAnAssigneeNotifiesNobody() {
        when(propertyClient.existsById(PROPERTY_ID)).thenReturn(true);
        when(repository.save(any(TurnoverAssignment.class))).thenAnswer(call -> call.getArgument(0));

        service.create(request(null));

        verify(notificationClient, never()).notifyUser(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("update: moving the job to a different housekeeper tells the new one")
    void updateNotifiesAReassignedHousekeeper() {
        when(repository.findById(1L)).thenReturn(Optional.of(entity(1L, 99L, HousekeeperStatus.PENDING)));
        when(propertyClient.existsById(PROPERTY_ID)).thenReturn(true);
        when(userService.existsById(HOUSEKEEPER_ID)).thenReturn(true);
        when(repository.save(any(TurnoverAssignment.class))).thenAnswer(call -> call.getArgument(0));
        when(propertyClient.findById(PROPERTY_ID)).thenReturn(Optional.of(property(MANAGER_ID)));

        service.update(1L, request(HOUSEKEEPER_ID));

        verify(notificationClient).notifyUser(eq(HOUSEKEEPER_ID), anyString(), eq(HOUSEKEEPING));
    }

    @Test
    @DisplayName("update: re-saving a turnover with the SAME housekeeper doesn't nag them")
    void updateDoesNotRenotifyTheSameHousekeeper() {
        when(repository.findById(1L))
                .thenReturn(Optional.of(entity(1L, HOUSEKEEPER_ID, HousekeeperStatus.PENDING)));
        when(propertyClient.existsById(PROPERTY_ID)).thenReturn(true);
        when(userService.existsById(HOUSEKEEPER_ID)).thenReturn(true);
        when(repository.save(any(TurnoverAssignment.class))).thenAnswer(call -> call.getArgument(0));

        service.update(1L, request(HOUSEKEEPER_ID));

        verify(notificationClient, never()).notifyUser(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("housekeeper-status COMPLETED: the property's manager is told who finished what")
    void completingTheWorkNotifiesTheManager() {
        when(repository.findById(1L))
                .thenReturn(Optional.of(entity(1L, HOUSEKEEPER_ID, HousekeeperStatus.PENDING)));
        when(repository.save(any(TurnoverAssignment.class))).thenAnswer(call -> call.getArgument(0));
        when(propertyClient.findById(PROPERTY_ID)).thenReturn(Optional.of(property(MANAGER_ID)));
        when(userService.getById(HOUSEKEEPER_ID)).thenReturn(housekeeper());

        service.setHousekeeperStatus(1L, HousekeeperStatus.COMPLETED);

        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(notificationClient).notifyUser(eq(MANAGER_ID), message.capture(), eq(HOUSEKEEPING));
        assertThat(message.getValue())
                .contains("Meera Clean")
                .contains("Sea Breeze Villa (Kochi)");
    }

    @Test
    @DisplayName("housekeeper-status COMPLETED: with no manager assigned, the owner is told instead")
    void completingTheWorkFallsBackToTheOwner() {
        when(repository.findById(1L))
                .thenReturn(Optional.of(entity(1L, HOUSEKEEPER_ID, HousekeeperStatus.PENDING)));
        when(repository.save(any(TurnoverAssignment.class))).thenAnswer(call -> call.getArgument(0));
        when(propertyClient.findById(PROPERTY_ID)).thenReturn(Optional.of(property(null)));
        when(userService.getById(HOUSEKEEPER_ID)).thenReturn(housekeeper());

        service.setHousekeeperStatus(1L, HousekeeperStatus.COMPLETED);

        verify(notificationClient).notifyUser(eq(OWNER_ID), anyString(), eq(HOUSEKEEPING));
    }

    @Test
    @DisplayName("housekeeper-status COMPLETED twice: the manager is told once, not on every re-save")
    void recompletingDoesNotNotifyAgain() {
        when(repository.findById(1L))
                .thenReturn(Optional.of(entity(1L, HOUSEKEEPER_ID, HousekeeperStatus.COMPLETED)));
        when(repository.save(any(TurnoverAssignment.class))).thenAnswer(call -> call.getArgument(0));

        service.setHousekeeperStatus(1L, HousekeeperStatus.COMPLETED);

        verify(notificationClient, never()).notifyUser(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("housekeeper-status PENDING: reopening the job is not a completion")
    void revertingToPendingNotifiesNobody() {
        when(repository.findById(1L))
                .thenReturn(Optional.of(entity(1L, HOUSEKEEPER_ID, HousekeeperStatus.COMPLETED)));
        when(repository.save(any(TurnoverAssignment.class))).thenAnswer(call -> call.getArgument(0));

        service.setHousekeeperStatus(1L, HousekeeperStatus.PENDING);

        verify(notificationClient, never()).notifyUser(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("manager-status: setting the overall status is silent — nobody is waiting on it")
    void managerStatusSendsNothing() {
        when(repository.findById(1L))
                .thenReturn(Optional.of(entity(1L, HOUSEKEEPER_ID, HousekeeperStatus.COMPLETED)));
        when(repository.save(any(TurnoverAssignment.class))).thenAnswer(call -> call.getArgument(0));

        service.setManagerStatus(1L, TurnoverStatus.COMPLETED);

        verify(notificationClient, never()).notifyUser(anyLong(), anyString(), anyString());
    }
}
