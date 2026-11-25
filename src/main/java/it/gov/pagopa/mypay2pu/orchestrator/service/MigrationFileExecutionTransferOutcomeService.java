package it.gov.pagopa.mypay2pu.orchestrator.service;

import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationFileExecutionDetailStatus;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationFileExecutionStatus;
import it.gov.pagopa.mypay2pu.orchestrator.exception.InvalidValueException;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationFileExecution;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationFileExecutionDetail;
import it.gov.pagopa.mypay2pu.orchestrator.repository.MigrationFileExecutionDetailRepository;
import it.gov.pagopa.mypay2pu.orchestrator.repository.MigrationFileExecutionRepository;
import it.gov.pagopa.mypay2pu.orchestrator.service.mapper.MigrationFileExecutionDetailMapper;
import it.gov.pagopa.mypay2pu.orchestrator.validation.MigrationFileExecutionTransferOutcomeValidator;
import it.gov.pagopa.pu.migration.dto.generated.IngestionFlowFileStatus;
import it.gov.pagopa.pu.migration.dto.generated.UploadDetails;
import it.gov.pagopa.pu.migration.dto.generated.Uploads;
import it.gov.pagopa.pu.migration.dto.generated.UploadsStatusEnum;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import static it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationFileExecutionStatus.COMPLETED;
import static it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationFileExecutionStatus.FAILED;
import static it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationFileExecutionStatus.WAITING_PU;
import static it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationFileExecutionStatus.WARNING;

/** Applies correlated PU snapshots in short transactions, without remote calls. */
@Service
public class MigrationFileExecutionTransferOutcomeService extends MigrationFileExecutionService {

  private final MigrationFileExecutionRepository fileRepository;
  private final MigrationFileExecutionDetailRepository detailRepository;
  private final MigrationFileExecutionDetailMapper detailMapper;
  private final MigrationFileExecutionTransferOutcomeValidator validator;

  public MigrationFileExecutionTransferOutcomeService(
    MigrationFileExecutionRepository fileRepository,
    MigrationFileExecutionDetailRepository detailRepository,
    MigrationFileExecutionDetailMapper detailMapper,
    MigrationFileExecutionTransferOutcomeValidator validator
  ) {
    super(fileRepository);
    this.fileRepository = fileRepository;
    this.detailRepository = detailRepository;
    this.detailMapper = detailMapper;
    this.validator = validator;
  }

  @Transactional
  public MigrationFileExecution recordTransferOutcome(
    UUID migrationFileExecutionId, Uploads upload, List<UploadDetails> uploadDetails
  ) {
    MigrationFileExecution file = findRequired(migrationFileExecutionId);
    validator.validateUpload(file, upload);
    List<UploadDetails> snapshot = uploadDetails == null ? List.of() : uploadDetails;
    validator.validateDetails(file.getUploadId(), snapshot);
    List<MigrationFileExecutionDetail> existing = detailRepository.findAllByMigrationFileExecutionId(migrationFileExecutionId);
    boolean terminalFile = validator.isCompleted(file);
    Map<Long, MigrationFileExecutionDetail> uploadDetailId2MigrationFileDetail = correlateDetails(file, snapshot, existing);
    boolean missingExisting = snapshot.size() != existing.size();
    MigrationFileExecutionStatus target = resolveOutcome(upload, snapshot, missingExisting);

    if (terminalFile) {
      validator.validateTerminalReplay(file, upload, target, !missingExisting);
      return file;
    }

    validator.validateRecordTransferOutcome(file, upload);
    snapshot.forEach(detail -> {
      MigrationFileExecutionDetail original = uploadDetailId2MigrationFileDetail.get(detail.getUploadDetailId());
      detailMapper.updateMigrationFileExecutionDetail(original, detail);
      if (target != WAITING_PU) {
        original.setStatus(detailMapper.toLocalStatus(detail.getStatus()));
      }
    });
    if (target == FAILED) {
      failWaitingDetails(existing);
    }
    if (!existing.isEmpty() && (!snapshot.isEmpty() || target == FAILED)) {
      detailRepository.saveAll(existing);
    }
    file.setUploadStatus(upload.getStatus());
    file.setStatus(target);
    if (target == FAILED) {
      file.setUploadErrorCode("TRANSFER_OUTCOME_PU_FAILED");
      file.setUploadErrorMsg(puError(upload, snapshot));
    }
    return fileRepository.save(file);
  }

