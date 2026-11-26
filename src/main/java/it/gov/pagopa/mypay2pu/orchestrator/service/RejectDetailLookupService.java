package it.gov.pagopa.mypay2pu.orchestrator.service;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationFileExecutionDetailStatus;
import it.gov.pagopa.mypay2pu.orchestrator.exception.InvalidValueException;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationFileExecution;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationFileExecutionDetail;
import it.gov.pagopa.mypay2pu.orchestrator.repository.MigrationFileExecutionDetailRepository;
import it.gov.pagopa.mypay2pu.orchestrator.repository.MigrationFileExecutionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class RejectDetailLookupService {

  private final MigrationFileExecutionRepository executionRepository;
  private final MigrationFileExecutionDetailRepository detailRepository;

  public RejectDetailLookupService(
    MigrationFileExecutionRepository executionRepository,
    MigrationFileExecutionDetailRepository detailRepository
  ) {
    this.executionRepository = executionRepository;
    this.detailRepository = detailRepository;
  }

  // TODO: invoke before it.gov.pagopa.mypay2pu.orchestrator.service.MigrationFileExecutionService.ensurePending for REJECTS_ONLY and resolve each group's discard files.
  @Transactional(readOnly = true)
  public Map<String, List<RejectDetail>> findByParentAndType(UUID parentMigrationId, MigrationFileType fileType) {
    Map<UUID, MigrationFileExecution> executions = executionRepository
      .findAllByMigrationIdAndFileType(parentMigrationId, fileType).stream()
      .collect(Collectors.toMap(MigrationFileExecution::getId, Function.identity()));
    if (executions.isEmpty()) {
      return Map.of();
    }

    Map<String, List<RejectDetail>> groups = new HashMap<>();
    List<MigrationFileExecutionDetail> migrationFileExecutionDetails = detailRepository
      .findAllByMigrationFileExecutionIdInAndStatus(executions.keySet(), MigrationFileExecutionDetailStatus.PARTIAL);
    for (MigrationFileExecutionDetail detail : migrationFileExecutionDetails) {
      MigrationFileExecution execution = executions.get(detail.getMigrationFileExecutionId());
      if (!StringUtils.hasText(detail.getDiscardFileName()) || execution.getUploadId() == null) {
        throw new InvalidValueException(
          "INVALID_REJECT_DETAIL",
          "discard file or upload for reject detail %s".formatted(detail.getId())
        );
      }
      groups.computeIfAbsent(detail.getIpaCode(), ignored -> new ArrayList<>())
        .add(new RejectDetail(detail.getId(), execution.getId(), execution.getUploadId(), detail.getDiscardFileName()));
    }
    groups.replaceAll((ipa, details) -> details);
    return groups;
  }

  public record RejectDetail(UUID detailId, UUID fileExecutionId, Long uploadId, String discardFileName) {}
}
