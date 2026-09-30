package it.gov.pagopa.mypay2pu.orchestrator.controller;

import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.CycleMode;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.FileType;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationPhase;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationRequestDto;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationResponseDto;
import it.gov.pagopa.mypay2pu.orchestrator.service.OrchestrateMigrationService;
import it.gov.pagopa.mypay2pu.orchestrator.validation.MigrationRequestValidator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class OrchestrateMigrationControllerTest {
  private final OrchestrateMigrationService orchestrateMigrationService = mock(OrchestrateMigrationService.class);
  private final OrchestrateMigrationController controller = new OrchestrateMigrationController(
    orchestrateMigrationService,
    new MigrationRequestValidator()
  );

  @AfterEach
  void verifyMocks() {
    verifyNoMoreInteractions(orchestrateMigrationService);
  }

  @Test
  void returnsAcceptedAfterDelegatingRequestToService() {
    MigrationRequestDto request = new MigrationRequestDto();
    request.setPhase(MigrationPhase.EXPORT);
    request.setIpaCodes(List.of("ipa-code"));
    request.setCycleMode(CycleMode.DELTA);
    request.setFileTypesToInclude(List.of(FileType.ORGANIZATIONS));
    MigrationResponseDto response = new MigrationResponseDto();
    when(orchestrateMigrationService.startMigration(request)).thenReturn(response);

    ResponseEntity<MigrationResponseDto> result = controller.orchestrateMigration(request);

    assertEquals(HttpStatus.ACCEPTED, result.getStatusCode());
    assertSame(response, result.getBody());
    verify(orchestrateMigrationService).startMigration(request);
  }
}