  @Transactional
  public MigrationFileExecution markFailed(UUID migrationFileExecutionId, String errorCode, String message) {
    MigrationFileExecution file = findRequired(migrationFileExecutionId);
    validator.validateMarkFailed(file);
    if (validator.isCompleted(file)) {
      return file;
    }
    List<MigrationFileExecutionDetail> details = detailRepository.findAllByMigrationFileExecutionId(migrationFileExecutionId);
    failWaitingDetails(details);
    if (!details.isEmpty()) {
      detailRepository.saveAll(details);
    }
    file.setStatus(FAILED);
    file.setUploadErrorCode(errorCode);
    file.setUploadErrorMsg(message);
    return fileRepository.save(file);
  }

  private Map<Long, MigrationFileExecutionDetail> correlateDetails(
    MigrationFileExecution file,
    List<UploadDetails> snapshot,
    List<MigrationFileExecutionDetail> existing
  ) {
    Map<String, MigrationFileExecutionDetail> fileName2MigrationFileDetail = existing.stream()
      .collect(Collectors.toMap(MigrationFileExecutionDetail::getFileName, Function.identity()));
    return snapshot.stream().collect(Collectors.toMap(UploadDetails::getUploadDetailId, detail -> {
      MigrationFileExecutionDetail original = fileName2MigrationFileDetail.get(detail.getFileName());
      if (original == null) {
        throw new InvalidValueException("TRANSFER_OUTCOME_INVALID_RESPONSE",
          "PU returned a file not present in the export: " + detail.getFileName());
      }
      validator.validateDetailUpdate(file, original, detail);
      return original;
    }));
  }

  private void failWaitingDetails(List<MigrationFileExecutionDetail> details) {
    details.stream().filter(detail -> detail.getStatus() == MigrationFileExecutionDetailStatus.WAITING_PU)
      .forEach(detail -> detail.setStatus(MigrationFileExecutionDetailStatus.FAILED));
  }

  private MigrationFileExecutionStatus resolveOutcome(Uploads upload,
                                                       List<UploadDetails> details,
                                                       boolean missingExisting) {
    if (hasPuFailure(upload, details)) {
      return FAILED;
    }
    if (!hasCompleteFileSnapshot(upload, details, missingExisting)) {
      return WAITING_PU;
    }
    long total = 0;
    long accepted = 0;
    for (UploadDetails detail : details) {
      long detailTotal = Objects.requireNonNullElse(detail.getNumTotalRows(), 0L);
      long detailAccepted = Objects.requireNonNullElse(detail.getNumCorrectlyImportedRows(), 0L);
      if (detail.getStatus() != IngestionFlowFileStatus.COMPLETED
        && detail.getStatus() != IngestionFlowFileStatus.WARNING) {
        return WAITING_PU;
      }
      total += detailTotal;
      accepted += detailAccepted;
    }
    if (accepted == total) {
      return COMPLETED;
    }
    return accepted == 0 ? FAILED : WARNING;
  }

  private boolean hasPuFailure(Uploads upload, List<UploadDetails> details) {
    return upload.getStatus() == UploadsStatusEnum.ERROR
      || (upload.getStatus() == UploadsStatusEnum.COMPLETED
        && details.stream().anyMatch(detail -> detail.getStatus() == IngestionFlowFileStatus.ERROR));
  }

  private boolean hasCompleteFileSnapshot(Uploads upload, List<UploadDetails> details, boolean missingExisting) {
    Integer expected = upload.getNumTotalFiles();
    Integer processed = upload.getNumCorrectlyProcessedFiles();
    return upload.getStatus() == UploadsStatusEnum.COMPLETED && !details.isEmpty()
      && !missingExisting && (expected == null || details.size() == expected)
      && (processed == null || details.size() == processed);
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
