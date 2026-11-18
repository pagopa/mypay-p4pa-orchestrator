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
import it.gov.pagopa.mypay2pu.orchestrator.validation.MigrationFileExecutionEvidenceValidator;
import it.gov.pagopa.pu.migration.dto.generated.UploadDetails;
import it.gov.pagopa.pu.migration.dto.generated.Uploads;
import it.gov.pagopa.pu.migration.dto.generated.UploadsStatusEnum;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import static it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationFileExecutionStatus.*;

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
    List<MigrationFileExecutionDetail> mapped = snapshot.stream()
      .map(detail -> detailMapper.toMigrationFileExecutionDetail(fileId, uploadId, detail))
      .toList();
    Map<Long, MigrationFileExecutionDetail> byUploadDetailId = existing.stream()
      .collect(Collectors.toMap(MigrationFileExecutionDetail::getUploadDetailId, Function.identity()));
    List<MigrationFileExecutionDetail> changed = prepareUpdates(mapped, byUploadDetailId);
    boolean missingExisting = !mapped.stream().map(MigrationFileExecutionDetail::getUploadDetailId)
      .collect(Collectors.toSet()).containsAll(byUploadDetailId.keySet());
    MigrationFileExecutionStatus target = resolveOutcome(upload, mapped, missingExisting);

    if (isTerminal(file.getStatus())) {
      if (target != file.getStatus() || upload.getStatus() != file.getUploadStatus()
        || !changed.isEmpty() || mapped.size() != existing.size()) {
        throw new ConflictException("EVIDENCE_CONFLICT", "Cannot replace terminal evidence for file " + fileId);
      }
      return file;
    }

    validator.validatePolling(file);
    if ((file.getUploadStatus() == UploadsStatusEnum.PROCESSING && upload.getStatus() == UploadsStatusEnum.UPLOADED)
      || (file.getUploadStatus() == UploadsStatusEnum.COMPLETED
        && (upload.getStatus() == UploadsStatusEnum.UPLOADED || upload.getStatus() == UploadsStatusEnum.PROCESSING))) {
      throw new ConflictException("EVIDENCE_CONFLICT", "PU upload status regressed for file " + fileId);
    }
    if (target != WAITING_PU) {
      validator.validateTransition(file, target);
    }
    if (!changed.isEmpty()) {
      detailRepository.saveAll(changed);
    }
    file.setUploadStatus(upload.getStatus());
    file.setStatus(target);
    if (target == FAILED) {
      file.setUploadErrorCode("EVIDENCE_PU_FAILED");
      file.setUploadErrorMsg(puError(upload, mapped));
    }
    return fileRepository.save(file);
  }

  @Transactional
  public MigrationFileExecution markFailed(UUID fileId, Long uploadId, String errorCode, String message) {
    MigrationFileExecution file = findRequiredForUpdate(fileId);
    if (!Objects.equals(uploadId, file.getUploadId())) {
      throw new ConflictException("EVIDENCE_CONFLICT", "Upload changed for file " + fileId);
    }
    if (isTerminal(file.getStatus())) {
      return file;
    }
    validator.validateTransition(file, FAILED);
    file.setStatus(FAILED);
    file.setUploadErrorCode(errorCode);
    file.setUploadErrorMsg(message);
    return fileRepository.save(file);
  }

  public static boolean isTerminal(MigrationFileExecutionStatus status) {
    return status == COMPLETED || status == WARNING || status == FAILED;
  }

  private List<MigrationFileExecutionDetail> prepareUpdates(
    List<MigrationFileExecutionDetail> mapped, Map<Long, MigrationFileExecutionDetail> existing
  ) {
    List<MigrationFileExecutionDetail> changed = new ArrayList<>();
    for (MigrationFileExecutionDetail detail : mapped) {
      MigrationFileExecutionDetail original = existing.get(detail.getUploadDetailId());
      if (original != null) {
        validator.validateDetailUpdate(original, detail);
        detail.setId(original.getId());
        detail.setCreationDate(original.getCreationDate());
      }
      if (!detail.equals(original)) {
        changed.add(detail);
      }
    }
    return changed;
  }

  private MigrationFileExecutionStatus resolveOutcome(Uploads upload,
                                                       List<MigrationFileExecutionDetail> details,
                                                       boolean missingExisting) {
    if (upload.getStatus() == UploadsStatusEnum.ERROR
      || details.stream().anyMatch(detail -> detail.getStatus() == MigrationFileExecutionDetailStatus.FAILED)) {
      return FAILED;
    }
    Integer expected = upload.getNumTotalFiles();
    if (expected != null && details.size() > expected) {
      throw new InvalidValueException("EVIDENCE_INVALID_RESPONSE", "PU returned more details than expected");
    }
    if (upload.getStatus() != UploadsStatusEnum.COMPLETED || details.isEmpty()
      || missingExisting || (expected != null && details.size() != expected)
      || (expected != null && upload.getNumCorrectlyProcessedFiles() != null
        && !expected.equals(upload.getNumCorrectlyProcessedFiles()))
      || details.stream().anyMatch(detail -> detail.getStatus() == MigrationFileExecutionDetailStatus.WAITING_PU
        || detail.getNumTotalRows() == null || detail.getNumCorrectlyImportedRows() == null)) {
      return WAITING_PU;
    }
    long total = details.stream().mapToLong(MigrationFileExecutionDetail::getNumTotalRows).sum();
    long accepted = details.stream().mapToLong(MigrationFileExecutionDetail::getNumCorrectlyImportedRows).sum();
    if (accepted == total) {
      return COMPLETED;
    }
    return accepted == 0 ? FAILED : WARNING;
  }

  private String puError(Uploads upload, List<MigrationFileExecutionDetail> details) {
    if (StringUtils.hasText(upload.getErrorDescription())) {
      return upload.getErrorDescription();
    }
    return details.stream().filter(detail -> detail.getStatus() == MigrationFileExecutionDetailStatus.FAILED)
      .map(MigrationFileExecutionDetail::getErrorDescription).filter(StringUtils::hasText).findFirst()
      .orElse("PU processing failed or rejected all rows for upload " + upload.getUploadId());
  }
}
