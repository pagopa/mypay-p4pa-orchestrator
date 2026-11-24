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
import java.util.HashSet;
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
  private static final Map<MigrationFileExecutionStatus, Set<MigrationFileExecutionStatus>> TRANSITIONS =
    Map.of(WAITING_PU, Set.of(COMPLETED, WARNING, FAILED));

  public MigrationFileExecutionTransferOutcomeValidator() {
    super(TRANSITIONS);
  }

  public void validatePolling(MigrationFileExecution file) {
    requireStatus(file, WAITING_PU);
    if (file.getUploadId() == null || file.getUploadId() <= 0 || file.getUploadCompletedAt() == null) {
      throw invalid("Transfer outcome requires a positive uploadId and uploadCompletedAt");
    }
  }

  // Generated @Nonnull getters can return null for incomplete PU responses.
  @SuppressWarnings("java:S2589")
  public void validateUpload(MigrationFileExecution file, Long uploadId, Uploads upload) {
    checkImmutableField(file, MigrationFileExecution.Fields.uploadId, file.getUploadId(), uploadId);
    if (upload == null || upload.getStatus() == null || !Objects.equals(uploadId, upload.getUploadId())) {
      throw invalid("PU returned a missing status or a different upload ID for upload " + uploadId);
    }
    Integer total = upload.getNumTotalFiles();
    Integer processed = upload.getNumCorrectlyProcessedFiles();
    if ((total != null && total < 0) || (processed != null && processed < 0)
      || (total != null && processed != null && processed > total)) {
      throw invalid("PU returned inconsistent file counts for upload " + uploadId);
    }
  }

  // Keep null checks for malformed payloads despite the generated @Nonnull contracts.
  @SuppressWarnings("java:S2589")
  public void validateDetails(Long uploadId, List<UploadDetails> details) {
    Set<Long> identifiers = new HashSet<>();
    for (UploadDetails detail : details) {
      if (detail == null || !Objects.equals(uploadId, detail.getUploadId())
        || detail.getUploadDetailId() == null || detail.getUploadDetailId() <= 0
        || !StringUtils.hasText(detail.getFileName()) || detail.getStatus() == null
        || detail.getFileSize() == null || detail.getFileSize() < 0
        || !identifiers.add(detail.getUploadDetailId())) {
        throw invalid("PU returned invalid or duplicate details for upload " + uploadId);
      }
    }
  }

  public void validateRecordTransferOutcome(MigrationFileExecution file, Uploads upload, MigrationFileExecutionStatus target) {
    validatePolling(file);
    if ((file.getUploadStatus() == UploadsStatusEnum.PROCESSING && upload.getStatus() == UploadsStatusEnum.UPLOADED)
      || (file.getUploadStatus() == UploadsStatusEnum.COMPLETED
        && (upload.getStatus() == UploadsStatusEnum.UPLOADED || upload.getStatus() == UploadsStatusEnum.PROCESSING))) {
      throw new ConflictException("TRANSFER_OUTCOME_CONFLICT", "PU upload status regressed for file " + file.getId());
    }
    if (target != WAITING_PU) {
      requireTransition(file, target);
    }
  }

  public void validateTerminalReplay(MigrationFileExecution file, Uploads upload,
                                     MigrationFileExecutionStatus target, boolean completeSnapshot) {
    if (target != file.getStatus() || upload.getStatus() != file.getUploadStatus()
      || (!completeSnapshot && target != FAILED)) {
      throw new ConflictException("TRANSFER_OUTCOME_CONFLICT", "Cannot replace terminal transfer outcome for file " + file.getId());
    }
  }

  public void validateMarkFailed(MigrationFileExecution file, Long uploadId) {
    if (!Objects.equals(uploadId, file.getUploadId())) {
      throw new ConflictException("TRANSFER_OUTCOME_CONFLICT", "Upload changed for file " + file.getId());
    }
    if (!isTerminal(file)) {
      requireTransition(file, FAILED);
    }
  }

  public void validateDetailCount(Uploads upload, int detailCount) {
    if (upload.getNumTotalFiles() != null && detailCount > upload.getNumTotalFiles()) {
      throw invalid("PU returned more details than expected");
    }
  }

  public boolean isTerminal(MigrationFileExecution file) {
    return file.getStatus() == COMPLETED || file.getStatus() == WARNING || file.getStatus() == FAILED;
  }

  public void validateDetailUpdate(MigrationFileExecution file, MigrationFileExecutionDetail original,
                                   UploadDetails updated) {
    if (original == null) {
      throw invalid("PU returned a file not present in the export: " + updated.getFileName());
    }
    boolean terminalFile = isTerminal(file);
    if ((terminalFile || original.getUploadDetailId() != null)
      && !Objects.equals(original.getUploadDetailId(), updated.getUploadDetailId())) {
      throw new ConflictException("TRANSFER_OUTCOME_CONFLICT", "PU changed upload detail ID for " + original.getFileName());
    }
    if (!Objects.equals(StringUtils.getFilename(original.getFileName()), updated.getFileName())
      || ((terminalFile || original.getUploadDetailId() != null || !Long.valueOf(0L).equals(original.getFileSize()))
        && !Objects.equals(original.getFileSize(), updated.getFileSize()))) {
      throw invalid("PU changed immutable metadata for detail " + original.getUploadDetailId());
    }
    Integer total = toRowCount(updated.getNumTotalRows(), "numTotalRows");
    Integer accepted = toRowCount(updated.getNumCorrectlyImportedRows(), "numCorrectlyImportedRows");
    Integer mergedTotal = total == null ? original.getNumTotalRows() : total;
    Integer mergedAccepted = accepted == null ? original.getNumCorrectlyImportedRows() : accepted;
    if (mergedTotal != null && mergedAccepted != null && mergedAccepted > mergedTotal) {
      throw invalid("numCorrectlyImportedRows exceeds numTotalRows for upload " + updated.getUploadId());
    }
    if ((terminalFile || original.getStatus() != MigrationFileExecutionDetailStatus.WAITING_PU)
      && (!hasSameDetailStatus(original.getStatus(), updated.getStatus(), file.getStatus() == FAILED)
        || changedValue(original.getNumTotalRows(), total, terminalFile)
        || changedValue(original.getNumCorrectlyImportedRows(), accepted, terminalFile)
        || changedValue(original.getErrorDescription(), updated.getErrorDescription(), terminalFile)
        || changedValue(original.getDiscardFileName(), updated.getDiscardFileName(), terminalFile))) {
      throw new ConflictException("TRANSFER_OUTCOME_CONFLICT", "PU changed terminal detail " + original.getUploadDetailId());
    }
  }

  private Integer toRowCount(Long value, String fieldName) {
    if (value == null) {
      return null;
    }
    if (value < 0 || value > Integer.MAX_VALUE) {
      throw invalid(fieldName + " must be between 0 and " + Integer.MAX_VALUE);
    }
    return value.intValue();
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
