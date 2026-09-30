package it.gov.pagopa.mypay2pu.orchestrator.controller;

import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.*;
import it.gov.pagopa.mypay2pu.orchestrator.service.OrchestrateMigrationService;
import it.gov.pagopa.mypay2pu.orchestrator.validation.MigrationRequestValidator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

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
    controller  = new OrchestrateMigrationController(
      orchestrateMigrationServiceMock,
      new MigrationRequestValidator());
  }

  @AfterEach
  void verifyMocks() {
    verifyNoMoreInteractions(orchestrateMigrationServiceMock);
  }

  @Test
  void returnsAcceptedAfterDelegatingRequestToService() {
    MigrationRequestDto request = new MigrationRequestDto();
    request.setPhase(MigrationPhase.TRANSFER);
    request.setMigrationId(UUID.randomUUID());
    request.setCycleMode(CycleMode.DELTA);
    MigrationResponseDto response = new MigrationResponseDto();
    when(orchestrateMigrationServiceMock.startMigration(request)).thenReturn(response);

    ResponseEntity<MigrationResponseDto> result = controller.orchestrateMigration(request);

    assertEquals(HttpStatus.ACCEPTED, result.getStatusCode());
    assertSame(response, result.getBody());
  }
}
