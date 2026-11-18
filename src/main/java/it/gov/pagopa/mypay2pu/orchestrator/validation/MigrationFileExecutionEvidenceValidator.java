package it.gov.pagopa.mypay2pu.orchestrator.validation;

import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationFileExecutionStatus;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationFileExecutionDetailStatus;
import it.gov.pagopa.mypay2pu.orchestrator.exception.ConflictException;
import it.gov.pagopa.mypay2pu.orchestrator.exception.InvalidValueException;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationFileExecution;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationFileExecutionDetail;
import it.gov.pagopa.pu.migration.dto.generated.UploadDetails;
import it.gov.pagopa.pu.migration.dto.generated.Uploads;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

import static it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationFileExecutionStatus.*;

@Component
public class MigrationFileExecutionEvidenceValidator extends BaseMigrationFileExecutionValidator {

  public MigrationFileExecutionEvidenceValidator() {
    super(Map.of(WAITING_PU, Set.of(COMPLETED, WARNING, FAILED)));
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
    for (UploadDetails detail : details) {
      if (detail == null || !Objects.equals(uploadId, detail.getUploadId())
        || detail.getUploadDetailId() == null || detail.getUploadDetailId() <= 0
        || !identifiers.add(detail.getUploadDetailId())) {
        throw invalid("PU returned invalid or duplicate details for upload " + uploadId);
      }
    }
  }

  public void validateTransition(MigrationFileExecution file, MigrationFileExecutionStatus target) {
    requireTransition(file, target);
  }

  public void validateDetailUpdate(MigrationFileExecutionDetail original, MigrationFileExecutionDetail updated) {
    if (!Objects.equals(original.getFileName(), updated.getFileName())
      || !Objects.equals(original.getFileSize(), updated.getFileSize())) {
      throw invalid("PU changed immutable metadata for detail " + original.getUploadDetailId());
    }
    if (original.getStatus() != MigrationFileExecutionDetailStatus.WAITING_PU
      && (original.getStatus() != updated.getStatus()
        || changedValue(original.getNumTotalRows(), updated.getNumTotalRows())
        || changedValue(original.getNumCorrectlyImportedRows(), updated.getNumCorrectlyImportedRows())
        || changedValue(original.getErrorDescription(), updated.getErrorDescription())
        || changedValue(original.getDiscardFileName(), updated.getDiscardFileName()))) {
      throw new ConflictException("EVIDENCE_CONFLICT", "PU changed terminal detail " + original.getUploadDetailId());
    }
  }

  private boolean changedValue(Object original, Object updated) {
    return original != null && !Objects.equals(original, updated);
  }

  private InvalidValueException invalid(String message) {
    return new InvalidValueException("EVIDENCE_INVALID_RESPONSE", message);
  }
}
