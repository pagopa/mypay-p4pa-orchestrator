package it.gov.pagopa.mypay2pu.orchestrator.service.mapper;

import it.gov.pagopa.mypay2pu.orchestrator.config.json.LocalDateTimeToOffsetDateTimeSerializer;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.ExportFileStatusDto;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationAction;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationExecutionStatus;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationFileExecutionStatus;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationFileExecutionDetailStatus;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationExportPhaseDto;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationStatusDto;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationTransferPhaseDto;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.TransferFileStatusDto;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationExecution;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationFileExecution;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationFileExecutionDetail;
import it.gov.pagopa.mypay2pu.orchestrator.service.MigrationStatusResolver;
import it.gov.pagopa.pu.migration.dto.generated.UploadsStatusEnum;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.stereotype.Component;

@Component
public class MigrationStatusMapper {

  private final MigrationStatusResolver migrationStatusResolver;

  private static final Set<MigrationFileExecutionDetailStatus> TERMINAL_DETAIL_STATUSES = EnumSet.of(
    MigrationFileExecutionDetailStatus.COMPLETED,
    MigrationFileExecutionDetailStatus.PARTIAL,
    MigrationFileExecutionDetailStatus.FAILED
  );

  public MigrationStatusMapper(MigrationStatusResolver migrationStatusResolver) {
    this.migrationStatusResolver = migrationStatusResolver;
  }

  public MigrationStatusDto toMigrationStatusDto(
    MigrationExecution execution,
    List<MigrationFileExecution> fileExecutions,
    Map<UUID, List<MigrationFileExecutionDetail>> detailsByFileExecutionId
  ) {
    return new MigrationStatusDto()
      .migrationId(execution.getId())
      .action(execution.getAction())
      .status(execution.getStatus())
      .orchestrationMode(execution.getOrchestrationMode())
      .ipaCodes(execution.getIpacodes())
      .createdAt(LocalDateTimeToOffsetDateTimeSerializer.convertToOffsetDateTime(execution.getCreationDate()))
      .updatedAt(LocalDateTimeToOffsetDateTimeSerializer.convertToOffsetDateTime(execution.getUpdateDate()))
      .errorCode(execution.getErrorCode())
      .errorMsg(execution.getErrorMsg())
      .exportPhase(new MigrationExportPhaseDto()
        .status(exportPhaseStatus(execution, fileExecutions))
        .files(fileExecutions.stream()
          .map(fileExecution -> toExportFileStatusDto(
            fileExecution,
            detailsByFileExecutionId.getOrDefault(fileExecution.getId(), List.of())
          ))
          .toList()))
      .transferPhase(new MigrationTransferPhaseDto()
            .status(migrationStatusResolver.resolveTransferStatus(execution, fileExecutions))
            .files(execution.getAction() == MigrationAction.EXPORT
              ? List.of()
              : fileExecutions.stream()
                .map(fileExecution -> toTransferFileStatusDto(
                  fileExecution,
                  detailsByFileExecutionId.getOrDefault(fileExecution.getId(), List.of())
                ))
                .toList()));
  }

  public ExportFileStatusDto toExportFileStatusDto(
    MigrationFileExecution fileExecution,
    List<MigrationFileExecutionDetail> details
  ) {
    FileMetrics metrics = calculateMetrics(fileExecution, details);
    return new ExportFileStatusDto()
      .fileType(fileExecution.getFileType())
      .status(exportStatus(fileExecution))
      .extractionId(fileExecution.getExtractionId())
      .filePath(fileExecution.getFilePath())
      .recordCount(metrics.recordCount())
      .errorCode(fileExecution.getExtractionErrorCode())
      .errorMsg(fileExecution.getExtractionErrorMsg())
      .extractionStartedAt(LocalDateTimeToOffsetDateTimeSerializer.convertToOffsetDateTime(fileExecution.getExtractionStartedAt()))
      .extractedAt(LocalDateTimeToOffsetDateTimeSerializer.convertToOffsetDateTime(fileExecution.getExtractionCompletedAt()));
  }

