package it.gov.pagopa.mypay2pu.orchestrator.validation;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.OrchestrationMode;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationAction;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationExecutionStatus;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationFileExecutionStatus;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationRequestDto;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.PeriodDto;
import it.gov.pagopa.mypay2pu.orchestrator.exception.InvalidValueException;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationExecution;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationFileExecution;
import it.gov.pagopa.mypay2pu.orchestrator.repository.MigrationExecutionRepository;
import it.gov.pagopa.mypay2pu.orchestrator.repository.MigrationFileExecutionRepository;
import it.gov.pagopa.mypay2pu.orchestrator.utils.Constants;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.LocalDate;
import java.time.Month;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MigrationRequestValidatorTest {
  @Mock
  private MigrationExecutionRepository migrationExecutionRepositoryMock;
  @Mock
  private MigrationFileExecutionRepository migrationFileExecutionRepositoryMock;

  private MigrationRequestValidator validator;

  @BeforeEach
  void setUp() {
    validator = new MigrationRequestValidator(migrationExecutionRepositoryMock, migrationFileExecutionRepositoryMock);
  }

  @AfterEach
  void verifyMocks() {
    verifyNoMoreInteractions(migrationExecutionRepositoryMock, migrationFileExecutionRepositoryMock);
  }

  @Test
  void givenRequestWithoutActionWhenValidateThenRejectRequest() {
    MigrationRequestDto request = new MigrationRequestDto();

    InvalidValueException exception = assertThrows(
      InvalidValueException.class,
      () -> validator.validate(request)
    );

    assertEquals("action is required", exception.getMessage());
  }

  @Test
  void givenRequestWithoutOrchestrationModeWhenValidateThenRejectRequest() {
    MigrationRequestDto request = new MigrationRequestDto();
    request.setAction(MigrationAction.TRANSFER);

    InvalidValueException exception = assertThrows(InvalidValueException.class, () -> validator.validate(request));

    assertEquals("orchestrationMode is required", exception.getMessage());
  }

  @Test
  void givenExportOrCompleteWithoutIpaCodesWhenValidateThenRejectRequest() {
    for (MigrationAction action : List.of(MigrationAction.EXPORT, MigrationAction.COMPLETE)) {
      MigrationRequestDto request = new MigrationRequestDto();
      request.setAction(action);
      request.setOrchestrationMode(OrchestrationMode.DELTA);
      request.setIpaCodes(null);

      InvalidValueException exception = assertThrows(InvalidValueException.class, () -> validator.validate(request));
      assertEquals("ipaCodes are required and must not be empty", exception.getMessage());
    }
  }

  @Test
  void givenExportOrCompleteWithoutFileTypesWhenValidateThenRejectRequest() {
    for (MigrationAction action : List.of(MigrationAction.EXPORT, MigrationAction.COMPLETE)) {
      MigrationRequestDto request = new MigrationRequestDto();
      request.setAction(action);
      request.setOrchestrationMode(OrchestrationMode.DELTA);
      request.setIpaCodes(List.of("80000000000"));
      request.setFileTypesToInclude(null);

      InvalidValueException exception = assertThrows(InvalidValueException.class, () -> validator.validate(request));
      assertEquals("fileTypesToInclude are required and must not be empty", exception.getMessage());
    }
  }

  @ParameterizedTest
  @EnumSource(MigrationAction.class)
  void givenBothPaymentsReportingTypesWhenValidateThenRejectRequest(MigrationAction action) {
    MigrationRequestDto request = request(action, OrchestrationMode.FULL)
      .migrationId(UUID.randomUUID())
      .period(new PeriodDto())
      .fileTypesToInclude(List.of(
        MigrationFileType.PAYMENTS_REPORTING_MYPAY,
        MigrationFileType.PAYMENTS_REPORTING_MYPIVOT
      ));

    InvalidValueException exception = assertThrows(InvalidValueException.class, () -> validator.validate(request));

    assertEquals("INVALID_MIGRATION_REQUEST", exception.getCode());
    assertEquals(
      "fileTypesToInclude must not contain both PAYMENTS_REPORTING_MYPAY and PAYMENTS_REPORTING_MYPIVOT",
      exception.getMessage()
    );
  }

  @ParameterizedTest
  @EnumSource(value = MigrationFileType.class, names = {
    "PAYMENTS_REPORTING_MYPAY", "PAYMENTS_REPORTING_MYPIVOT"
  })
  void givenOnePaymentsReportingTypeWithOtherTypesWhenValidateThenAcceptRequest(MigrationFileType fileType) {
    MigrationRequestDto request = request(MigrationAction.EXPORT, OrchestrationMode.FULL)
      .period(new PeriodDto())
      .fileTypesToInclude(List.of(MigrationFileType.DEBT_POSITIONS, fileType));

    assertDoesNotThrow(() -> validator.validate(request));
  }

  @Test
  void givenTransferWithoutExportFieldsWhenValidateThenRequireOnlyMigrationId() {
    MigrationRequestDto request = new MigrationRequestDto();
    request.setAction(MigrationAction.TRANSFER);
    request.setOrchestrationMode(OrchestrationMode.FULL);
    InvalidValueException exception = assertThrows(
      InvalidValueException.class,
      () -> validator.validate(request)
    );
    assertEquals("migrationId is required for TRANSFER", exception.getMessage());

    UUID migrationId = UUID.randomUUID();
    request.setMigrationId(migrationId);
    MigrationExecution execution = MigrationExecution.builder()
      .id(migrationId)
      .status(MigrationExecutionStatus.COMPLETED)
      .action(MigrationAction.EXPORT)
      .build();
    when(migrationExecutionRepositoryMock.findById(migrationId)).thenReturn(Optional.of(execution));
    assertDoesNotThrow(() -> validator.validate(request));
    assertSame(execution, validator.requireTransferExecution(request));
    verify(migrationExecutionRepositoryMock).findById(migrationId);
  }

  @Test
  void givenRunningExportMigrationWhenRequireTransferExecutionThenRejectRequest() {
    UUID migrationId = UUID.randomUUID();
    MigrationRequestDto request = new MigrationRequestDto();
    request.setAction(MigrationAction.TRANSFER);
    request.setMigrationId(migrationId);
    request.setOrchestrationMode(OrchestrationMode.DELTA);
    MigrationExecution execution = MigrationExecution.builder()
      .id(migrationId)
      .status(MigrationExecutionStatus.RUNNING)
      .action(MigrationAction.EXPORT)
      .build();
    when(migrationExecutionRepositoryMock.findById(migrationId)).thenReturn(Optional.of(execution));

    assertDoesNotThrow(() -> validator.validate(request));
    InvalidValueException exception = assertThrows(
      InvalidValueException.class,
      () -> validator.requireTransferExecution(request)
    );

    assertEquals("migrationId must identify a completed EXPORT migration for TRANSFER", exception.getMessage());
    verify(migrationExecutionRepositoryMock).findById(migrationId);
  }

  @Test
  void givenCompletedNonExportMigrationWhenRequireTransferExecutionThenRejectRequest() {
    UUID migrationId = UUID.randomUUID();
    MigrationRequestDto request = new MigrationRequestDto();
    request.setAction(MigrationAction.TRANSFER);
    request.setMigrationId(migrationId);
    request.setOrchestrationMode(OrchestrationMode.DELTA);
    MigrationExecution execution = MigrationExecution.builder()
      .id(migrationId)
      .status(MigrationExecutionStatus.COMPLETED)
      .action(MigrationAction.COMPLETE)
      .build();
    when(migrationExecutionRepositoryMock.findById(migrationId)).thenReturn(Optional.of(execution));

    InvalidValueException exception = assertThrows(
      InvalidValueException.class,
      () -> validator.requireTransferExecution(request)
    );

    assertEquals("migrationId must identify a completed EXPORT migration for TRANSFER", exception.getMessage());
    verify(migrationExecutionRepositoryMock).findById(migrationId);
  }

  @Test
  void givenTransferWithRejectsOnlyOrchestrationWhenValidateThenRejectRequest() {
    MigrationRequestDto request = new MigrationRequestDto();
    request.setAction(MigrationAction.TRANSFER);
    request.setMigrationId(UUID.randomUUID());
    request.setOrchestrationMode(OrchestrationMode.REJECTS_ONLY);

    InvalidValueException exception = assertThrows(InvalidValueException.class, () -> validator.validate(request));

    assertEquals("orchestrationMode REJECTS_ONLY is not supported for TRANSFER", exception.getMessage());
  }

  @Test
  void givenFullOrchestrationWhenValidateThenRequirePeriod() {
    for (MigrationAction action : List.of(MigrationAction.EXPORT, MigrationAction.COMPLETE)) {
      MigrationRequestDto request = request(action, OrchestrationMode.FULL);

      InvalidValueException exception = assertThrows(InvalidValueException.class, () -> validator.validate(request));

      assertEquals("period is required when orchestrationMode is FULL", exception.getMessage());

      request.setPeriod(new PeriodDto());
      assertDoesNotThrow(() -> validator.validate(request));
    }
  }

  @Test
  void givenDeltaWithoutMigrationIdWhenValidateThenRejectRequest() {
    MigrationRequestDto request = request(MigrationAction.COMPLETE, OrchestrationMode.DELTA);
    request.setFileTypesToInclude(List.of(MigrationFileType.DEBT_POSITIONS));
    InvalidValueException exception = assertThrows(InvalidValueException.class, () -> validator.validate(request));
    assertEquals("migrationId is required when orchestrationMode is DELTA", exception.getMessage());
  }

  @ParameterizedTest
  @EnumSource(value = MigrationFileType.class, names = {
    "ORGANIZATIONS", "ORG_SIL_SERVICES", "DEBT_POSITIONS_TYPE",
    "DEBT_POSITIONS_TYPE_ORG", "DEBT_POSITIONS_TYPE_ORG_OPERATORS"
  })
  void givenFullOnlyTypeWhenValidateThenRejectDelta(MigrationFileType type) {
    MigrationRequestDto request = request(MigrationAction.EXPORT, OrchestrationMode.DELTA);
    request.setMigrationId(UUID.randomUUID());
    request.setFileTypesToInclude(List.of(type));

    InvalidValueException exception = assertThrows(InvalidValueException.class, () -> validator.validate(request));

    assertEquals("fileTypesToInclude contains types not supported for DELTA", exception.getMessage());
  }

  @ParameterizedTest
  @EnumSource(value = MigrationExecutionStatus.class, names = {"RUNNING", "FAILED"})
  void givenIncompletePreviousExecutionWhenRequireDeltaPreviousMigrationThenReject(MigrationExecutionStatus status) {
    UUID parentId = UUID.randomUUID();
    MigrationRequestDto request = request(MigrationAction.EXPORT, OrchestrationMode.DELTA);
    request.setMigrationId(parentId);
    request.setFileTypesToInclude(List.of(MigrationFileType.DEBT_POSITIONS));
    MigrationExecution previous = MigrationExecution.builder()
      .id(parentId).action(MigrationAction.EXPORT).status(status)
      .orchestrationMode(OrchestrationMode.FULL)
      .ipaCodes(request.getIpaCodes()).fileTypes(request.getFileTypesToInclude())
      .lastSuccessfulExtractionAt(LocalDate.of(2026, Month.JANUARY, 2).atStartOfDay())
      .build();
    when(migrationExecutionRepositoryMock.findById(parentId)).thenReturn(Optional.of(previous));

    assertThrows(InvalidValueException.class, () -> validator.requireDeltaPreviousMigration(request));

    verify(migrationExecutionRepositoryMock).findById(parentId);
  }

  @Test
  void givenFailedPreviousFileWhenRequireDeltaPreviousMigrationThenReject() {
    UUID parentId = UUID.randomUUID();
    MigrationRequestDto request = request(MigrationAction.EXPORT, OrchestrationMode.DELTA);
    request.setMigrationId(parentId);
    request.setFileTypesToInclude(List.of(MigrationFileType.DEBT_POSITIONS));
    MigrationExecution previous = MigrationExecution.builder()
      .id(parentId).action(MigrationAction.EXPORT).status(MigrationExecutionStatus.COMPLETED)
      .orchestrationMode(OrchestrationMode.FULL)
      .ipaCodes(request.getIpaCodes()).fileTypes(request.getFileTypesToInclude())
      .lastSuccessfulExtractionAt(LocalDate.of(2026, Month.JANUARY, 2).atStartOfDay()).build();
    MigrationFileExecution failed = MigrationFileExecution.builder()
      .fileType(MigrationFileType.DEBT_POSITIONS).status(MigrationFileExecutionStatus.FAILED).build();
    when(migrationExecutionRepositoryMock.findById(parentId)).thenReturn(Optional.of(previous));
    when(migrationFileExecutionRepositoryMock.findAllByMigrationIdOrderByCreationDateAscIdAsc(parentId))
      .thenReturn(List.of(failed));

    assertThrows(InvalidValueException.class, () -> validator.requireDeltaPreviousMigration(request));

    verify(migrationExecutionRepositoryMock).findById(parentId);
    verify(migrationFileExecutionRepositoryMock).findAllByMigrationIdOrderByCreationDateAscIdAsc(parentId);
  }

  @ParameterizedTest
  @EnumSource(value = MigrationFileExecutionStatus.class, names = {"EXPORTED", "COMPLETED"})
  void givenSuccessfulFilesAndEquivalentIpaSetsWhenRequireDeltaPreviousMigrationThenReturnsPrevious(
    MigrationFileExecutionStatus status
  ) {
    MigrationRequestDto request = request(MigrationAction.EXPORT, OrchestrationMode.DELTA)
      .migrationId(UUID.randomUUID())
      .ipaCodes(List.of("IPA1", "IPA2"))
      .fileTypesToInclude(List.of(MigrationFileType.DEBT_POSITIONS, MigrationFileType.ASSESSMENTS));
    MigrationExecution previous = deltaPrevious(request);
    previous.setIpaCodes(List.of("IPA2", "IPA1", "IPA1"));
    List<MigrationFileExecution> fileExecutions = List.of(
      MigrationFileExecution.builder().fileType(MigrationFileType.DEBT_POSITIONS).status(status).build(),
      MigrationFileExecution.builder().fileType(MigrationFileType.ASSESSMENTS).status(status).build(),
      MigrationFileExecution.builder().fileType(MigrationFileType.ORGANIZATIONS)
        .status(MigrationFileExecutionStatus.FAILED).build()
    );
    when(migrationExecutionRepositoryMock.findById(previous.getId())).thenReturn(Optional.of(previous));
    when(migrationFileExecutionRepositoryMock.findAllByMigrationIdOrderByCreationDateAscIdAsc(previous.getId()))
      .thenReturn(fileExecutions);

    assertSame(previous, validator.requireDeltaPreviousMigration(request));

    verify(migrationExecutionRepositoryMock).findById(previous.getId());
    verify(migrationFileExecutionRepositoryMock).findAllByMigrationIdOrderByCreationDateAscIdAsc(previous.getId());
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 2})
  void givenMissingOrDuplicatePreviousFilesWhenRequireDeltaPreviousMigrationThenRejects(int fileCount) {
    MigrationRequestDto request = request(MigrationAction.EXPORT, OrchestrationMode.DELTA)
      .migrationId(UUID.randomUUID()).fileTypesToInclude(List.of(MigrationFileType.DEBT_POSITIONS));
    MigrationExecution previous = deltaPrevious(request);
    List<MigrationFileExecution> files = fileCount == 0 ? List.of() : List.of(
      MigrationFileExecution.builder().fileType(MigrationFileType.DEBT_POSITIONS)
        .status(MigrationFileExecutionStatus.EXPORTED).build(),
      MigrationFileExecution.builder().fileType(MigrationFileType.DEBT_POSITIONS)
        .status(MigrationFileExecutionStatus.FAILED).build()
    );
    when(migrationExecutionRepositoryMock.findById(previous.getId())).thenReturn(Optional.of(previous));
    when(migrationFileExecutionRepositoryMock.findAllByMigrationIdOrderByCreationDateAscIdAsc(previous.getId()))
      .thenReturn(files);

    InvalidValueException failure = assertThrows(
      InvalidValueException.class, () -> validator.requireDeltaPreviousMigration(request));

    assertEquals("previous extraction must have one successful file execution for every requested file type",
      failure.getMessage());
    verify(migrationExecutionRepositoryMock).findById(previous.getId());
    verify(migrationFileExecutionRepositoryMock).findAllByMigrationIdOrderByCreationDateAscIdAsc(previous.getId());
  }

  @Test
  void givenDifferentIpaScopeWhenRequireDeltaPreviousMigrationThenRejectsBeforeLoadingFiles() {
    MigrationRequestDto request = request(MigrationAction.EXPORT, OrchestrationMode.DELTA)
      .migrationId(UUID.randomUUID()).ipaCodes(List.of("IPA1", "IPA2"))
      .fileTypesToInclude(List.of(MigrationFileType.DEBT_POSITIONS));
    MigrationExecution previous = deltaPrevious(request);
    previous.setIpaCodes(List.of("IPA1"));
    when(migrationExecutionRepositoryMock.findById(previous.getId())).thenReturn(Optional.of(previous));

    InvalidValueException failure = assertThrows(
      InvalidValueException.class, () -> validator.requireDeltaPreviousMigration(request));

    assertEquals(
      "migrationId must identify a completed extraction with a checkpoint and matching IPA codes and file types",
      failure.getMessage());
    verify(migrationExecutionRepositoryMock).findById(previous.getId());
  }

  @ParameterizedTest
  @EnumSource(value = MigrationAction.class, names = {"EXPORT", "COMPLETE"})
  void givenRejectsOnlyWithExistingParentWhenValidateThenRejectUntilDiscardKeysAreAvailable(MigrationAction action) {
    UUID parentId = UUID.randomUUID();
    MigrationRequestDto request = request(action, OrchestrationMode.REJECTS_ONLY);
    request.setMigrationId(parentId);
    when(migrationExecutionRepositoryMock.findById(parentId))
      .thenReturn(Optional.of(MigrationExecution.builder().id(parentId).build()));

    InvalidValueException exception = assertThrows(InvalidValueException.class, () -> validator.validate(request));

    assertEquals(
      "orchestrationMode REJECTS_ONLY is unavailable because discard-key data is not yet available",
      exception.getMessage()
    );
    verify(migrationExecutionRepositoryMock).findById(parentId);
  }

  @Test
  void givenRejectsOnlyWithUnknownParentWhenValidateThenRejectInvalidParent() {
    UUID parentId = UUID.randomUUID();
    MigrationRequestDto request = request(MigrationAction.COMPLETE, OrchestrationMode.REJECTS_ONLY);
    request.setMigrationId(parentId);
    when(migrationExecutionRepositoryMock.findById(parentId)).thenReturn(Optional.empty());

    InvalidValueException exception = assertThrows(InvalidValueException.class, () -> validator.validate(request));

    assertEquals("migrationId does not identify an existing migration", exception.getMessage());
    verify(migrationExecutionRepositoryMock).findById(parentId);
  }

  @Test
  void givenFullOrchestrationWhenValidateThenRejectInvertedPeriodAndAllowOptionalEndpoints() {
    MigrationRequestDto request = request(MigrationAction.EXPORT, OrchestrationMode.FULL);
    request.setPeriod(new PeriodDto(
      LocalDate.of(2026, Month.FEBRUARY, 1).atStartOfDay(Constants.ZONEID).toOffsetDateTime(),
      LocalDate.of(2026, Month.JANUARY, 1).atStartOfDay(Constants.ZONEID).toOffsetDateTime()
    ));

    InvalidValueException exception = assertThrows(InvalidValueException.class, () -> validator.validate(request));
    assertEquals("period.fromDate must not be after period.toDate", exception.getMessage());

    request.setPeriod(new PeriodDto());
    assertDoesNotThrow(() -> validator.validate(request));
    request.setPeriod(new PeriodDto(
      LocalDate.of(2026, Month.JANUARY, 1).atStartOfDay(Constants.ZONEID).toOffsetDateTime(), null));
    assertDoesNotThrow(() -> validator.validate(request));
  }

  @Test
  void givenRejectsOnlyOrchestrationWhenValidateThenRequirePreviousMigrationId() {
    for (MigrationAction action : List.of(MigrationAction.EXPORT, MigrationAction.COMPLETE)) {
      MigrationRequestDto request = request(action, OrchestrationMode.REJECTS_ONLY);

      InvalidValueException exception = assertThrows(InvalidValueException.class, () -> validator.validate(request));

      assertEquals("migrationId is required when orchestrationMode is REJECTS_ONLY", exception.getMessage());
    }
  }

  @Test
  void givenRejectsOnlyOrchestrationWithExistingMigrationWhenRequirePreviousMigrationThenReturnExecution() {
    UUID migrationId = UUID.randomUUID();
    MigrationRequestDto request = request(MigrationAction.COMPLETE, OrchestrationMode.REJECTS_ONLY);
    request.setMigrationId(migrationId);
    MigrationExecution execution = MigrationExecution.builder().id(migrationId).build();
    when(migrationExecutionRepositoryMock.findById(migrationId)).thenReturn(Optional.of(execution));

    assertSame(execution, validator.requirePreviousMigration(request));

    verify(migrationExecutionRepositoryMock).findById(migrationId);
  }

  private MigrationExecution deltaPrevious(MigrationRequestDto request) {
    return MigrationExecution.builder()
      .id(request.getMigrationId())
      .action(MigrationAction.EXPORT)
      .status(MigrationExecutionStatus.COMPLETED)
      .orchestrationMode(OrchestrationMode.FULL)
      .ipaCodes(request.getIpaCodes())
      .fileTypes(request.getFileTypesToInclude())
      .lastSuccessfulExtractionAt(LocalDate.of(2026, Month.JANUARY, 2).atStartOfDay())
      .build();
  }

  private MigrationRequestDto request(MigrationAction action, OrchestrationMode orchestrationMode) {
    MigrationRequestDto request = new MigrationRequestDto();
    request.setAction(action);
    request.setOrchestrationMode(orchestrationMode);
    request.setIpaCodes(List.of("80000000000"));
    request.setFileTypesToInclude(List.of(MigrationFileType.ORGANIZATIONS));
    return request;
  }
}
