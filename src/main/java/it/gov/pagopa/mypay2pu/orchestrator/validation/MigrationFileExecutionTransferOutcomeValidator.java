package it.gov.pagopa.mypay2pu.orchestrator.validation;

import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationFileExecutionDetailStatus;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationFileExecutionStatus;
import it.gov.pagopa.mypay2pu.orchestrator.exception.ConflictException;
import it.gov.pagopa.mypay2pu.orchestrator.exception.InvalidValueException;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationFileExecution;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationFileExecutionDetail;
import it.gov.pagopa.pu.migration.dto.generated.IngestionFlowFileStatus;
import it.gov.pagopa.pu.migration.dto.generated.UploadDetails;
import it.gov.pagopa.pu.migration.dto.generated.Uploads;
import it.gov.pagopa.pu.migration.dto.generated.UploadsStatusEnum;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import static it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationFileExecutionStatus.COMPLETED;
import static it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationFileExecutionStatus.FAILED;
import static it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationFileExecutionStatus.WAITING_PU;
import static it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationFileExecutionStatus.WARNING;

@Component
public class MigrationFileExecutionTransferOutcomeValidator extends BaseMigrationFileExecutionValidator {

  private static final String INVALID_RESPONSE = "TRANSFER_OUTCOME_INVALID_RESPONSE";
  private static final Set<MigrationFileExecutionStatus> TERMINAL_STATUSES = Set.of(COMPLETED, WARNING, FAILED);
  private static final Map<MigrationFileExecutionStatus, Set<MigrationFileExecutionStatus>> TRANSITIONS =
    Map.of(WAITING_PU, TERMINAL_STATUSES);

  public MigrationFileExecutionTransferOutcomeValidator() {
    super(TRANSITIONS);
  }

  public void validatePolling(MigrationFileExecution file) {
    requireStatus(file, WAITING_PU);
    if (file.getUploadId() == null || file.getUploadCompletedAt() == null) {
      throw invalid("Transfer outcome requires a positive uploadId and uploadCompletedAt");
    }
  }

  // Generated @Nonnull getters can return null for incomplete PU responses.
  @SuppressWarnings("java:S2589")
  public void validateUpload(MigrationFileExecution file, Uploads upload) {
    Long uploadId = file.getUploadId();
    if (upload == null || upload.getStatus() == null || !Objects.equals(uploadId, upload.getUploadId())) {
      throw invalid("PU returned a missing status or a different upload ID for upload " + uploadId);
    }
  }

  // Keep null checks for malformed payloads despite the generated @Nonnull contracts.
  @SuppressWarnings("java:S2589")
  public void validateDetails(Long uploadId, List<UploadDetails> details) {
    for (UploadDetails detail : details) {
      if (detail == null || !Objects.equals(uploadId, detail.getUploadId())
        || detail.getUploadDetailId() == null
        || !StringUtils.hasText(detail.getFileName()) || detail.getStatus() == null
        || detail.getFileSize() == null) {
        throw invalid("PU returned invalid details for upload " + uploadId);
      }
    }
  }

  public void validateRecordTransferOutcome(MigrationFileExecution file, Uploads upload) {
    validatePolling(file);
    if ((file.getUploadStatus() == UploadsStatusEnum.PROCESSING && upload.getStatus() == UploadsStatusEnum.UPLOADED)
      || (file.getUploadStatus() == UploadsStatusEnum.COMPLETED
        && (upload.getStatus() == UploadsStatusEnum.UPLOADED || upload.getStatus() == UploadsStatusEnum.PROCESSING))) {
      throw new ConflictException("TRANSFER_OUTCOME_CONFLICT", "PU upload status regressed for file " + file.getId());
    }
  }

  public void validateTerminalReplay(MigrationFileExecution file, Uploads upload,
                                     MigrationFileExecutionStatus target, boolean completeSnapshot) {
    if (target != file.getStatus() || upload.getStatus() != file.getUploadStatus()
      || (!completeSnapshot && target != FAILED)) {
      throw new ConflictException("TRANSFER_OUTCOME_CONFLICT", "Cannot replace terminal transfer outcome for file " + file.getId());
    }
  }

  public void validateMarkFailed(MigrationFileExecution file) {
    if (!isCompleted(file)) {
      requireTransition(file, FAILED);
    }
  }

  public boolean isCompleted(MigrationFileExecution file) {
    return file.getStatus() != null && TERMINAL_STATUSES.contains(file.getStatus());
  }

  public void validateDetailUpdate(MigrationFileExecution file, MigrationFileExecutionDetail original,
                                   UploadDetails updated) {
    boolean terminalFile = isCompleted(file);
    if ((terminalFile || original.getUploadDetailId() != null)
      && !Objects.equals(original.getUploadDetailId(), updated.getUploadDetailId())) {
      throw new ConflictException("TRANSFER_OUTCOME_CONFLICT", "PU changed upload detail ID for " + original.getFileName());
    }
    if ((terminalFile || original.getUploadDetailId() != null || !Long.valueOf(0L).equals(original.getFileSize()))
      && !Objects.equals(original.getFileSize(), updated.getFileSize())) {
      throw invalid("PU changed immutable metadata for detail " + original.getUploadDetailId());
    }
    if ((terminalFile || original.getStatus() != MigrationFileExecutionDetailStatus.WAITING_PU)
      && (!hasSameDetailStatus(original.getStatus(), updated.getStatus(), file.getStatus() == FAILED)
        || changedValue(original.getNumTotalRows(), updated.getNumTotalRows(), terminalFile)
        || changedValue(original.getNumCorrectlyImportedRows(), updated.getNumCorrectlyImportedRows(), terminalFile)
        || changedValue(original.getErrorDescription(), updated.getErrorDescription(), terminalFile)
        || changedValue(original.getDiscardFileName(), updated.getDiscardFileName(), terminalFile))) {
      throw new ConflictException("TRANSFER_OUTCOME_CONFLICT", "PU changed terminal detail " + original.getUploadDetailId());
    }
  }

  private boolean hasSameDetailStatus(MigrationFileExecutionDetailStatus original,
                                      IngestionFlowFileStatus updated, boolean failedFile) {
    return switch (original) {
      case WAITING_PU -> isWaitingForPu(updated);
      case COMPLETED -> updated == IngestionFlowFileStatus.COMPLETED;
      case PARTIAL -> updated == IngestionFlowFileStatus.WARNING;
      // An upload failure also marks unfinished details as FAILED locally.
      case FAILED -> updated == IngestionFlowFileStatus.ERROR || (failedFile && isWaitingForPu(updated));
      default -> false;
    };
  }

  private boolean isWaitingForPu(IngestionFlowFileStatus status) {
    return status == IngestionFlowFileStatus.UPLOADED
      || status == IngestionFlowFileStatus.PROCESSING
      || status == IngestionFlowFileStatus.WAITING_FILE;
  }

  private boolean changedValue(Object original, Object updated, boolean terminalFile) {
    return updated != null && (terminalFile || original != null) && !Objects.equals(original, updated);
  }

  private InvalidValueException invalid(String message) {
    return new InvalidValueException(INVALID_RESPONSE, message);
  }
}