  public TransferFileStatusDto toTransferFileStatusDto(
    MigrationFileExecution fileExecution,
    List<MigrationFileExecutionDetail> details
  ) {
    FileMetrics metrics = calculateMetrics(fileExecution, details);
    boolean finalTransferOutcome = hasFinalTransferOutcome(fileExecution, details);
    return new TransferFileStatusDto()
      .fileType(fileExecution.getFileType())
      .status(fileExecution.getStatus())
      .uploadId(fileExecution.getUploadId())
      .uploadStatus(fileExecution.getUploadStatus() == null ? null : fileExecution.getUploadStatus().name())
      .recordsAccepted(finalTransferOutcome ? metrics.recordsAccepted() : null)
      .recordsRejected(finalTransferOutcome ? metrics.recordsRejected() : null)
      .errorCode(fileExecution.getUploadErrorCode())
      .errorMsg(fileExecution.getUploadErrorMsg())
      .uploadedAt(LocalDateTimeToOffsetDateTimeSerializer.convertToOffsetDateTime(fileExecution.getUploadCompletedAt()))
      .transferOutcomeCollectedAt(finalTransferOutcome
        ? LocalDateTimeToOffsetDateTimeSerializer.convertToOffsetDateTime(transferOutcomeCollectedAt(details))
        : null);
  }

  private MigrationFileExecutionStatus exportStatus(MigrationFileExecution file) {
    return switch (file.getStatus()) {
      case TRANSFERING, WAITING_PU, COMPLETED, WARNING -> MigrationFileExecutionStatus.EXPORTED;
      case FAILED -> file.getExtractionCompletedAt() == null
        ? MigrationFileExecutionStatus.FAILED : MigrationFileExecutionStatus.EXPORTED;
      default -> file.getStatus();
    };
  }

  private MigrationExecutionStatus exportPhaseStatus(MigrationExecution execution, List<MigrationFileExecution> files) {
    if (files.stream().anyMatch(file -> exportStatus(file) == MigrationFileExecutionStatus.FAILED)) {
      return MigrationExecutionStatus.FAILED;
    }
    if (migrationStatusResolver.hasAllSelectedFiles(execution, files)
      && files.stream().allMatch(file -> exportStatus(file) == MigrationFileExecutionStatus.EXPORTED)) {
      return MigrationExecutionStatus.COMPLETED;
    }
    return execution.getStatus() == MigrationExecutionStatus.FAILED
      ? MigrationExecutionStatus.FAILED : MigrationExecutionStatus.RUNNING;
  }

  private FileMetrics calculateMetrics(
    MigrationFileExecution fileExecution,
    List<MigrationFileExecutionDetail> details
  ) {
    Long recordCount = sumIfComplete(details, MigrationFileExecutionDetail::getNumTotalRows);
    Long recordsAccepted = sumIfComplete(details, MigrationFileExecutionDetail::getNumCorrectlyImportedRows);
    Long recordsRejected = null;
    if (recordCount != null && recordsAccepted != null) {
      if (recordsAccepted > recordCount) {
        throw new IllegalStateException(
          "Accepted row count exceeds total row count for file execution %s".formatted(fileExecution.getId())
        );
      }
      recordsRejected = recordCount - recordsAccepted;
    }
    return new FileMetrics(recordCount, recordsAccepted, recordsRejected);
  }

  private LocalDateTime transferOutcomeCollectedAt(List<MigrationFileExecutionDetail> details) {
    return details.stream()
      .map(MigrationFileExecutionDetail::getUpdateDate)
      .filter(Objects::nonNull)
      .max(Comparator.naturalOrder())
      .orElse(null);
  }

  private boolean hasFinalTransferOutcome(MigrationFileExecution file, List<MigrationFileExecutionDetail> details) {
    return (file.getStatus() == MigrationFileExecutionStatus.COMPLETED
      || file.getStatus() == MigrationFileExecutionStatus.WARNING
      || file.getStatus() == MigrationFileExecutionStatus.FAILED)
      && (file.getUploadStatus() == UploadsStatusEnum.COMPLETED || file.getUploadStatus() == UploadsStatusEnum.ERROR)
      && !details.isEmpty()
      && details.stream().allMatch(detail -> TERMINAL_DETAIL_STATUSES.contains(detail.getStatus())
        && detail.getNumTotalRows() != null && detail.getNumCorrectlyImportedRows() != null);
  }

  private Long sumIfComplete(
    List<MigrationFileExecutionDetail> details,
    Function<MigrationFileExecutionDetail, ? extends Number> valueExtractor
  ) {
    if (details.isEmpty() || details.stream().anyMatch(detail -> valueExtractor.apply(detail) == null)) {
      return null;
    }
    return details.stream()
      .map(valueExtractor)
      .mapToLong(Number::longValue)
      .sum();
  }

  private record FileMetrics(Long recordCount, Long recordsAccepted, Long recordsRejected) {
  }
}
