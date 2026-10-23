package it.gov.pagopa.mypay2pu.orchestrator.connector.pu.migration.client;

import it.gov.pagopa.mypay2pu.orchestrator.connector.pu.migration.config.PuMigrationApisHolder;
import it.gov.pagopa.mypay2pu.orchestrator.connector.pu.migration.PuMigrationService;
import it.gov.pagopa.mypay2pu.orchestrator.connector.pu.migration.PuMigrationServiceImpl;
import it.gov.pagopa.pu.migration.controller.generated.MigrationFileApi;
import it.gov.pagopa.pu.migration.dto.generated.MigrationFileTypeEnum;
import it.gov.pagopa.pu.migration.dto.generated.UploadDetails;
import it.gov.pagopa.pu.migration.dto.generated.UploadMigrationFileResponseDTO;
import it.gov.pagopa.pu.migration.dto.generated.Uploads;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

import java.util.List;

@ExtendWith(MockitoExtension.class)
class PuMigrationClientTest {

  private static final String ACCESS_TOKEN = "accessToken";

  @Mock
  private PuMigrationApisHolder puMigrationApisHolderMock;
  @Mock
  private MigrationFileApi migrationApiMock;
  @Mock
  private PuMigrationFileClient puMigrationFileClientMock;

  private PuMigrationFileClient puMigrationFileClient;

  @BeforeEach
  void setUp() {
    puMigrationFileClient = new PuMigrationFileClient(puMigrationApisHolderMock);
  }

  @AfterEach
  void verifyNoMoreInteractions() {
    Mockito.verifyNoMoreInteractions(
      puMigrationApisHolderMock,
      migrationApiMock,
      puMigrationFileClientMock
    );
  }

  @Test
  void whenUploadMigrationFileThenInvokeWithAccessToken() {
    String orgIpaCode = "orgIpaCode";
    MigrationFileTypeEnum fileType = MigrationFileTypeEnum.ORGANIZATIONS;
    String fileName = "fileName";
    Resource file = new ByteArrayResource(new byte[] {1, 2, 3}) {
      @Override
      public String getFilename() {
        return fileName;
      }
    };
    UploadMigrationFileResponseDTO expectedResult =
      new UploadMigrationFileResponseDTO(123L, "workflowId", "runId");

    Mockito.when(puMigrationApisHolderMock.getMigrationFileApi(ACCESS_TOKEN))
      .thenReturn(migrationApiMock);
    Mockito.when(migrationApiMock.uploadMigrationFile(orgIpaCode, fileType, file))
      .thenReturn(expectedResult);

    UploadMigrationFileResponseDTO result = puMigrationFileClient.uploadMigrationFile(
      orgIpaCode, fileType, file, ACCESS_TOKEN
    );

    Assertions.assertSame(expectedResult, result);
    Assertions.assertEquals("workflowId", result.getWorkflowId());
    Mockito.verify(puMigrationApisHolderMock).getMigrationFileApi(ACCESS_TOKEN);
    Mockito.verify(migrationApiMock).uploadMigrationFile(orgIpaCode, fileType, file);
  }

  @Test
  void givenUploadIdWhenGetMigrationUploadThenReturnsUploadStatus() {
    String orgIpaCode = "orgIpaCode";
    Long uploadId = 123L;
    Uploads expectedResponse = new Uploads();
    Mockito.when(puMigrationApisHolderMock.getMigrationFileApi(ACCESS_TOKEN))
      .thenReturn(migrationApiMock);
    Mockito.when(migrationApiMock.getMigrationUpload(orgIpaCode, uploadId)).thenReturn(expectedResponse);

    Uploads response = puMigrationFileClient.getMigrationUpload(orgIpaCode, uploadId, ACCESS_TOKEN);

    Assertions.assertSame(expectedResponse, response);
    Mockito.verify(puMigrationApisHolderMock).getMigrationFileApi(ACCESS_TOKEN);
    Mockito.verify(migrationApiMock).getMigrationUpload(orgIpaCode, uploadId);
  }

  @Test
  void givenUploadIdWhenGetMigrationUploadDetailsThenReturnsDetails() {
    String orgIpaCode = "orgIpaCode";
    Long uploadId = 123L;
    List<UploadDetails> expectedResponse = List.of(new UploadDetails());
    Mockito.when(puMigrationApisHolderMock.getMigrationFileApi(ACCESS_TOKEN))
      .thenReturn(migrationApiMock);
    Mockito.when(migrationApiMock.getMigrationUploadDetails(orgIpaCode, uploadId))
      .thenReturn(expectedResponse);

    List<UploadDetails> response =
      puMigrationFileClient.getMigrationUploadDetails(orgIpaCode, uploadId, ACCESS_TOKEN);

    Assertions.assertSame(expectedResponse, response);
    Mockito.verify(puMigrationApisHolderMock).getMigrationFileApi(ACCESS_TOKEN);
    Mockito.verify(migrationApiMock).getMigrationUploadDetails(orgIpaCode, uploadId);
  }

