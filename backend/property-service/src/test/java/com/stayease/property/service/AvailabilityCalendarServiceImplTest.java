package com.stayease.property.service;

import com.stayease.common.exception.DuplicateResourceException;
import com.stayease.common.exception.ResourceNotFoundException;
import com.stayease.property.dto.AvailabilityCalendarRequest;
import com.stayease.property.dto.AvailabilityCalendarResponse;
import com.stayease.property.entity.AvailabilityCalendar;
import com.stayease.property.enums.AvailabilityStatus;
import com.stayease.property.repository.AvailabilityCalendarRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for property-service's availability logic.
 *
 * The headline rule is the past-date lock: an owner or manager may set price and
 * status from TODAY forward, never backwards, and because that same endpoint is
 * what a booking approval uses to hold nights, enforcing it here is what actually
 * stops a guest booking a night that has already gone. The calendar UI greys those
 * days out too, but a disabled button is a courtesy — this is the guarantee.
 *
 * The other rule is one row per (property, date), so a day can't end up with two
 * conflicting prices.
 */
@ExtendWith(MockitoExtension.class)
class AvailabilityCalendarServiceImplTest {

    private static final Long PROPERTY_ID = 3L;

    @Mock
    private AvailabilityCalendarRepository repository;

    @Mock
    private PropertyService propertyService;

    @InjectMocks
    private AvailabilityCalendarServiceImpl service;

    private AvailabilityCalendarRequest request(LocalDate date, AvailabilityStatus status) {
        return new AvailabilityCalendarRequest(PROPERTY_ID, date, status, new BigDecimal("4500.00"), 1);
    }

    private AvailabilityCalendar entity(Long id, LocalDate date) {
        AvailabilityCalendar row = new AvailabilityCalendar();
        row.setId(id);
        row.setPropertyId(PROPERTY_ID);
        row.setCalendarDate(date);
        row.setAvailabilityStatus(AvailabilityStatus.AVAILABLE);
        row.setBasePrice(new BigDecimal("4500.00"));
        row.setMinimumNights(1);
        return row;
    }

    // ---------------- the past-date lock ----------------

    @Test
    @DisplayName("create: a date before today is rejected (this is what stops past-day bookings)")
    void createRejectsAPastDate() {
        when(propertyService.existsById(PROPERTY_ID)).thenReturn(true);
        LocalDate yesterday = LocalDate.now().minusDays(1);

        assertThatThrownBy(() -> service.create(request(yesterday, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("in the past");

        verify(repository, never()).save(any(AvailabilityCalendar.class));
    }

    @Test
    @DisplayName("create: today is still editable — the cut-off is 'before today', not 'after today'")
    void createAllowsToday() {
        when(propertyService.existsById(PROPERTY_ID)).thenReturn(true);
        when(repository.findByPropertyIdAndCalendarDate(any(), any())).thenReturn(Optional.empty());
        when(repository.save(any(AvailabilityCalendar.class))).thenAnswer(call -> call.getArgument(0));

        AvailabilityCalendarResponse created = service.create(request(LocalDate.now(), null));

        assertThat(created.calendarDate()).isEqualTo(LocalDate.now());
    }

    @Test
    @DisplayName("create: a future date is fine")
    void createAllowsAFutureDate() {
        when(propertyService.existsById(PROPERTY_ID)).thenReturn(true);
        when(repository.findByPropertyIdAndCalendarDate(any(), any())).thenReturn(Optional.empty());
        when(repository.save(any(AvailabilityCalendar.class))).thenAnswer(call -> call.getArgument(0));

        LocalDate nextMonth = LocalDate.now().plusMonths(1);
        AvailabilityCalendarResponse created = service.create(request(nextMonth, AvailabilityStatus.BLOCKED));

        assertThat(created.calendarDate()).isEqualTo(nextMonth);
        assertThat(created.availabilityStatus()).isEqualTo(AvailabilityStatus.BLOCKED);
    }

    @Test
    @DisplayName("update: a day that has already passed can't be rewritten")
    void updateRejectsEditingAPastDay() {
        LocalDate lastWeek = LocalDate.now().minusDays(7);
        when(repository.findById(1L)).thenReturn(Optional.of(entity(1L, lastWeek)));

        assertThatThrownBy(() -> service.update(1L, request(lastWeek, AvailabilityStatus.AVAILABLE)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("in the past");

        verify(repository, never()).save(any(AvailabilityCalendar.class));
    }

    @Test
    @DisplayName("update: a future day can't be dragged back into the past either")
    void updateRejectsMovingADayIntoThePast() {
        LocalDate nextWeek = LocalDate.now().plusDays(7);
        when(repository.findById(1L)).thenReturn(Optional.of(entity(1L, nextWeek)));

        assertThatThrownBy(() -> service.update(1L, request(LocalDate.now().minusDays(2), null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("in the past");

        verify(repository, never()).save(any(AvailabilityCalendar.class));
    }

    @Test
    @DisplayName("update: a future day updates normally (e.g. a booking marking it BOOKED)")
    void updateAllowsAFutureDay() {
        LocalDate nextWeek = LocalDate.now().plusDays(7);
        when(repository.findById(1L)).thenReturn(Optional.of(entity(1L, nextWeek)));
        when(repository.findByPropertyIdAndCalendarDate(PROPERTY_ID, nextWeek)).thenReturn(Optional.empty());
        when(repository.save(any(AvailabilityCalendar.class))).thenAnswer(call -> call.getArgument(0));

        AvailabilityCalendarResponse updated =
                service.update(1L, request(nextWeek, AvailabilityStatus.BOOKED));

        assertThat(updated.availabilityStatus()).isEqualTo(AvailabilityStatus.BOOKED);
    }

    // ---------------- one row per (property, date) ----------------

    @Test
    @DisplayName("create: the same property and date twice is rejected")
    void createRejectsADuplicateDay() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        when(propertyService.existsById(PROPERTY_ID)).thenReturn(true);
        when(repository.findByPropertyIdAndCalendarDate(PROPERTY_ID, tomorrow))
                .thenReturn(Optional.of(entity(1L, tomorrow)));

        assertThatThrownBy(() -> service.create(request(tomorrow, null)))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("already exists");

        verify(repository, never()).save(any(AvailabilityCalendar.class));
    }

    @Test
    @DisplayName("create: defaults the status to AVAILABLE when none is given")
    void createDefaultsToAvailable() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        when(propertyService.existsById(PROPERTY_ID)).thenReturn(true);
        when(repository.findByPropertyIdAndCalendarDate(PROPERTY_ID, tomorrow)).thenReturn(Optional.empty());
        when(repository.save(any(AvailabilityCalendar.class))).thenAnswer(call -> call.getArgument(0));

        AvailabilityCalendarResponse created = service.create(request(tomorrow, null));

        assertThat(created.availabilityStatus()).isEqualTo(AvailabilityStatus.AVAILABLE);
    }

    @Test
    @DisplayName("create: refuses availability for a property that doesn't exist")
    void createRequiresARealProperty() {
        when(propertyService.existsById(PROPERTY_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.create(request(LocalDate.now().plusDays(1), null)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Property not found");
    }

    @Test
    @DisplayName("getById: unknown id gives a not-found error")
    void getByIdThrowsWhenMissing() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
