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

import static it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationFileExecutionStatus.COMPLETED;
import static it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationFileExecutionStatus.FAILED;
import static it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationFileExecutionStatus.WAITING_PU;
import static it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationFileExecutionStatus.WARNING;

@Component
public class MigrationFileExecutionEvidenceValidator extends BaseMigrationFileExecutionValidator {

  private static final String INVALID_RESPONSE = "EVIDENCE_INVALID_RESPONSE";
  private static final Map<MigrationFileExecutionStatus, Set<MigrationFileExecutionStatus>> TRANSITIONS =
    Map.of(WAITING_PU, Set.of(COMPLETED, WARNING, FAILED));

  public MigrationFileExecutionEvidenceValidator() {
    super(TRANSITIONS);
  }

  public void validatePolling(MigrationFileExecution file) {
    requireStatus(file, WAITING_PU);
    if (file.getUploadId() == null || file.getUploadId() <= 0 || file.getUploadCompletedAt() == null) {
      throw invalid("Evidence requires a positive uploadId and uploadCompletedAt");
    }
  }

  public void validateUpload(MigrationFileExecution file, Long uploadId, Uploads upload) {
    checkImmutableField(file, MigrationFileExecution.Fields.uploadId, file.getUploadId(), uploadId);
    if (upload == null || !Objects.equals(uploadId, upload.getUploadId())) {
      throw invalid("PU returned a missing status or a different upload ID for upload " + uploadId);
    }
    Integer total = upload.getNumTotalFiles();
    Integer processed = upload.getNumCorrectlyProcessedFiles();
    if ((total != null && total < 0) || (processed != null && processed < 0)
      || (total != null && processed != null && processed > total)) {
      throw invalid("PU returned inconsistent file counts for upload " + uploadId);
    }
  }

  public void validateDetails(Long uploadId, List<UploadDetails> details) {
    Set<Long> identifiers = new HashSet<>();
    Set<String> fileNames = new HashSet<>();
    for (UploadDetails detail : details) {
      if (detail == null || !Objects.equals(uploadId, detail.getUploadId())
        || detail.getUploadDetailId() == null || detail.getUploadDetailId() <= 0
        || !identifiers.add(detail.getUploadDetailId())
        || !fileNames.add(detail.getFileName())) {
        throw invalid("PU returned invalid or duplicate details for upload " + uploadId);
      }
    }
  }

  public void validateRecordEvidence(MigrationFileExecution file, Uploads upload, MigrationFileExecutionStatus target) {
    validatePolling(file);
    if ((file.getUploadStatus() == UploadsStatusEnum.PROCESSING && upload.getStatus() == UploadsStatusEnum.UPLOADED)
      || (file.getUploadStatus() == UploadsStatusEnum.COMPLETED
        && (upload.getStatus() == UploadsStatusEnum.UPLOADED || upload.getStatus() == UploadsStatusEnum.PROCESSING))) {
      throw new ConflictException("EVIDENCE_CONFLICT", "PU upload status regressed for file " + file.getId());
    }
    if (target != WAITING_PU) {
      requireTransition(file, target);
    }
  }

  public void validateTerminalReplay(MigrationFileExecution file, Uploads upload,
                                     MigrationFileExecutionStatus target, boolean completeSnapshot) {
    if (target != file.getStatus() || upload.getStatus() != file.getUploadStatus() || !completeSnapshot) {
      throw new ConflictException("EVIDENCE_CONFLICT", "Cannot replace terminal evidence for file " + file.getId());
    }
  }

  public void validateMarkFailed(MigrationFileExecution file, Long uploadId) {
    if (!Objects.equals(uploadId, file.getUploadId())) {
      throw new ConflictException("EVIDENCE_CONFLICT", "Upload changed for file " + file.getId());
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
      throw new ConflictException("EVIDENCE_CONFLICT", "PU changed upload detail ID for " + original.getFileName());
    }
    if (!Objects.equals(original.getFileName(), updated.getFileName())
      || !Objects.equals(original.getFileSize(), updated.getFileSize())) {
      throw invalid("PU changed immutable metadata for detail " + original.getUploadDetailId());
    }
    Integer total = toRowCount(updated.getNumTotalRows(), "numTotalRows");
    Integer accepted = toRowCount(updated.getNumCorrectlyImportedRows(), "numCorrectlyImportedRows");
    if (total != null && accepted != null && accepted > total) {
      throw invalid("numCorrectlyImportedRows exceeds numTotalRows for upload " + updated.getUploadId());
    }
    if ((terminalFile || original.getStatus() != MigrationFileExecutionDetailStatus.WAITING_PU)
      && (!hasSameDetailStatus(original.getStatus(), updated.getStatus())
        || changedValue(original.getNumTotalRows(), total, terminalFile)
        || changedValue(original.getNumCorrectlyImportedRows(), accepted, terminalFile)
        || changedValue(original.getErrorDescription(), updated.getErrorDescription(), terminalFile)
        || changedValue(original.getDiscardFileName(), updated.getDiscardFileName(), terminalFile))) {
      throw new ConflictException("EVIDENCE_CONFLICT", "PU changed terminal detail " + original.getUploadDetailId());
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

  private boolean hasSameDetailStatus(MigrationFileExecutionDetailStatus original, IngestionFlowFileStatus updated) {
    return switch (original) {
      case WAITING_PU -> updated == IngestionFlowFileStatus.UPLOADED
        || updated == IngestionFlowFileStatus.PROCESSING || updated == IngestionFlowFileStatus.WAITING_FILE;
      case COMPLETED -> updated == IngestionFlowFileStatus.COMPLETED;
      case PARTIAL -> updated == IngestionFlowFileStatus.WARNING;
      case FAILED -> updated == IngestionFlowFileStatus.ERROR;
      default -> false;
    };
  }

  private boolean changedValue(Object original, Object updated, boolean terminalFile) {
    return (terminalFile || original != null) && !Objects.equals(original, updated);
  }

  private InvalidValueException invalid(String message) {
    return new InvalidValueException(INVALID_RESPONSE, message);
  }
}
