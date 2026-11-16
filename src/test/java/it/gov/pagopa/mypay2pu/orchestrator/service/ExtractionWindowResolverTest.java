package it.gov.pagopa.mypay2pu.orchestrator.service;

import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationAction;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationRequestDto;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.OrchestrationMode;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.PeriodDto;
import it.gov.pagopa.mypay2pu.orchestrator.exception.InvalidValueException;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationExecution;
import it.gov.pagopa.mypay2pu.orchestrator.repository.MigrationExecutionRepository;
import it.gov.pagopa.mypay2pu.orchestrator.validation.MigrationRequestValidator;
import java.time.LocalDate;
import java.time.Month;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExtractionWindowResolverTest {

  private static final UUID MIGRATION_ID = UUID.randomUUID();
  private static final LocalDate CHECKPOINT_DATE = LocalDate.of(2026, Month.JANUARY, 2);

  @Mock
  private MigrationRequestValidator migrationRequestValidatorMock;
  @Mock
  private MigrationExecutionRepository migrationExecutionRepositoryMock;

  private ExtractionWindowResolver resolver;

  @BeforeEach
  void setUp() {
    resolver = new ExtractionWindowResolver(migrationRequestValidatorMock, migrationExecutionRepositoryMock);
  }

  @AfterEach
  void verifyMocks() {
    verifyNoMoreInteractions(migrationRequestValidatorMock, migrationExecutionRepositoryMock);
  }

  @Test
  void givenPersistedFullBoundsWhenResolvePersistedWindowThenConvertsDatesToUtc() {
    MigrationRequestDto request = request(OrchestrationMode.FULL).migrationId(MIGRATION_ID);
    LocalDate from = LocalDate.of(2026, Month.JANUARY, 1);
    LocalDate to = LocalDate.of(2026, Month.JANUARY, 3);
    when(migrationExecutionRepositoryMock.findById(MIGRATION_ID)).thenReturn(Optional.of(
      MigrationExecution.builder().dateFrom(from).dateTo(to).orchestrationMode(OrchestrationMode.FULL).build()));

    ExtractionWindow window = resolver.resolvePersistedWindow(request);

    assertEquals(from.atStartOfDay().atOffset(ZoneOffset.UTC), window.dateFrom());
    assertEquals(to.atStartOfDay().atOffset(ZoneOffset.UTC), window.dateTo());
    assertFalse(window.delta());
    verify(migrationExecutionRepositoryMock).findById(MIGRATION_ID);
  }

  @Test
  void givenPersistedDeltaBoundsWhenResolvePersistedWindowThenMarksWindowAsDelta() {
    MigrationRequestDto request = request(OrchestrationMode.DELTA).migrationId(MIGRATION_ID);
    LocalDate from = LocalDate.of(2026, Month.JANUARY, 2);
    LocalDate to = LocalDate.of(2026, Month.JANUARY, 3);
    when(migrationExecutionRepositoryMock.findById(MIGRATION_ID)).thenReturn(Optional.of(
      MigrationExecution.builder().dateFrom(from).dateTo(to).orchestrationMode(OrchestrationMode.DELTA).build()));

    ExtractionWindow window = resolver.resolvePersistedWindow(request);

    assertEquals(OffsetDateTime.of(from.atStartOfDay(), ZoneOffset.UTC), window.dateFrom());
    assertEquals(OffsetDateTime.of(to.atStartOfDay(), ZoneOffset.UTC), window.dateTo());
    assertTrue(window.delta());
    verify(migrationExecutionRepositoryMock).findById(MIGRATION_ID);
  }

  @Test
  void givenMissingPersistedExecutionWhenResolvePersistedWindowThenFailsWithoutFallback() {
    MigrationRequestDto request = request(OrchestrationMode.FULL).migrationId(MIGRATION_ID)
      .period(new PeriodDto(
        LocalDate.of(2026, Month.JANUARY, 1), LocalDate.of(2026, Month.JANUARY, 3)));
    when(migrationExecutionRepositoryMock.findById(MIGRATION_ID)).thenReturn(Optional.empty());

    IllegalStateException failure = assertThrows(
      IllegalStateException.class, () -> resolver.resolvePersistedWindow(request));

    assertEquals("Missing migration execution " + MIGRATION_ID, failure.getMessage());
    verify(migrationExecutionRepositoryMock).findById(MIGRATION_ID);
  }

  @Test
  void givenMissingPersistedEndWhenResolvePersistedWindowThenRejectsWindow() {
    MigrationRequestDto request = request(OrchestrationMode.FULL).migrationId(MIGRATION_ID);
    when(migrationExecutionRepositoryMock.findById(MIGRATION_ID)).thenReturn(Optional.of(
      MigrationExecution.builder().dateFrom(CHECKPOINT_DATE).orchestrationMode(OrchestrationMode.FULL).build()));

    NullPointerException failure = assertThrows(
      NullPointerException.class, () -> resolver.resolvePersistedWindow(request));

    assertEquals("Persisted extraction end is required for migration " + MIGRATION_ID, failure.getMessage());
    verify(migrationExecutionRepositoryMock).findById(MIGRATION_ID);
  }

  @ParameterizedTest
  @EnumSource(value = OrchestrationMode.class, names = {"REJECTS_ONLY"})
  void givenModeWithoutWindowWhenResolvePersistedWindowThenReturnsNull(OrchestrationMode mode) {
    assertNull(resolver.resolvePersistedWindow(request(mode).migrationId(MIGRATION_ID)));
  }

  @Test
  void givenTransferActionWhenResolvePersistedWindowThenReturnsNull() {
    MigrationRequestDto request = request(OrchestrationMode.FULL)
      .action(MigrationAction.TRANSFER).migrationId(MIGRATION_ID);

    assertNull(resolver.resolvePersistedWindow(request));
  }

  @Test
  void givenExplicitFullBoundsWhenResolveThenPreservesBothDatesWithoutParent() {
    MigrationExecution execution = MigrationExecution.builder()
      .dateFrom(CHECKPOINT_DATE).dateTo(CHECKPOINT_DATE.plusDays(1)).build();

    resolver.resolve(request(OrchestrationMode.FULL), execution);

    assertEquals(CHECKPOINT_DATE, execution.getDateFrom());
    assertEquals(CHECKPOINT_DATE.plusDays(1), execution.getDateTo());
    assertNull(execution.getParentExecutionId());
    assertNull(execution.getSinceDate());
  }

  @Test
  void givenFutureUpperBoundWhenResolveThenRejectsWindow() {
    MigrationRequestDto request = request(OrchestrationMode.FULL);
    MigrationExecution execution = MigrationExecution.builder()
      .dateTo(LocalDate.now(ZoneOffset.UTC).plusDays(2)).build();

    InvalidValueException failure = assertThrows(InvalidValueException.class, () -> resolver.resolve(request, execution));

    assertEquals("period.toDate must not be after the current UTC date", failure.getMessage());
  }

  @Test
  void givenInvertedWindowWhenResolveThenRejectsWindow() {
    MigrationRequestDto request = request(OrchestrationMode.FULL);
    MigrationExecution execution = MigrationExecution.builder()
      .dateFrom(CHECKPOINT_DATE.plusDays(1)).dateTo(CHECKPOINT_DATE).build();

    InvalidValueException failure = assertThrows(InvalidValueException.class, () -> resolver.resolve(request, execution));

    assertEquals("extraction period must have a start before its exclusive end", failure.getMessage());
  }

  @Test
  void givenMatchingExplicitDeltaStartWhenResolveThenPersistsExactCheckpointAndParent() {
    MigrationRequestDto request = request(OrchestrationMode.DELTA);
    MigrationExecution previous = previous();
    MigrationExecution execution = MigrationExecution.builder()
      .dateFrom(CHECKPOINT_DATE).dateTo(CHECKPOINT_DATE.plusDays(1)).build();
    when(migrationRequestValidatorMock.requireDeltaPreviousMigration(request)).thenReturn(previous);

    resolver.resolve(request, execution);

    assertEquals(previous.getLastSuccessfulExtractionAt(), execution.getDateFrom().atStartOfDay());
    assertEquals(previous.getId(), execution.getParentExecutionId());
    assertNull(execution.getSinceDate());
    verify(migrationRequestValidatorMock).requireDeltaPreviousMigration(request);
  }

  @Test
  void givenDifferentExplicitDeltaStartWhenResolveThenRejectsWithoutPersistingParent() {
    MigrationRequestDto request = request(OrchestrationMode.DELTA);
    MigrationExecution execution = MigrationExecution.builder()
      .dateFrom(CHECKPOINT_DATE.minusDays(1)).dateTo(CHECKPOINT_DATE.plusDays(1)).build();
    when(migrationRequestValidatorMock.requireDeltaPreviousMigration(request)).thenReturn(previous());

    InvalidValueException failure = assertThrows(InvalidValueException.class, () -> resolver.resolve(request, execution));

    assertEquals("period.fromDate must equal the previous extraction checkpoint", failure.getMessage());
    assertNull(execution.getParentExecutionId());
    verify(migrationRequestValidatorMock).requireDeltaPreviousMigration(request);
  }

  @Test
  void givenMidnightCheckpointDifferentFromPreviousEndWhenResolveThenRejectsWindow() {
    MigrationRequestDto request = request(OrchestrationMode.DELTA);
    MigrationExecution previous = previous();
    previous.setDateTo(CHECKPOINT_DATE.minusDays(1));
    MigrationExecution execution = MigrationExecution.builder().dateTo(CHECKPOINT_DATE.plusDays(1)).build();
    when(migrationRequestValidatorMock.requireDeltaPreviousMigration(request)).thenReturn(previous);

    InvalidValueException failure = assertThrows(InvalidValueException.class, () -> resolver.resolve(request, execution));

    assertEquals("previous checkpoint must equal its exclusive period.toDate at midnight UTC", failure.getMessage());
    verify(migrationRequestValidatorMock).requireDeltaPreviousMigration(request);
  }

  @ParameterizedTest
  @EnumSource(value = OrchestrationMode.class, names = {"FULL", "DELTA"})
  void givenTransferWhenResolveThenDoesNotResolveOrChangeWindow(OrchestrationMode mode) {
    MigrationExecution execution = previous();
    MigrationRequestDto request = request(mode).action(MigrationAction.TRANSFER);

    resolver.resolve(request, execution);

    assertNull(execution.getDateFrom());
    assertEquals(CHECKPOINT_DATE, execution.getDateTo());
    assertEquals(CHECKPOINT_DATE.atStartOfDay(), execution.getLastSuccessfulExtractionAt());
  }

  @Test
  void givenRejectsOnlyWhenResolveThenDoesNotAcquireWindow() {
    MigrationExecution execution = new MigrationExecution();

    resolver.resolve(request(OrchestrationMode.REJECTS_ONLY), execution);

    assertNull(execution.getDateFrom());
    assertNull(execution.getDateTo());
    assertNull(execution.getParentExecutionId());
  }

  private MigrationRequestDto request(OrchestrationMode mode) {
    return new MigrationRequestDto().action(MigrationAction.COMPLETE).orchestrationMode(mode);
  }

  private MigrationExecution previous() {
    return MigrationExecution.builder().id(UUID.randomUUID()).dateTo(CHECKPOINT_DATE)
      .lastSuccessfulExtractionAt(CHECKPOINT_DATE.atStartOfDay()).build();
  }
}