  @Test
  void givenUploadIdWhenGetMigrationErrorsThenReturnsErrorsResource() {
    String orgIpaCode = "orgIpaCode";
    Long uploadId = 123L;
    Resource expectedResponse = new ByteArrayResource(new byte[] {1, 2, 3});
    Mockito.when(puMigrationApisHolderMock.getMigrationFileApi(ACCESS_TOKEN))
      .thenReturn(migrationApiMock);
    Mockito.when(migrationApiMock.getMigrationErrors(orgIpaCode, uploadId)).thenReturn(expectedResponse);

    Resource response = puMigrationFileClient.getMigrationErrors(orgIpaCode, uploadId, ACCESS_TOKEN);

    Assertions.assertSame(expectedResponse, response);
    Mockito.verify(puMigrationApisHolderMock).getMigrationFileApi(ACCESS_TOKEN);
    Mockito.verify(migrationApiMock).getMigrationErrors(orgIpaCode, uploadId);
  }

  @Test
  void whenServiceUploadsMigrationFileThenInvokeClient() {
    String orgIpaCode = "orgIpaCode";
    MigrationFileTypeEnum fileType = MigrationFileTypeEnum.ORGANIZATIONS;
    Resource file = new ByteArrayResource(new byte[] {1, 2, 3});
    String accessToken = "accessToken";
    UploadMigrationFileResponseDTO expectedResponse =
      new UploadMigrationFileResponseDTO(123L, "workflowId", "runId");
    Mockito.when(puMigrationFileClientMock.uploadMigrationFile(
      orgIpaCode, fileType, file, accessToken
    )).thenReturn(expectedResponse);
    PuMigrationService service = new PuMigrationServiceImpl(puMigrationFileClientMock);

    UploadMigrationFileResponseDTO response =
      service.uploadMigrationFile(orgIpaCode, fileType, file, accessToken);

    Assertions.assertSame(expectedResponse, response);
    Mockito.verify(puMigrationFileClientMock).uploadMigrationFile(
      orgIpaCode, fileType, file, accessToken
    );
  }

  @Test
  void whenServiceGetsMigrationUploadThenInvokeClient() {
    String orgIpaCode = "orgIpaCode";
    Long uploadId = 123L;
    String accessToken = "accessToken";
    Uploads expectedResponse = new Uploads();
    Mockito.when(puMigrationFileClientMock.getMigrationUpload(
      orgIpaCode, uploadId, accessToken
    )).thenReturn(expectedResponse);
    PuMigrationService service = new PuMigrationServiceImpl(puMigrationFileClientMock);

    Uploads response = service.getMigrationUpload(orgIpaCode, uploadId, accessToken);

    Assertions.assertSame(expectedResponse, response);
    Mockito.verify(puMigrationFileClientMock).getMigrationUpload(
      orgIpaCode, uploadId, accessToken
    );
  }

  @Test
  void whenServiceGetsMigrationUploadDetailsThenInvokeClient() {
    String orgIpaCode = "orgIpaCode";
    Long uploadId = 123L;
    String accessToken = "accessToken";
    List<UploadDetails> expectedResponse = List.of(new UploadDetails());
    Mockito.when(puMigrationFileClientMock.getMigrationUploadDetails(
      orgIpaCode, uploadId, accessToken
    )).thenReturn(expectedResponse);
    PuMigrationService service = new PuMigrationServiceImpl(puMigrationFileClientMock);

    List<UploadDetails> response =
      service.getMigrationUploadDetails(orgIpaCode, uploadId, accessToken);

    Assertions.assertSame(expectedResponse, response);
    Mockito.verify(puMigrationFileClientMock).getMigrationUploadDetails(
      orgIpaCode, uploadId, accessToken
    );
  }

  @Test
  void whenServiceGetsMigrationErrorsThenInvokeClient() {
    String orgIpaCode = "orgIpaCode";
    Long uploadId = 123L;
    String accessToken = "accessToken";
    Resource expectedResponse = new ByteArrayResource(new byte[] {1, 2, 3});
    Mockito.when(puMigrationFileClientMock.getMigrationErrors(
      orgIpaCode, uploadId, accessToken
    )).thenReturn(expectedResponse);
    PuMigrationService service = new PuMigrationServiceImpl(puMigrationFileClientMock);

    Resource response = service.getMigrationErrors(orgIpaCode, uploadId, accessToken);

    Assertions.assertSame(expectedResponse, response);
    Mockito.verify(puMigrationFileClientMock).getMigrationErrors(
      orgIpaCode, uploadId, accessToken
    );
  }
}
