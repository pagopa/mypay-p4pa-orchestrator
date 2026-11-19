package it.gov.pagopa.mypay2pu.orchestrator.service;

import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationFileExecutionDetailStatus;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationFileExecutionStatus;
import it.gov.pagopa.mypay2pu.orchestrator.exception.ConflictException;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationFileExecution;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationFileExecutionDetail;
import it.gov.pagopa.mypay2pu.orchestrator.repository.MigrationFileExecutionDetailRepository;
import it.gov.pagopa.mypay2pu.orchestrator.repository.MigrationFileExecutionRepository;
import it.gov.pagopa.mypay2pu.orchestrator.service.mapper.MigrationFileExecutionDetailMapper;
import it.gov.pagopa.mypay2pu.orchestrator.validation.MigrationFileExecutionEvidenceValidator;
import it.gov.pagopa.pu.migration.dto.generated.IngestionFlowFileStatus;
import it.gov.pagopa.pu.migration.dto.generated.UploadDetails;
import it.gov.pagopa.pu.migration.dto.generated.Uploads;
import it.gov.pagopa.pu.migration.dto.generated.UploadsStatusEnum;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import static it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationFileExecutionStatus.COMPLETED;
import static it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationFileExecutionStatus.FAILED;
import static it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationFileExecutionStatus.WAITING_PU;
import static it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationFileExecutionStatus.WARNING;

/** Applies correlated PU snapshots in short transactions, without remote calls. */
@Service
public class MigrationFileExecutionEvidenceService extends MigrationFileExecutionService {

  private final MigrationFileExecutionRepository fileRepository;
  private final MigrationFileExecutionDetailRepository detailRepository;
  private final MigrationFileExecutionDetailMapper detailMapper;
  private final MigrationFileExecutionEvidenceValidator validator;

  public MigrationFileExecutionEvidenceService(
    MigrationFileExecutionRepository fileRepository,
    MigrationFileExecutionDetailRepository detailRepository,
    MigrationFileExecutionDetailMapper detailMapper,
    MigrationFileExecutionEvidenceValidator validator
  ) {
    super(fileRepository);
    this.fileRepository = fileRepository;
    this.detailRepository = detailRepository;
    this.detailMapper = detailMapper;
    this.validator = validator;
  }

  @Transactional
  public MigrationFileExecution recordEvidence(UUID fileId, Long uploadId, Uploads upload,
                                                List<UploadDetails> uploadDetails) {
    MigrationFileExecution file = findRequiredForUpdate(fileId);
    validator.validateUpload(file, uploadId, upload);
    List<UploadDetails> snapshot = uploadDetails == null ? List.of() : uploadDetails;
    validator.validateDetails(uploadId, snapshot);
    List<MigrationFileExecutionDetail> existing = detailRepository.findAllByMigrationFileExecutionId(fileId);
    Map<String, MigrationFileExecutionDetail> byFileName = correlateDetails(file, snapshot, existing);
    boolean missingExisting = snapshot.size() != existing.size();
    MigrationFileExecutionStatus target = resolveOutcome(upload, snapshot, missingExisting);

    if (validator.isTerminal(file)) {
      validator.validateTerminalReplay(file, upload, target, !missingExisting);
      return file;
    }

    validator.validateRecordEvidence(file, upload, target);
    List<UploadDetails> terminalDetails = snapshot.stream()
      .filter(detail -> detail.getStatus() == IngestionFlowFileStatus.COMPLETED
        || detail.getStatus() == IngestionFlowFileStatus.WARNING
        || detail.getStatus() == IngestionFlowFileStatus.ERROR)
      .toList();
    terminalDetails.forEach(detail -> detailMapper.updateMigrationFileExecutionDetail(
      byFileName.get(detail.getFileName()), detail
    ));
    if (target == FAILED) {
      failWaitingDetails(existing);
    }
    if (!existing.isEmpty() && (!terminalDetails.isEmpty() || target == FAILED)) {
      detailRepository.saveAll(existing);
    }
    file.setUploadStatus(upload.getStatus());
    file.setStatus(target);
    if (target == FAILED) {
      file.setUploadErrorCode("EVIDENCE_PU_FAILED");
      file.setUploadErrorMsg(puError(upload, snapshot));
    }
    return fileRepository.save(file);
  }

