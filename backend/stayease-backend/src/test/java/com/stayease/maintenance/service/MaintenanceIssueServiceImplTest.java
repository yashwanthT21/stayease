package com.stayease.maintenance.service;

import com.stayease.common.client.PropertyClient;
import com.stayease.common.exception.ResourceNotFoundException;
import com.stayease.iam.service.UserService;
import com.stayease.maintenance.dto.MaintenanceIssueRequest;
import com.stayease.maintenance.dto.MaintenanceIssueResponse;
import com.stayease.maintenance.entity.MaintenanceIssue;
import com.stayease.maintenance.enums.MaintenanceCategory;
import com.stayease.maintenance.enums.MaintenancePriority;
import com.stayease.maintenance.enums.MaintenanceStatus;
import com.stayease.maintenance.enums.ReportedByType;
import com.stayease.maintenance.repository.MaintenanceIssueRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the maintenance module's issue service.
 *
 * An issue points at two things that live elsewhere: a property (in
 * property-service, reached over HTTP) and the user who reported it (in IAM).
 * Both are mocked here, and the tests prove the service refuses to log an issue
 * against either one being missing — that's what keeps orphaned rows out of the
 * owner's maintenance-cost totals.
 */
@ExtendWith(MockitoExtension.class)
class MaintenanceIssueServiceImplTest {

    private static final Long PROPERTY_ID = 3L;
    private static final Long REPORTER_ID = 7L;

    @Mock
    private MaintenanceIssueRepository repository;

    @Mock
    private PropertyClient propertyClient;

    @Mock
    private UserService userService;

    @InjectMocks
    private MaintenanceIssueServiceImpl service;

    private MaintenanceIssueRequest request(MaintenancePriority priority, MaintenanceStatus status) {
        return new MaintenanceIssueRequest(PROPERTY_ID, REPORTER_ID, ReportedByType.GUEST,
                MaintenanceCategory.PLUMBING, "Kitchen tap is dripping",
                priority, null, null, null, status);
    }

    private MaintenanceIssue entity(Long id, MaintenanceStatus status) {
        MaintenanceIssue issue = new MaintenanceIssue();
        issue.setId(id);
        issue.setPropertyId(PROPERTY_ID);
        issue.setReportedById(REPORTER_ID);
        issue.setReportedByType(ReportedByType.GUEST);
        issue.setCategory(MaintenanceCategory.PLUMBING);
        issue.setPriority(MaintenancePriority.MEDIUM);
        issue.setStatus(status);
        return issue;
    }

    @Test
    @DisplayName("create: a new issue defaults to MEDIUM priority and OPEN status")
    void createAppliesDefaults() {
        when(propertyClient.existsById(PROPERTY_ID)).thenReturn(true);
        when(userService.existsById(REPORTER_ID)).thenReturn(true);
        when(repository.save(any(MaintenanceIssue.class))).thenAnswer(call -> call.getArgument(0));

        MaintenanceIssueResponse created = service.create(request(null, null));

        assertThat(created.priority()).isEqualTo(MaintenancePriority.MEDIUM);
        assertThat(created.status()).isEqualTo(MaintenanceStatus.OPEN);
        // The report time is stamped by the server, never sent by the client.
        assertThat(created.reportedDate()).isNotNull();
    }

    @Test
    @DisplayName("create: an emergency stays an emergency")
    void createKeepsSuppliedPriority() {
        when(propertyClient.existsById(PROPERTY_ID)).thenReturn(true);
        when(userService.existsById(REPORTER_ID)).thenReturn(true);
        when(repository.save(any(MaintenanceIssue.class))).thenAnswer(call -> call.getArgument(0));

        MaintenanceIssueResponse created =
                service.create(request(MaintenancePriority.EMERGENCY, MaintenanceStatus.ASSIGNED));

        assertThat(created.priority()).isEqualTo(MaintenancePriority.EMERGENCY);
        assertThat(created.status()).isEqualTo(MaintenanceStatus.ASSIGNED);
    }

    @Test
    @DisplayName("create: refuses an issue against a property that doesn't exist")
    void createRequiresARealProperty() {
        when(propertyClient.existsById(PROPERTY_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.create(request(null, null)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Property not found");

        verify(repository, never()).save(any(MaintenanceIssue.class));
    }

    @Test
    @DisplayName("create: refuses an issue reported by a user that doesn't exist")
    void createRequiresARealReporter() {
        when(propertyClient.existsById(PROPERTY_ID)).thenReturn(true);
        when(userService.existsById(REPORTER_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.create(request(null, null)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Reporter");

        verify(repository, never()).save(any(MaintenanceIssue.class));
    }

    @Test
    @DisplayName("getAll: a propertyId filter queries by property")
    void getAllFiltersByProperty() {
        when(repository.findByPropertyId(PROPERTY_ID))
                .thenReturn(List.of(entity(1L, MaintenanceStatus.OPEN)));

        List<MaintenanceIssueResponse> issues = service.getAll(PROPERTY_ID, null);

        assertThat(issues).hasSize(1);
        verify(repository, never()).findAll();
    }

    @Test
    @DisplayName("getAll: a status filter on its own queries by status")
    void getAllFiltersByStatus() {
        when(repository.findByStatus(MaintenanceStatus.RESOLVED))
                .thenReturn(List.of(entity(2L, MaintenanceStatus.RESOLVED)));

        List<MaintenanceIssueResponse> issues = service.getAll(null, MaintenanceStatus.RESOLVED);

        assertThat(issues).hasSize(1);
        assertThat(issues.get(0).status()).isEqualTo(MaintenanceStatus.RESOLVED);
    }

    @Test
    @DisplayName("update: recording the repair cost keeps it on the issue")
    void updateStoresAmountSpent() {
        when(repository.findById(1L)).thenReturn(Optional.of(entity(1L, MaintenanceStatus.OPEN)));
        when(propertyClient.existsById(PROPERTY_ID)).thenReturn(true);
        when(userService.existsById(REPORTER_ID)).thenReturn(true);
        when(repository.save(any(MaintenanceIssue.class))).thenAnswer(call -> call.getArgument(0));

        MaintenanceIssueRequest resolved = new MaintenanceIssueRequest(
                PROPERTY_ID, REPORTER_ID, ReportedByType.GUEST, MaintenanceCategory.PLUMBING,
                "Kitchen tap is dripping", MaintenancePriority.MEDIUM, null, null,
                new BigDecimal("1250.00"), MaintenanceStatus.RESOLVED);

        MaintenanceIssueResponse updated = service.update(1L, resolved);

        assertThat(updated.amountSpent()).isEqualByComparingTo("1250.00");
        assertThat(updated.status()).isEqualTo(MaintenanceStatus.RESOLVED);
    }

    @Test
    @DisplayName("getById: unknown id gives a not-found error")
    void getByIdThrowsWhenMissing() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
