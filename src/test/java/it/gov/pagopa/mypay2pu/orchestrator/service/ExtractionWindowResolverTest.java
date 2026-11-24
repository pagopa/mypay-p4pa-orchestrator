package it.gov.pagopa.mypay2pu.orchestrator.service;

import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationAction;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationRequestDto;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.OrchestrationMode;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.PeriodDto;
import it.gov.pagopa.mypay2pu.orchestrator.exception.InvalidValueException;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationExecution;
import it.gov.pagopa.mypay2pu.orchestrator.utils.Constants;
import it.gov.pagopa.mypay2pu.orchestrator.validation.MigrationRequestValidator;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExtractionWindowResolverTest {

  private static final LocalDateTime CHECKPOINT =
    LocalDateTime.of(2026, Month.JANUARY, 2, 12, 30, 45, 123456000);

  @Mock
  private MigrationRequestValidator migrationRequestValidatorMock;

  private ExtractionWindowResolver resolver;

  @BeforeEach
  void setUp() {
    resolver = new ExtractionWindowResolver(migrationRequestValidatorMock);
  }

  @AfterEach
  void verifyMocks() {
    verifyNoMoreInteractions(migrationRequestValidatorMock);
  }

  @Test
  void givenExplicitFullTimestampsWhenResolveThenAlignsRequestAndExecutionWithoutTruncating() {
    OffsetDateTime from = CHECKPOINT.atZone(Constants.ZONEID).toOffsetDateTime();
    OffsetDateTime to = from.plusHours(2).plusNanos(1000);
    MigrationRequestDto request = request(OrchestrationMode.FULL).period(new PeriodDto(from, to));
    MigrationExecution execution = new MigrationExecution();

    resolver.resolve(request, execution);

    assertEquals(from, request.getPeriod().getFromDate());
    assertEquals(to, request.getPeriod().getToDate());
    assertEquals(CHECKPOINT, execution.getDateFrom());
    assertEquals(CHECKPOINT.plusHours(2).plusNanos(1000), execution.getDateTo());
    assertNull(execution.getParentExecutionId());
    assertNull(execution.getSinceDate());
  }

  @ParameterizedTest
  @EnumSource(value = Month.class, names = {"JANUARY", "JULY"})
  void givenPreviousIntradayEndWhenResolveThenUsesExactTimestampWithSeasonalOffset(Month month) {
    LocalDateTime end = CHECKPOINT.withMonth(month.getValue());
    MigrationExecution previous = previous(end);
    OffsetDateTime to = end.plusHours(1).atZone(Constants.ZONEID).toOffsetDateTime();
    MigrationRequestDto request = request(OrchestrationMode.DELTA).period(new PeriodDto(null, to));
    MigrationExecution execution = new MigrationExecution();
    when(migrationRequestValidatorMock.requireDeltaPreviousMigration(request)).thenReturn(previous);

    resolver.resolve(request, execution);

    OffsetDateTime expectedFrom = end.atZone(Constants.ZONEID).toOffsetDateTime();
    assertEquals(expectedFrom, request.getPeriod().getFromDate());
    assertEquals(to, request.getPeriod().getToDate());
    assertEquals(end, execution.getDateFrom());
    assertEquals(to.toLocalDateTime(), execution.getDateTo());
    assertEquals(previous.getId(), execution.getParentExecutionId());
    assertNull(execution.getSinceDate());
    verify(migrationRequestValidatorMock).requireDeltaPreviousMigration(request);
  }

  @ParameterizedTest
  @EnumSource(value = Month.class, names = {"JANUARY", "JULY"})
  void givenMatchingDeltaStartWithDifferentOffsetWhenResolveThenAcceptsSameInstant(Month month) {
    LocalDateTime checkpoint = CHECKPOINT.withMonth(month.getValue());
    MigrationExecution previous = previous(checkpoint);
    OffsetDateTime from = checkpoint.atZone(Constants.ZONEID).toOffsetDateTime();
    OffsetDateTime to = from.plusHours(1).withOffsetSameInstant(ZoneOffset.UTC);
    MigrationRequestDto request = request(OrchestrationMode.DELTA)
      .period(new PeriodDto(from.withOffsetSameInstant(ZoneOffset.UTC), to));
    MigrationExecution execution = new MigrationExecution();
    when(migrationRequestValidatorMock.requireDeltaPreviousMigration(request)).thenReturn(previous);

    resolver.resolve(request, execution);

    assertTrue(from.isEqual(request.getPeriod().getFromDate()));
    assertEquals(checkpoint, execution.getDateFrom());
    assertEquals(checkpoint.plusHours(1), execution.getDateTo());
    verify(migrationRequestValidatorMock).requireDeltaPreviousMigration(request);
  }

  @Test
  void givenSameLocalDeltaStartWithDifferentOffsetWhenResolveThenRejectsDifferentInstant() {
    OffsetDateTime previousEnd = CHECKPOINT.atZone(Constants.ZONEID).toOffsetDateTime();
    OffsetDateTime from = previousEnd.withOffsetSameLocal(ZoneOffset.UTC);
    MigrationRequestDto request = request(OrchestrationMode.DELTA)
      .period(new PeriodDto(from, from.plusHours(1)));
    MigrationExecution execution = new MigrationExecution();
    when(migrationRequestValidatorMock.requireDeltaPreviousMigration(request)).thenReturn(previous(CHECKPOINT));

    InvalidValueException failure = assertThrows(InvalidValueException.class, () -> resolver.resolve(request, execution));

    assertEquals("period.fromDate must equal the previous extraction checkpoint", failure.getMessage());
    assertEquals(from, request.getPeriod().getFromDate());
    assertNull(execution.getDateFrom());
    assertNull(execution.getParentExecutionId());
    verify(migrationRequestValidatorMock).requireDeltaPreviousMigration(request);
  }

  @ParameterizedTest
  @EnumSource(value = OrchestrationMode.class, mode = EnumSource.Mode.EXCLUDE, names = {"REJECTS_ONLY"})
  void givenMissingPeriodEndWhenResolveThenUsesExpectedStartOfDayInApplicationZone(OrchestrationMode mode) {
    MigrationExecution previous = mode == OrchestrationMode.DELTA ? previous(CHECKPOINT) : null;
    MigrationRequestDto request = request(mode);
    if (mode == OrchestrationMode.FULL) {
      request.period(new PeriodDto());
    }
    MigrationExecution execution = new MigrationExecution();
    if (previous != null) {
      when(migrationRequestValidatorMock.requireDeltaPreviousMigration(request)).thenReturn(previous);
    }
    OffsetDateTime before = LocalDate.now(Constants.ZONEID).atStartOfDay(Constants.ZONEID).toOffsetDateTime();

    resolver.resolve(request, execution);

    OffsetDateTime after = LocalDate.now(Constants.ZONEID).atStartOfDay(Constants.ZONEID).toOffsetDateTime();
    assertTrue(List.of(before, after).contains(request.getPeriod().getToDate()));
    assertEquals(request.getPeriod().getToDate().toLocalDateTime(), execution.getDateTo());
    if (mode == OrchestrationMode.DELTA) {
      assertEquals(CHECKPOINT, execution.getDateFrom());
      verify(migrationRequestValidatorMock).requireDeltaPreviousMigration(request);
    } else {
      assertNull(request.getPeriod().getFromDate());
      assertNull(execution.getDateFrom());
    }
  }

  @Test
  void givenDifferentExplicitDeltaStartWhenResolveThenRejectsWithoutChangingRequestOrExecution() {
    OffsetDateTime from = CHECKPOINT.minusNanos(1000).atZone(Constants.ZONEID).toOffsetDateTime();
    MigrationRequestDto request = request(OrchestrationMode.DELTA).period(new PeriodDto(from, from.plusHours(1)));
    MigrationExecution execution = new MigrationExecution();
    when(migrationRequestValidatorMock.requireDeltaPreviousMigration(request)).thenReturn(previous(CHECKPOINT));

    InvalidValueException failure = assertThrows(InvalidValueException.class, () -> resolver.resolve(request, execution));

    assertEquals("period.fromDate must equal the previous extraction checkpoint", failure.getMessage());
    assertEquals(from, request.getPeriod().getFromDate());
    assertNull(execution.getDateFrom());
    assertNull(execution.getParentExecutionId());
    verify(migrationRequestValidatorMock).requireDeltaPreviousMigration(request);
  }

  @Test
  void givenCheckpointDifferentFromPreviousEndWhenResolveThenRejectsWindow() {
    MigrationExecution previous = previous(CHECKPOINT);
    previous.setLastSuccessfulExtractionAt(CHECKPOINT.minusNanos(1000));
    MigrationRequestDto request = request(OrchestrationMode.DELTA);
    MigrationExecution execution = new MigrationExecution();
    when(migrationRequestValidatorMock.requireDeltaPreviousMigration(request)).thenReturn(previous);

    InvalidValueException failure = assertThrows(InvalidValueException.class, () -> resolver.resolve(request, execution));

    assertEquals("previous checkpoint must equal its exclusive period.toDate", failure.getMessage());
    verify(migrationRequestValidatorMock).requireDeltaPreviousMigration(request);
  }

  @Test
  void givenMissingPreviousEndWhenResolveThenRejectsWindow() {
    MigrationExecution previous = previous(CHECKPOINT);
    previous.setDateTo(null);
    MigrationRequestDto request = request(OrchestrationMode.DELTA);
    MigrationExecution execution = new MigrationExecution();
    when(migrationRequestValidatorMock.requireDeltaPreviousMigration(request)).thenReturn(previous);

    InvalidValueException failure = assertThrows(InvalidValueException.class, () -> resolver.resolve(request, execution));

    assertEquals("previous checkpoint must equal its exclusive period.toDate", failure.getMessage());
    verify(migrationRequestValidatorMock).requireDeltaPreviousMigration(request);
  }

  @Test
  void givenFutureUpperBoundWhenResolveThenRejectsWindow() {
    MigrationRequestDto request = request(OrchestrationMode.FULL)
      .period(new PeriodDto(null, OffsetDateTime.now(Constants.ZONEID).plusDays(1)));
    MigrationExecution execution = new MigrationExecution();

    InvalidValueException failure = assertThrows(InvalidValueException.class, () -> resolver.resolve(request, execution));

    assertEquals("period.toDate must not be in the future", failure.getMessage());
  }

  @Test
  void givenEqualOrInvertedBoundsWhenResolveThenRejectsWindow() {
    OffsetDateTime from = CHECKPOINT.atZone(Constants.ZONEID).toOffsetDateTime();
    MigrationExecution execution = new MigrationExecution();
    for (OffsetDateTime to : List.of(from, from.minusSeconds(1))) {
      MigrationRequestDto request = request(OrchestrationMode.FULL).period(new PeriodDto(from, to));

      InvalidValueException failure = assertThrows(InvalidValueException.class, () -> resolver.resolve(request, execution));

      assertEquals("extraction period must have a start before its exclusive end", failure.getMessage());
    }
  }

  @ParameterizedTest
  @EnumSource(value = OrchestrationMode.class, names = {"FULL", "DELTA"})
  void givenTransferWhenResolveThenLeavesRequestAndExecutionUnchanged(OrchestrationMode mode) {
    MigrationExecution execution = previous(CHECKPOINT);
    MigrationRequestDto request = request(mode).action(MigrationAction.TRANSFER);

    resolver.resolve(request, execution);

    assertNull(request.getPeriod());
    assertNull(execution.getDateFrom());
    assertEquals(CHECKPOINT, execution.getDateTo());
    assertEquals(CHECKPOINT, execution.getLastSuccessfulExtractionAt());
  }

  @Test
  void givenRejectsOnlyWhenResolveThenDoesNotAcquireWindow() {
    MigrationExecution execution = new MigrationExecution();
    MigrationRequestDto request = request(OrchestrationMode.REJECTS_ONLY);

    resolver.resolve(request, execution);

    assertNull(request.getPeriod());
    assertNull(execution.getDateFrom());
    assertNull(execution.getDateTo());
    assertNull(execution.getParentExecutionId());
  }

  private MigrationRequestDto request(OrchestrationMode mode) {
    return new MigrationRequestDto().action(MigrationAction.COMPLETE).orchestrationMode(mode);
  }

  private MigrationExecution previous(LocalDateTime end) {
    return MigrationExecution.builder().id(UUID.randomUUID()).dateTo(end).lastSuccessfulExtractionAt(end).build();
  }
}
