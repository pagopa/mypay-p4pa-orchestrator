package it.gov.pagopa.mypay2pu.orchestrator.validation;

import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationFileExecutionStatus;
import it.gov.pagopa.mypay2pu.orchestrator.exception.ConflictException;
import it.gov.pagopa.mypay2pu.orchestrator.exception.InvalidValueException;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationFileExecution;
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

  private InvalidValueException invalid(String message) {
    return new InvalidValueException(INVALID_RESPONSE, message);
  }

  public void validateUpload(MigrationFileExecution file, Uploads upload) {
    Long uploadId = file.getUploadId();
    if (!Objects.equals(uploadId, upload.getUploadId())) {
      throw invalid("PU returned a different upload ID for upload " + uploadId);
    }
  }

  public void validateDetails(Long uploadId, List<UploadDetails> details) {
    for (UploadDetails detail : details) {
      if (!Objects.equals(uploadId, detail.getUploadId())
        || detail.getUploadDetailId() == null
        || !StringUtils.hasText(detail.getFileName())) {
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
}
