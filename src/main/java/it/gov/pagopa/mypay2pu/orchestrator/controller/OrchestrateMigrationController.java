package it.gov.pagopa.mypay2pu.orchestrator.controller;

import it.gov.pagopa.mypay2pu.orchestrator.controller.generated.OrchestrateMigrationApi;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationRequestDto;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationResponseDto;
import it.gov.pagopa.mypay2pu.orchestrator.service.OrchestrateMigrationService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
public class OrchestrateMigrationController implements OrchestrateMigrationApi {
  private final OrchestrateMigrationService orchestrateMigrationService;

  public OrchestrateMigrationController(OrchestrateMigrationService orchestrateMigrationService) {
    this.orchestrateMigrationService = orchestrateMigrationService;
  }

  @Override
  public ResponseEntity<MigrationResponseDto> orchestrateMigration(@Valid MigrationRequestDto request) {
    log.info("orchestrateMigration: action={}, ipaCodes={}", request.getAction(), request.getIpaCodes());
    MigrationResponseDto response = orchestrateMigrationService.startMigration(request);
    log.info("Accepted migration {} for action {} and ipaCodes {}", response.getMigrationId(), request.getAction(), request.getIpaCodes());
    return ResponseEntity.accepted().body(response);
  }
}
