package it.gov.pagopa.mypay2pu.orchestrator.connector.pu.migration.client;

import it.gov.pagopa.mypay2pu.orchestrator.connector.pu.auth.PuAuthnService;
import it.gov.pagopa.mypay2pu.orchestrator.connector.pu.migration.PuMigrationService;
import it.gov.pagopa.mypay2pu.orchestrator.connector.pu.migration.PuMigrationServiceImpl;
import it.gov.pagopa.mypay2pu.orchestrator.connector.pu.migration.config.PuMigrationApiClientConfig;
import it.gov.pagopa.mypay2pu.orchestrator.connector.pu.migration.config.PuMigrationApisHolder;
import it.gov.pagopa.mypay2pu.orchestrator.connector.pu.migration.mapper.PuMigrationFileTypeMapper;
import it.gov.pagopa.mypay2pu.orchestrator.exception.InvalidValueException;
import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
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
  @Mock
  private PuAuthnService puAuthnServiceMock;

  private PuMigrationFileClient puMigrationFileClient;
  private PuMigrationFileTypeMapper puMigrationFileTypeMapper;

  @BeforeEach
  void setUp() {
    puMigrationFileClient = new PuMigrationFileClient(puMigrationApisHolderMock);
    puMigrationFileTypeMapper = new PuMigrationFileTypeMapper();
  }

  @AfterEach
  void verifyNoMoreInteractions() {
    Mockito.verifyNoMoreInteractions(
      puMigrationApisHolderMock,
      migrationApiMock,
      puMigrationFileClientMock,
      puAuthnServiceMock
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
  void givenFileTypeWhenServiceUploadsMigrationFileThenInvokeClientWithMappedType() {
    String orgIpaCode = "orgIpaCode";
    Resource file = new ByteArrayResource(new byte[] {1, 2, 3});
    String accessToken = "accessToken";
    UploadMigrationFileResponseDTO expectedResponse =
      new UploadMigrationFileResponseDTO(123L, "workflowId", "runId");
    Mockito.when(puMigrationFileClientMock.uploadMigrationFile(
      orgIpaCode, MigrationFileTypeEnum.ORGANIZATIONS, file, accessToken
    )).thenReturn(expectedResponse);
    PuMigrationService service = migrationService(orgIpaCode);

    UploadMigrationFileResponseDTO response =
      service.uploadMigrationFile(MigrationFileType.ORGANIZATIONS, file);

    Assertions.assertSame(expectedResponse, response);
    Mockito.verify(puMigrationFileClientMock).uploadMigrationFile(
      orgIpaCode, MigrationFileTypeEnum.ORGANIZATIONS, file, accessToken
    );
    Mockito.verify(puAuthnServiceMock).getAccessToken();
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
    PuMigrationService service = migrationService(orgIpaCode);

    Uploads response = service.getMigrationUpload(uploadId);

    Assertions.assertSame(expectedResponse, response);
    Mockito.verify(puMigrationFileClientMock).getMigrationUpload(
      orgIpaCode, uploadId, accessToken
    );
    Mockito.verify(puAuthnServiceMock).getAccessToken();
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
    PuMigrationService service = migrationService(orgIpaCode);

    List<UploadDetails> response =
      service.getMigrationUploadDetails(uploadId);

    Assertions.assertSame(expectedResponse, response);
    Mockito.verify(puMigrationFileClientMock).getMigrationUploadDetails(
      orgIpaCode, uploadId, accessToken
    );
    Mockito.verify(puAuthnServiceMock).getAccessToken();
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
    PuMigrationService service = migrationService(orgIpaCode);

    Resource response = service.getMigrationErrors(uploadId);

    Assertions.assertSame(expectedResponse, response);
    Mockito.verify(puMigrationFileClientMock).getMigrationErrors(
      orgIpaCode, uploadId, accessToken
    );
    Mockito.verify(puAuthnServiceMock).getAccessToken();
  }

  @Test
  void givenBlankBrokerIpaCodeWhenUploadMigrationFileThenThrowsInvalidValueException() {
    PuMigrationService service = new PuMigrationServiceImpl(
      puMigrationFileClientMock, migrationConfig(" "), puAuthnServiceMock, puMigrationFileTypeMapper
    );
    Resource archive = new ByteArrayResource(new byte[] {1});

    InvalidValueException exception = Assertions.assertThrows(
      InvalidValueException.class,
      () -> service.uploadMigrationFile(
        MigrationFileType.ORGANIZATIONS,
        archive
      )
    );

    Assertions.assertEquals("TRANSFER_BROKER_IPA_CODE_MISSING", exception.getCode());
  }

  private static PuMigrationApiClientConfig migrationConfig(String brokerIpaCode) {
    return PuMigrationApiClientConfig.builder()
      .brokerIpaCode(brokerIpaCode)
      .build();
  }

  private PuMigrationService migrationService(String brokerIpaCode) {
    Mockito.when(puAuthnServiceMock.getAccessToken()).thenReturn(ACCESS_TOKEN);
    return new PuMigrationServiceImpl(
      puMigrationFileClientMock,
      migrationConfig(brokerIpaCode),
      puAuthnServiceMock,
      puMigrationFileTypeMapper
    );
  }

}
