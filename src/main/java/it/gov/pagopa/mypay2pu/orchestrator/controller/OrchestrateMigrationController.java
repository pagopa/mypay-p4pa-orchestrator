package it.gov.pagopa.mypay2pu.orchestrator.controller;

import it.gov.pagopa.mypay2pu.orchestrator.controller.generated.OrchestrateMigrationApi;
import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.ExportFileStatusDto;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationRequestDto;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationResponseDto;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationStatusDto;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.TransferFileStatusDto;
import it.gov.pagopa.mypay2pu.orchestrator.service.MigrationStatusService;
import it.gov.pagopa.mypay2pu.orchestrator.service.OrchestrateMigrationService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
public class OrchestrateMigrationController implements OrchestrateMigrationApi {
  private final OrchestrateMigrationService orchestrateMigrationService;
  private final MigrationStatusService migrationStatusService;

  @Autowired
  public OrchestrateMigrationController(
    OrchestrateMigrationService orchestrateMigrationService,
    MigrationStatusService migrationStatusService
  ) {
    this.orchestrateMigrationService = orchestrateMigrationService;
    this.migrationStatusService = migrationStatusService;
  }

  @Override
  public ResponseEntity<MigrationResponseDto> orchestrateMigration(@Valid MigrationRequestDto request) {
    log.info("orchestrateMigration: action={}, ipaCodes={}", request.getAction(), request.getIpaCodes());
    MigrationResponseDto response = orchestrateMigrationService.startMigration(request);
    log.info("Accepted migration {} for action {} and ipaCodes {}", response.getMigrationId(), request.getAction(), request.getIpaCodes());
    return ResponseEntity.accepted().body(response);
  }

  @Override
  public ResponseEntity<MigrationStatusDto> getMigrationStatus(UUID migrationId) {
    return ResponseEntity.ok(migrationStatusService.getMigrationStatus(migrationId));
  }

  @Override
  public ResponseEntity<ExportFileStatusDto> getExportFileStatus(UUID migrationId, MigrationFileType fileType) {
    return ResponseEntity.ok(migrationStatusService.getExportFileStatus(migrationId, fileType));
  }

  @Override
  public ResponseEntity<TransferFileStatusDto> getTransferFileStatus(UUID migrationId, MigrationFileType fileType) {
    return ResponseEntity.ok(migrationStatusService.getTransferFileStatus(migrationId, fileType));
  }
}
