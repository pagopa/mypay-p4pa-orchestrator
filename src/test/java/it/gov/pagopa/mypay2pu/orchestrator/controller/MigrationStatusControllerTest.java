package it.gov.pagopa.mypay2pu.orchestrator.controller;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.ExportFileStatusDto;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationAction;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationExecutionStatus;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationExportPhaseDto;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationFileExecutionStatus;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationStatusDto;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.OrchestrationMode;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationTransferPhaseDto;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.TransferFileStatusDto;
import it.gov.pagopa.mypay2pu.orchestrator.service.MigrationStatusService;
import it.gov.pagopa.mypay2pu.orchestrator.service.OrchestrateMigrationService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MigrationStatusControllerTest {

  @Mock
  private OrchestrateMigrationService orchestrateMigrationServiceMock;
  @Mock
  private MigrationStatusService migrationStatusServiceMock;

  private OrchestrateMigrationController controller;

  @BeforeEach
  void setUp() {
    controller = new OrchestrateMigrationController(orchestrateMigrationServiceMock, migrationStatusServiceMock);
  }

  @AfterEach
  void verifyMocks() {
    verifyNoMoreInteractions(orchestrateMigrationServiceMock, migrationStatusServiceMock);
  }

  @Test
  void returnsMigrationStatusFromService() {
    UUID migrationId = UUID.randomUUID();
    MigrationStatusDto response = new MigrationStatusDto()
      .migrationId(migrationId)
      .action(MigrationAction.EXPORT)
      .status(MigrationExecutionStatus.RUNNING)
      .orchestrationMode(OrchestrationMode.FULL)
      .ipaCodes(List.of("80000000000"))
      .exportPhase(new MigrationExportPhaseDto(MigrationExecutionStatus.RUNNING, List.of()))
      .transferPhase(new MigrationTransferPhaseDto(MigrationExecutionStatus.RUNNING, List.of()));
    when(migrationStatusServiceMock.getMigrationStatus(migrationId)).thenReturn(response);

    ResponseEntity<MigrationStatusDto> result = controller.getMigrationStatus(migrationId);

    verify(migrationStatusServiceMock).getMigrationStatus(migrationId);
    assertEquals(HttpStatus.OK, result.getStatusCode());
    assertSame(response, result.getBody());
  }

  @Test
  void returnsExportFileStatusFromService() {
    UUID migrationId = UUID.randomUUID();
    MigrationFileType fileType = MigrationFileType.ORGANIZATIONS;
    ExportFileStatusDto response = new ExportFileStatusDto(fileType, MigrationFileExecutionStatus.COMPLETED);
    when(migrationStatusServiceMock.getExportFileStatus(migrationId, fileType)).thenReturn(response);

    ResponseEntity<ExportFileStatusDto> result = controller.getExportFileStatus(migrationId, fileType);

    verify(migrationStatusServiceMock).getExportFileStatus(migrationId, fileType);
    assertEquals(HttpStatus.OK, result.getStatusCode());
    assertSame(response, result.getBody());
  }

  @Test
  void returnsTransferFileStatusFromService() {
    UUID migrationId = UUID.randomUUID();
    MigrationFileType fileType = MigrationFileType.ORGANIZATIONS;
    TransferFileStatusDto response = new TransferFileStatusDto(fileType, MigrationFileExecutionStatus.COMPLETED);
    when(migrationStatusServiceMock.getTransferFileStatus(migrationId, fileType)).thenReturn(response);

    ResponseEntity<TransferFileStatusDto> result = controller.getTransferFileStatus(migrationId, fileType);

    verify(migrationStatusServiceMock).getTransferFileStatus(migrationId, fileType);
    assertEquals(HttpStatus.OK, result.getStatusCode());
    assertSame(response, result.getBody());
  }
}
