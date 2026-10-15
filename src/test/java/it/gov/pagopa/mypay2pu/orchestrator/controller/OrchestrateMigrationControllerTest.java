package it.gov.pagopa.mypay2pu.orchestrator.controller;

import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.*;
import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.service.OrchestrateMigrationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrchestrateMigrationControllerTest {
  @Mock
  private OrchestrateMigrationService orchestrateMigrationServiceMock;

  private OrchestrateMigrationController controller;

  @BeforeEach
  void init() {
    controller  = new OrchestrateMigrationController(orchestrateMigrationServiceMock);
  }

  @AfterEach
  void verifyMocks() {
    verifyNoMoreInteractions(orchestrateMigrationServiceMock);
  }

  @Test
  void returnsAcceptedAfterDelegatingRequestToService() {
    MigrationRequestDto request = new MigrationRequestDto();
    request.setAction(MigrationAction.TRANSFER);
    request.setMigrationId(UUID.randomUUID());
    request.setOrchestationMode(CycleMode.DELTA);
    MigrationResponseDto response = new MigrationResponseDto();
    when(orchestrateMigrationServiceMock.startMigration(request)).thenReturn(response);

    ResponseEntity<MigrationResponseDto> result = controller.orchestrateMigration(request);

    assertEquals(HttpStatus.ACCEPTED, result.getStatusCode());
    assertSame(response, result.getBody());
  }

  @Test
  void acceptsCompletePhaseWithExportRequest() {
    MigrationRequestDto request = new MigrationRequestDto();
    request.setAction(MigrationAction.COMPLETE);
    request.setOrchestationMode(CycleMode.DELTA);
    request.setIpaCodes(List.of("80000000000"));
    request.setFileTypesToInclude(List.of(MigrationFileType.ORGANIZATIONS));
    MigrationResponseDto response = new MigrationResponseDto();
    when(orchestrateMigrationServiceMock.startMigration(request)).thenReturn(response);

    ResponseEntity<MigrationResponseDto> result = controller.orchestrateMigration(request);

    assertEquals(HttpStatus.ACCEPTED, result.getStatusCode());
    assertSame(response, result.getBody());
  }
}
