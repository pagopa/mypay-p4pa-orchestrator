package it.gov.pagopa.mypay2pu.orchestrator.service;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationAction;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationExecutionStatus;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationFileExecutionStatus;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationExecution;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationFileExecution;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class MigrationStatusResolverTest {

  private final MigrationStatusResolver resolver = new MigrationStatusResolver();

  @ParameterizedTest
  @MethodSource("fileOutcomes")
  void givenFileStatusWhenResolveTransferStatusThenUsesTransferOutcome(
    MigrationFileExecutionStatus fileStatus, MigrationExecutionStatus expected
  ) {
    MigrationExecution execution = execution(MigrationFileType.ORGANIZATIONS);

    MigrationExecutionStatus result = resolver.resolveTransferStatus(execution,
      List.of(file(MigrationFileType.ORGANIZATIONS, fileStatus)));

    assertEquals(expected, result);
  }

  @Test
  void givenCompletedAndWarningFilesWhenResolveTransferStatusThenReturnsWarning() {
    MigrationExecution execution = execution(MigrationFileType.ORGANIZATIONS, MigrationFileType.ORG_SIL_SERVICES);

    MigrationExecutionStatus result = resolver.resolveTransferStatus(execution, List.of(
      file(MigrationFileType.ORGANIZATIONS, MigrationFileExecutionStatus.COMPLETED),
      file(MigrationFileType.ORG_SIL_SERVICES, MigrationFileExecutionStatus.WARNING)));

    assertEquals(MigrationExecutionStatus.WARNING, result);
  }

  @ParameterizedTest
  @MethodSource("incompleteSelections")
  void givenIncompleteSelectionWhenResolveTransferStatusThenDoesNotReportCompletion(List<MigrationFileExecution> files) {
    MigrationExecution execution = execution(MigrationFileType.ORGANIZATIONS, MigrationFileType.ORG_SIL_SERVICES);

    assertFalse(resolver.hasAllSelectedFiles(execution, files));
    assertEquals(MigrationExecutionStatus.RUNNING, resolver.resolveTransferStatus(execution, files));
  }

  @Test
  void givenDuplicateSelectedTypesWhenResolveTransferStatusThenDoesNotReportCompletion() {
    MigrationExecution execution = execution(MigrationFileType.ORGANIZATIONS, MigrationFileType.ORGANIZATIONS);
    MigrationFileExecution file = file(MigrationFileType.ORGANIZATIONS, MigrationFileExecutionStatus.COMPLETED);

    assertFalse(resolver.hasAllSelectedFiles(execution, List.of(file, file)));
    assertEquals(MigrationExecutionStatus.RUNNING, resolver.resolveTransferStatus(execution, List.of(file, file)));
  }

  @Test
  void givenFailedMigrationWithMissingFilesWhenResolveTransferStatusThenKeepsFailure() {
    MigrationExecution execution = execution(MigrationFileType.ORGANIZATIONS);
    execution.setStatus(MigrationExecutionStatus.FAILED);

    assertEquals(MigrationExecutionStatus.FAILED, resolver.resolveTransferStatus(execution, List.of()));
  }

  @Test
  void givenExportActionWhenResolveTransferStatusThenDoesNotReportTransferCompletion() {
    MigrationExecution execution = execution(MigrationFileType.ORGANIZATIONS);
    execution.setAction(MigrationAction.EXPORT);

    assertEquals(MigrationExecutionStatus.RUNNING, resolver.resolveTransferStatus(execution,
      List.of(file(MigrationFileType.ORGANIZATIONS, MigrationFileExecutionStatus.COMPLETED))));
  }

  private static Stream<Arguments> fileOutcomes() {
    return Stream.of(
      Arguments.of(MigrationFileExecutionStatus.EXPORTED, MigrationExecutionStatus.RUNNING),
      Arguments.of(MigrationFileExecutionStatus.TRANSFERING, MigrationExecutionStatus.RUNNING),
      Arguments.of(MigrationFileExecutionStatus.WAITING_PU, MigrationExecutionStatus.RUNNING),
      Arguments.of(MigrationFileExecutionStatus.FAILED, MigrationExecutionStatus.FAILED),
      Arguments.of(MigrationFileExecutionStatus.COMPLETED, MigrationExecutionStatus.COMPLETED),
      Arguments.of(MigrationFileExecutionStatus.WARNING, MigrationExecutionStatus.WARNING));
  }

  private static Stream<Arguments> incompleteSelections() {
    MigrationFileExecution organization = file(MigrationFileType.ORGANIZATIONS, MigrationFileExecutionStatus.COMPLETED);
    MigrationFileExecution service = file(MigrationFileType.ORG_SIL_SERVICES, MigrationFileExecutionStatus.COMPLETED);
    MigrationFileExecution unexpected = file(MigrationFileType.DEBT_POSITIONS, MigrationFileExecutionStatus.COMPLETED);
    return Stream.of(
      Arguments.of(List.of()),
      Arguments.of(List.of(organization)),
      Arguments.of(List.of(organization, organization)),
      Arguments.of(List.of(organization, unexpected)),
      Arguments.of(List.of(organization, service, unexpected)));
  }

  private static MigrationExecution execution(MigrationFileType... fileTypes) {
    return MigrationExecution.builder().action(MigrationAction.TRANSFER)
      .status(MigrationExecutionStatus.RUNNING).fileTypes(List.of(fileTypes)).build();
  }

  private static MigrationFileExecution file(MigrationFileType fileType, MigrationFileExecutionStatus status) {
    return MigrationFileExecution.builder().fileType(fileType).status(status).build();
  }
}
