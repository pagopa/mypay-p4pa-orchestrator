package it.gov.pagopa.mypay2pu.orchestrator.controller;

import it.gov.pagopa.mypay2pu.orchestrator.controller.generated.OrchestrateMigrationApi;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationRequestDto;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationResponseDto;
import it.gov.pagopa.mypay2pu.orchestrator.service.OrchestrateMigrationService;
import it.gov.pagopa.mypay2pu.orchestrator.validation.MigrationRequestValidator;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
public class OrchestrateMigrationController implements OrchestrateMigrationApi {
  private final OrchestrateMigrationService orchestrateMigrationService;
  private final MigrationRequestValidator migrationRequestValidator;

  public OrchestrateMigrationController(
    OrchestrateMigrationService orchestrateMigrationService,
    MigrationRequestValidator migrationRequestValidator
  ) {
    this.orchestrateMigrationService = orchestrateMigrationService;
    this.migrationRequestValidator = migrationRequestValidator;
  }

  @Override
  public ResponseEntity<MigrationResponseDto> orchestrateMigration(@Valid MigrationRequestDto request) {
    log.info("orchestrateMigration: phase={}, ipaCodes={}", request.getPhase(), request.getIpaCodes());
    migrationRequestValidator.validate(request);
    MigrationResponseDto response = orchestrateMigrationService.startMigration(request);
    log.info("Accepted migration {} for phase {} and ipaCodes {}", response.getMigrationId(), request.getPhase(), request.getIpaCodes());
    return ResponseEntity.accepted().body(response);
  }
}
