package com.stayease.housekeeping.service;

import com.stayease.common.exception.ResourceNotFoundException;
import com.stayease.housekeeping.dto.TurnoverChecklistRequest;
import com.stayease.housekeeping.dto.TurnoverChecklistResponse;
import com.stayease.housekeeping.entity.TurnoverChecklist;
import com.stayease.housekeeping.enums.ChecklistCategory;
import com.stayease.housekeeping.enums.ChecklistStatus;
import com.stayease.housekeeping.repository.TurnoverChecklistRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the housekeeping module's checklist service.
 *
 * A checklist item only makes sense as part of a turnover, so the key rule is that
 * the turnover must exist before an item can be attached to it. The rest is about
 * sensible defaults for a housekeeper adding tasks on their phone.
 */
@ExtendWith(MockitoExtension.class)
class TurnoverChecklistServiceImplTest {

    private static final Long TURNOVER_ID = 12L;

    @Mock
    private TurnoverChecklistRepository repository;

    @Mock
    private TurnoverAssignmentService turnoverAssignmentService;

    @InjectMocks
    private TurnoverChecklistServiceImpl service;

    private TurnoverChecklistRequest request(Boolean completed, ChecklistStatus status) {
        return new TurnoverChecklistRequest(TURNOVER_ID, "Strip and remake all beds",
                ChecklistCategory.LAUNDRY, completed, "Spare linen is in the hall cupboard", status);
    }

    private TurnoverChecklist entity(Long id, boolean completed) {
        TurnoverChecklist item = new TurnoverChecklist();
        item.setId(id);
        item.setTurnoverId(TURNOVER_ID);
        item.setTaskName("Strip and remake all beds");
        item.setCategory(ChecklistCategory.LAUNDRY);
        item.setCompleted(completed);
        item.setStatus(completed ? ChecklistStatus.DONE : ChecklistStatus.PENDING);
        return item;
    }

    @Test
    @DisplayName("create: a new task starts PENDING and not completed")
    void createAppliesDefaults() {
        when(turnoverAssignmentService.existsById(TURNOVER_ID)).thenReturn(true);
        when(repository.save(any(TurnoverChecklist.class))).thenAnswer(call -> call.getArgument(0));

        TurnoverChecklistResponse created = service.create(request(null, null));

        assertThat(created.status()).isEqualTo(ChecklistStatus.PENDING);
        assertThat(created.completed()).isFalse();
        assertThat(created.turnoverId()).isEqualTo(TURNOVER_ID);
    }

    @Test
    @DisplayName("create: a task added as already-done keeps that state")
    void createKeepsSuppliedState() {
        when(turnoverAssignmentService.existsById(TURNOVER_ID)).thenReturn(true);
        when(repository.save(any(TurnoverChecklist.class))).thenAnswer(call -> call.getArgument(0));

        TurnoverChecklistResponse created = service.create(request(true, ChecklistStatus.DONE));

        assertThat(created.completed()).isTrue();
        assertThat(created.status()).isEqualTo(ChecklistStatus.DONE);
    }

    @Test
    @DisplayName("create: refuses a task for a turnover that doesn't exist")
    void createRequiresARealTurnover() {
        when(turnoverAssignmentService.existsById(TURNOVER_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.create(request(null, null)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Turnover assignment not found");

        verify(repository, never()).save(any(TurnoverChecklist.class));
    }

    @Test
    @DisplayName("getByTurnover: returns only that turnover's items")
    void getByTurnoverReturnsItsItems() {
        when(turnoverAssignmentService.existsById(TURNOVER_ID)).thenReturn(true);
        when(repository.findByTurnoverId(TURNOVER_ID))
                .thenReturn(List.of(entity(1L, false), entity(2L, true)));

        List<TurnoverChecklistResponse> items = service.getByTurnover(TURNOVER_ID);

        assertThat(items).hasSize(2);
        assertThat(items).allMatch(item -> item.turnoverId().equals(TURNOVER_ID));
    }

    @Test
    @DisplayName("getByTurnover: an unknown turnover is a not-found error, not an empty list")
    void getByTurnoverThrowsForUnknownTurnover() {
        when(turnoverAssignmentService.existsById(999L)).thenReturn(false);

        assertThatThrownBy(() -> service.getByTurnover(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("update: ticking a task off saves the new state")
    void updateMarksTaskComplete() {
        when(repository.findById(1L)).thenReturn(Optional.of(entity(1L, false)));
        when(turnoverAssignmentService.existsById(TURNOVER_ID)).thenReturn(true);
        when(repository.save(any(TurnoverChecklist.class))).thenAnswer(call -> call.getArgument(0));

        TurnoverChecklistResponse updated = service.update(1L, request(true, ChecklistStatus.DONE));

        assertThat(updated.completed()).isTrue();
        assertThat(updated.status()).isEqualTo(ChecklistStatus.DONE);
    }

    @Test
    @DisplayName("getById: unknown id gives a not-found error")
    void getByIdThrowsWhenMissing() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