  @Transactional
  public MigrationFileExecution markFailed(UUID fileId, Long uploadId, String errorCode, String message) {
    MigrationFileExecution file = findRequiredForUpdate(fileId);
    validator.validateMarkFailed(file, uploadId);
    if (validator.isTerminal(file)) {
      return file;
    }
    List<MigrationFileExecutionDetail> details = detailRepository.findAllByMigrationFileExecutionId(fileId);
    failWaitingDetails(details);
    if (!details.isEmpty()) {
      detailRepository.saveAll(details);
    }
    file.setStatus(FAILED);
    file.setUploadErrorCode(errorCode);
    file.setUploadErrorMsg(message);
    return fileRepository.save(file);
  }

  private Map<String, MigrationFileExecutionDetail> correlateDetails(
    MigrationFileExecution file,
    List<UploadDetails> snapshot,
    List<MigrationFileExecutionDetail> existing
  ) {
    Map<String, MigrationFileExecutionDetail> byFileName = new HashMap<>();
    Map<Long, MigrationFileExecutionDetail> byUploadDetailId = new HashMap<>();
    for (MigrationFileExecutionDetail detail : existing) {
      if (byFileName.put(detail.getFileName(), detail) != null
        || (detail.getUploadDetailId() != null
          && byUploadDetailId.put(detail.getUploadDetailId(), detail) != null)) {
        throw new ConflictException("EVIDENCE_CONFLICT", "Duplicate persisted details for file " + file.getId());
      }
    }
    for (UploadDetails detail : snapshot) {
      MigrationFileExecutionDetail original = byUploadDetailId.get(detail.getUploadDetailId());
      if (original == null) {
        original = byFileName.get(detail.getFileName());
      }
      validator.validateDetailUpdate(file, original, detail);
    }
    return byFileName;
  }

  private void failWaitingDetails(List<MigrationFileExecutionDetail> details) {
    details.stream().filter(detail -> detail.getStatus() == MigrationFileExecutionDetailStatus.WAITING_PU)
      .forEach(detail -> detail.setStatus(MigrationFileExecutionDetailStatus.FAILED));
  }

  private MigrationFileExecutionStatus resolveOutcome(Uploads upload,
                                                       List<UploadDetails> details,
                                                       boolean missingExisting) {
    if (upload.getStatus() == UploadsStatusEnum.ERROR
      || (upload.getStatus() == UploadsStatusEnum.COMPLETED
        && details.stream().anyMatch(detail -> detail.getStatus() == IngestionFlowFileStatus.ERROR))) {
      return FAILED;
    }
    Integer expected = upload.getNumTotalFiles();
    validator.validateDetailCount(upload, details.size());
    if (upload.getStatus() != UploadsStatusEnum.COMPLETED || details.isEmpty()
      || missingExisting || (expected != null && details.size() != expected)
      || (expected != null && upload.getNumCorrectlyProcessedFiles() != null
        && !expected.equals(upload.getNumCorrectlyProcessedFiles()))
      || details.stream().anyMatch(detail -> (detail.getStatus() != IngestionFlowFileStatus.COMPLETED
        && detail.getStatus() != IngestionFlowFileStatus.WARNING)
        || detail.getNumTotalRows() == null || detail.getNumCorrectlyImportedRows() == null)) {
      return WAITING_PU;
    }
    long total = details.stream().mapToLong(UploadDetails::getNumTotalRows).sum();
    long accepted = details.stream().mapToLong(UploadDetails::getNumCorrectlyImportedRows).sum();
    if (accepted == total) {
      return COMPLETED;
    }
    return accepted == 0 ? FAILED : WARNING;
  }

  private String puError(Uploads upload, List<UploadDetails> details) {
    if (StringUtils.hasText(upload.getErrorDescription())) {
      return upload.getErrorDescription();
    }
    return details.stream().filter(detail -> detail.getStatus() == IngestionFlowFileStatus.ERROR)
      .map(UploadDetails::getErrorDescription).filter(StringUtils::hasText).findFirst()
      .orElse("PU processing failed or rejected all rows for upload " + upload.getUploadId());
  }
}
