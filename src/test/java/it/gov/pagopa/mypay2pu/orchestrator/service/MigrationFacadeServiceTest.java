package it.gov.pagopa.mypay2pu.orchestrator.service;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationAction;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationRequestDto;
import it.gov.pagopa.mypay2pu.orchestrator.service.operations.MigrationExportOperation;
import it.gov.pagopa.mypay2pu.orchestrator.service.operations.MigrationTransferOperation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MigrationFacadeServiceTest {

  @Mock
  private ApplicationContext applicationContextMock;
  @Mock
  private MigrationExportOperation organizationsExportMock;
  @Mock
  private MigrationExportOperation orgSilServicesExportMock;
  @Mock
  private MigrationExportOperation debtPositionsExportMock;
  @Mock
  private MigrationTransferOperation organizationsTransferMock;
  @Mock
  private MigrationTransferOperation orgSilServicesTransferMock;
  @Mock
  private MigrationTransferOperation debtPositionsTransferMock;

  private MigrationFacadeService facade;

  @BeforeEach
  void setUp() {
    when(organizationsExportMock.getMigrationFileType()).thenReturn(MigrationFileType.ORGANIZATIONS);
    when(orgSilServicesExportMock.getMigrationFileType()).thenReturn(MigrationFileType.ORG_SIL_SERVICES);
    when(debtPositionsExportMock.getMigrationFileType()).thenReturn(MigrationFileType.DEBT_POSITIONS);
    when(organizationsTransferMock.getMigrationFileType()).thenReturn(MigrationFileType.ORGANIZATIONS);
    when(orgSilServicesTransferMock.getMigrationFileType()).thenReturn(MigrationFileType.ORG_SIL_SERVICES);
    when(debtPositionsTransferMock.getMigrationFileType()).thenReturn(MigrationFileType.DEBT_POSITIONS);
    when(applicationContextMock.getBeansOfType(MigrationExportOperation.class)).thenReturn(Map.of(
      "organizationsExport", organizationsExportMock,
      "orgSilServicesExport", orgSilServicesExportMock,
      "debtPositionsExport", debtPositionsExportMock
    ));
    when(applicationContextMock.getBeansOfType(MigrationTransferOperation.class)).thenReturn(Map.of(
      "organizationsTransfer", organizationsTransferMock,
      "orgSilServicesTransfer", orgSilServicesTransferMock,
      "debtPositionsTransfer", debtPositionsTransferMock
    ));

    facade = new MigrationFacadeService(new MigrationPlanExecutorService(), applicationContextMock);
  }

  @AfterEach
  void verifyMocks() {
    verifyNoMoreInteractions(
      applicationContextMock,
      organizationsExportMock,
      orgSilServicesExportMock,
      debtPositionsExportMock,
      organizationsTransferMock,
      orgSilServicesTransferMock,
      debtPositionsTransferMock
    );
  }

  @Test
  void givenTransferSelectionWhenExecuteThenInvokeOnlySelectedTransferOperation() {
    MigrationRequestDto request = request(MigrationAction.TRANSFER,
      MigrationFileType.DEBT_POSITIONS);

    facade.execute(request);

    verify(debtPositionsTransferMock).execute(request);
  }

  @Test
  void givenExportSelectionWhenExecuteThenInvokeOnlySelectedExportOperation() {
    MigrationRequestDto request = request(MigrationAction.EXPORT,
      MigrationFileType.ORG_SIL_SERVICES);

    facade.execute(request);

    verify(orgSilServicesExportMock).execute(request);
  }

  @Test
  void givenCompleteSelectionWhenExecuteThenInvokeOnlySelectedExportAndTransferOperations() {
    MigrationRequestDto request = request(MigrationAction.COMPLETE,
      MigrationFileType.ORGANIZATIONS,
      MigrationFileType.ORG_SIL_SERVICES);

    facade.execute(request);

    verify(organizationsExportMock).execute(request);
    verify(organizationsTransferMock).execute(request);
    verify(orgSilServicesExportMock).execute(request);
    verify(orgSilServicesTransferMock).execute(request);
  }

  private MigrationRequestDto request(MigrationAction action, MigrationFileType... fileTypes) {
    MigrationRequestDto request = new MigrationRequestDto();
    request.setAction(action);
    request.setFileTypesToInclude(List.of(fileTypes));
    return request;
  }
}
