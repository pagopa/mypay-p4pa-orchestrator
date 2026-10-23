package it.gov.pagopa.mypay2pu.orchestrator.service.mapper;

import it.gov.pagopa.mypay2pu.orchestrator.config.json.LocalDateTimeToOffsetDateTimeSerializer;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.ExportFileStatusDto;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationAction;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationFileExecutionDetailStatus;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationExportPhaseDto;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationStatusDto;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationTransferPhaseDto;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.TransferFileStatusDto;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationExecution;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationFileExecution;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationFileExecutionDetail;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
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

  private static final Set<MigrationFileExecutionDetailStatus> TERMINAL_DETAIL_STATUSES = EnumSet.of(
    MigrationFileExecutionDetailStatus.COMPLETED,
    MigrationFileExecutionDetailStatus.PARTIAL,
    MigrationFileExecutionDetailStatus.FAILED
  );

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
      .createdAt(toOffsetDateTime(execution.getCreationDate()))
      .updatedAt(toOffsetDateTime(execution.getUpdateDate()))
      .errorCode(execution.getErrorCode())
      .errorMsg(execution.getErrorMsg())
      .exportPhase(new MigrationExportPhaseDto()
        .status(execution.getStatus())
        .files(fileExecutions.stream()
          .map(fileExecution -> toExportFileStatusDto(
            fileExecution,
            detailsByFileExecutionId.getOrDefault(fileExecution.getId(), List.of())
          ))
          .toList()))
      .transferPhase(new MigrationTransferPhaseDto()
            .status(execution.getStatus())
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
      .status(fileExecution.getStatus())
      .extractionId(fileExecution.getExtractionId())
      .filePath(fileExecution.getFilePath())
      .recordCount(metrics.recordCount())
      .errorCode(fileExecution.getExtractionErrorCode())
      .errorMsg(fileExecution.getExtractionErrorMsg())
      .extractionStartedAt(toOffsetDateTime(fileExecution.getExtractionStartedAt()))
      .extractedAt(toOffsetDateTime(fileExecution.getExtractionCompletedAt()));
  }

  public TransferFileStatusDto toTransferFileStatusDto(
    MigrationFileExecution fileExecution,
    List<MigrationFileExecutionDetail> details
  ) {
    FileMetrics metrics = calculateMetrics(fileExecution, details);
    return new TransferFileStatusDto()
      .fileType(fileExecution.getFileType())
      .status(fileExecution.getStatus())
      .uploadId(fileExecution.getUploadId())
      .uploadStatus(fileExecution.getUploadStatus() == null ? null : fileExecution.getUploadStatus().name())
      .recordsAccepted(metrics.recordsAccepted())
      .recordsRejected(metrics.recordsRejected())
      .errorCode(fileExecution.getUploadErrorCode())
      .errorMsg(fileExecution.getUploadErrorMsg())
      .uploadedAt(toOffsetDateTime(fileExecution.getUploadCompletedAt()))
      .evidenceCollectedAt(toOffsetDateTime(latestTerminalDetailUpdateDate(details)));
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

  private LocalDateTime latestTerminalDetailUpdateDate(List<MigrationFileExecutionDetail> details) {
    return details.stream()
      .filter(detail -> TERMINAL_DETAIL_STATUSES.contains(detail.getStatus()))
      .map(MigrationFileExecutionDetail::getUpdateDate)
      .filter(Objects::nonNull)
      .max(Comparator.naturalOrder())
      .orElse(null);
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

  private OffsetDateTime toOffsetDateTime(LocalDateTime value) {
    return value == null ? null : LocalDateTimeToOffsetDateTimeSerializer.convertToOffsetDateTime(value);
  }

  private record FileMetrics(Long recordCount, Long recordsAccepted, Long recordsRejected) {
  }
}
