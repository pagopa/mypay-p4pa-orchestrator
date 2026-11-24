package it.gov.pagopa.mypay2pu.orchestrator.service;

import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationFileExecutionDetailStatus;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationFileExecutionStatus;
import it.gov.pagopa.mypay2pu.orchestrator.exception.ConflictException;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
    UUID fileId, Long uploadId, Uploads upload, List<UploadDetails> uploadDetails
  ) {
    MigrationFileExecution file = findRequiredForUpdate(fileId);
    validator.validateUpload(file, uploadId, upload);
    List<UploadDetails> snapshot = uploadDetails == null ? List.of() : uploadDetails;
    validator.validateDetails(uploadId, snapshot);
    List<MigrationFileExecutionDetail> existing = detailRepository.findAllByMigrationFileExecutionId(fileId);
    boolean terminalFile = validator.isTerminal(file);
    Map<Long, MigrationFileExecutionDetail> correlated = correlateDetails(file, snapshot, existing);
    boolean missingExisting = snapshot.size() != existing.size();
    validator.validateDetailCount(upload, snapshot.size());
    MigrationFileExecutionStatus target = resolveOutcome(upload, snapshot, correlated, missingExisting);

    if (terminalFile) {
      validator.validateTerminalReplay(file, upload, target, !missingExisting);
      return file;
    }

    validator.validateRecordTransferOutcome(file, upload, target);
    snapshot.forEach(detail -> {
      MigrationFileExecutionDetail original = correlated.get(detail.getUploadDetailId());
      detailMapper.updateMigrationFileExecutionDetail(original, detail, target != WAITING_PU);
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

  private Map<Long, MigrationFileExecutionDetail> correlateDetails(
    MigrationFileExecution file,
    List<UploadDetails> snapshot,
    List<MigrationFileExecutionDetail> existing
  ) {
    Map<String, List<MigrationFileExecutionDetail>> byBaseName = new HashMap<>();
    Map<Long, MigrationFileExecutionDetail> byUploadDetailId = new HashMap<>();
    Set<String> fileNames = new HashSet<>();
    for (MigrationFileExecutionDetail detail : existing) {
      if (!fileNames.add(detail.getFileName())
        || (detail.getUploadDetailId() != null
          && byUploadDetailId.put(detail.getUploadDetailId(), detail) != null)) {
        throw new ConflictException("TRANSFER_OUTCOME_CONFLICT", "Duplicate persisted details for file " + file.getId());
      }
      byBaseName.computeIfAbsent(StringUtils.getFilename(detail.getFileName()), key -> new ArrayList<>()).add(detail);
    }
    Map<Long, MigrationFileExecutionDetail> correlated = new HashMap<>();
    Set<UUID> matched = new HashSet<>();
    for (UploadDetails detail : snapshot) {
      MigrationFileExecutionDetail original = byUploadDetailId.get(detail.getUploadDetailId());
      if (original == null) {
        original = findInitialDetail(detail, byBaseName.getOrDefault(detail.getFileName(), List.of()));
      }
      validator.validateDetailUpdate(file, original, detail);
      if (!matched.add(original.getId())) {
        throw new InvalidValueException("TRANSFER_OUTCOME_INVALID_RESPONSE",
          "PU returned multiple results for exported file " + original.getFileName());
      }
      correlated.put(detail.getUploadDetailId(), original);
    }
    return correlated;
  }

  private MigrationFileExecutionDetail findInitialDetail(UploadDetails detail,
                                                         List<MigrationFileExecutionDetail> namedDetails) {
    List<MigrationFileExecutionDetail> matching = namedDetails.stream()
      .filter(candidate -> (candidate.getUploadDetailId() == null && Long.valueOf(0L).equals(candidate.getFileSize()))
        || Objects.equals(candidate.getFileSize(), detail.getFileSize())).toList();
    List<MigrationFileExecutionDetail> unbound = matching.stream()
      .filter(candidate -> candidate.getUploadDetailId() == null).toList();
    if (unbound.size() == 1) {
      return unbound.getFirst();
    }
    if (unbound.isEmpty() && !matching.isEmpty()) {
      throw new ConflictException("TRANSFER_OUTCOME_CONFLICT", "PU changed upload detail ID for " + detail.getFileName());
    }
    throw new InvalidValueException("TRANSFER_OUTCOME_INVALID_RESPONSE",
      "PU detail has no unique exported file for upload " + detail.getUploadId() + ": " + detail.getFileName());
  }

  private void failWaitingDetails(List<MigrationFileExecutionDetail> details) {
    details.stream().filter(detail -> detail.getStatus() == MigrationFileExecutionDetailStatus.WAITING_PU)
      .forEach(detail -> detail.setStatus(MigrationFileExecutionDetailStatus.FAILED));
  }

  private MigrationFileExecutionStatus resolveOutcome(Uploads upload,
                                                       List<UploadDetails> details,
                                                       Map<Long, MigrationFileExecutionDetail> correlated,
                                                       boolean missingExisting) {
    if (upload.getStatus() == UploadsStatusEnum.ERROR
      || (upload.getStatus() == UploadsStatusEnum.COMPLETED
        && details.stream().anyMatch(detail -> detail.getStatus() == IngestionFlowFileStatus.ERROR))) {
      return FAILED;
    }
    Integer expected = upload.getNumTotalFiles();
    if (upload.getStatus() != UploadsStatusEnum.COMPLETED || details.isEmpty()
      || missingExisting || (expected != null && details.size() != expected)
      || (upload.getNumCorrectlyProcessedFiles() != null
        && details.size() != upload.getNumCorrectlyProcessedFiles())) {
      return WAITING_PU;
    }
    long total = 0;
    long accepted = 0;
    for (UploadDetails detail : details) {
      MigrationFileExecutionDetail original = correlated.get(detail.getUploadDetailId());
      Integer detailTotal = detail.getNumTotalRows() == null
        ? original.getNumTotalRows() : Integer.valueOf(Math.toIntExact(detail.getNumTotalRows()));
      Integer detailAccepted = detail.getNumCorrectlyImportedRows() == null
        ? original.getNumCorrectlyImportedRows() : Integer.valueOf(Math.toIntExact(detail.getNumCorrectlyImportedRows()));
      if ((detail.getStatus() != IngestionFlowFileStatus.COMPLETED
        && detail.getStatus() != IngestionFlowFileStatus.WARNING)
        || detailTotal == null || detailAccepted == null) {
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

  private String puError(Uploads upload, List<UploadDetails> details) {
    if (StringUtils.hasText(upload.getErrorDescription())) {
      return upload.getErrorDescription();
    }
    return details.stream().filter(detail -> detail.getStatus() == IngestionFlowFileStatus.ERROR)
      .map(UploadDetails::getErrorDescription).filter(StringUtils::hasText).findFirst()
      .orElse("PU processing failed or rejected all rows for upload " + upload.getUploadId());
  }
}
