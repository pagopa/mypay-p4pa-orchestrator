package it.gov.pagopa.mypay2pu.orchestrator.service;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.ExportFileStatusDto;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationStatusDto;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.TransferFileStatusDto;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationExecution;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationFileExecution;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationFileExecutionDetail;
import it.gov.pagopa.mypay2pu.orchestrator.repository.MigrationExecutionRepository;
import it.gov.pagopa.mypay2pu.orchestrator.repository.MigrationFileExecutionRepository;
import it.gov.pagopa.mypay2pu.orchestrator.repository.MigrationFileExecutionDetailRepository;
import it.gov.pagopa.mypay2pu.orchestrator.service.mapper.MigrationStatusMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MigrationStatusServiceImpl implements MigrationStatusService {

  private final MigrationExecutionRepository migrationExecutionRepository;
  private final MigrationFileExecutionRepository migrationFileExecutionRepository;
  private final MigrationFileExecutionDetailRepository migrationFileExecutionDetailRepository;
  private final MigrationStatusMapper migrationStatusMapper;

  public MigrationStatusServiceImpl(
    MigrationExecutionRepository migrationExecutionRepository,
    MigrationFileExecutionRepository migrationFileExecutionRepository,
    MigrationFileExecutionDetailRepository migrationFileExecutionDetailRepository,
    MigrationStatusMapper migrationStatusMapper
  ) {
    this.migrationExecutionRepository = migrationExecutionRepository;
    this.migrationFileExecutionRepository = migrationFileExecutionRepository;
    this.migrationFileExecutionDetailRepository = migrationFileExecutionDetailRepository;
    this.migrationStatusMapper = migrationStatusMapper;
  }

  @Override
  public MigrationStatusDto getMigrationStatus(UUID migrationId) {
    MigrationExecution execution = findMigration(migrationId);
    List<MigrationFileExecution> fileExecutions =
      migrationFileExecutionRepository.findAllByMigrationIdOrderByCreationDateAscIdAsc(migrationId);
    List<UUID> fileExecutionIds = fileExecutions.stream().map(MigrationFileExecution::getId).toList();
    List<MigrationFileExecutionDetail> fileExecutionDetails = fileExecutionIds.isEmpty()
      ? List.of()
      : migrationFileExecutionDetailRepository.findAllByMigrationFileExecutionIdIn(fileExecutionIds);
    Map<UUID, List<MigrationFileExecutionDetail>> detailsByFileExecutionId = fileExecutionDetails.stream()
      .collect(Collectors.groupingBy(MigrationFileExecutionDetail::getMigrationFileExecutionId));
    return migrationStatusMapper.toMigrationStatusDto(execution, fileExecutions, detailsByFileExecutionId);
  }

  @Override
  public ExportFileStatusDto getExportFileStatus(UUID migrationId, MigrationFileType fileType) {
    MigrationFileExecution fileExecution = findFileExecution(migrationId, fileType);
    List<MigrationFileExecutionDetail> details =
      migrationFileExecutionDetailRepository.findAllByMigrationFileExecutionId(fileExecution.getId());
    return migrationStatusMapper.toExportFileStatusDto(fileExecution, details);
  }

  @Override
  public TransferFileStatusDto getTransferFileStatus(UUID migrationId, MigrationFileType fileType) {
    MigrationFileExecution fileExecution = findFileExecution(migrationId, fileType);
    List<MigrationFileExecutionDetail> details =
      migrationFileExecutionDetailRepository.findAllByMigrationFileExecutionId(fileExecution.getId());
    return migrationStatusMapper.toTransferFileStatusDto(fileExecution, details);
  }

  private MigrationExecution findMigration(UUID migrationId) {
    return migrationExecutionRepository.findById(migrationId)
      .orElseThrow(() -> new ResponseStatusException(
        HttpStatus.NOT_FOUND,
        "Migration with id %s not found".formatted(migrationId)
      ));
  }

  private MigrationFileExecution findFileExecution(UUID migrationId, MigrationFileType fileType) {
    return migrationFileExecutionRepository.findByMigrationIdAndFileType(migrationId, fileType)
      .orElseThrow(() -> new ResponseStatusException(
        HttpStatus.NOT_FOUND,
        "File execution for migration with id %s and file type %s not found".formatted(migrationId, fileType)
      ));
  }
}
