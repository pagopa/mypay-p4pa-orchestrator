package it.gov.pagopa.mypay2pu.orchestrator.connector.pu.migration.client;

import it.gov.pagopa.mypay2pu.orchestrator.connector.pu.auth.PuAuthnService;
import it.gov.pagopa.mypay2pu.orchestrator.connector.pu.migration.PuMigrationService;
import it.gov.pagopa.mypay2pu.orchestrator.connector.pu.migration.PuMigrationServiceImpl;
import it.gov.pagopa.mypay2pu.orchestrator.connector.pu.migration.config.PuMigrationApisHolder;
import it.gov.pagopa.mypay2pu.orchestrator.connector.pu.migration.mapper.PuMigrationFileTypeMapper;
import it.gov.pagopa.mypay2pu.orchestrator.exception.InvalidValueException;
import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.pu.migration.controller.generated.MigrationFileApi;
import it.gov.pagopa.pu.migration.dto.generated.IngestionFlowFileStatus;
import it.gov.pagopa.pu.migration.dto.generated.IngestionFlowFileTypeEnum;
import it.gov.pagopa.pu.migration.dto.generated.MigrationFileTypeEnum;
import it.gov.pagopa.pu.migration.dto.generated.UploadDetails;
import it.gov.pagopa.pu.migration.dto.generated.UploadMigrationFileResponseDTO;
import it.gov.pagopa.pu.migration.dto.generated.Uploads;
import it.gov.pagopa.pu.migration.dto.generated.UploadsStatusEnum;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

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

  @ParameterizedTest
  @EnumSource(UploadsStatusEnum.class)
  void givenUploadIdWhenGetMigrationUploadThenReturnsUploadStatus(UploadsStatusEnum status) {
    String orgIpaCode = "orgIpaCode";
    Long uploadId = 123L;
    Uploads expectedResponse = new Uploads().uploadId(uploadId).status(status);
    Mockito.when(puMigrationApisHolderMock.getMigrationFileApi(ACCESS_TOKEN))
      .thenReturn(migrationApiMock);
    Mockito.when(migrationApiMock.getMigrationUpload(orgIpaCode, uploadId)).thenReturn(expectedResponse);

    Uploads response = puMigrationFileClient.getMigrationUpload(orgIpaCode, uploadId, ACCESS_TOKEN);

    Assertions.assertSame(expectedResponse, response);
    Mockito.verify(puMigrationApisHolderMock).getMigrationFileApi(ACCESS_TOKEN);
    Mockito.verify(migrationApiMock).getMigrationUpload(orgIpaCode, uploadId);
  }

  @ParameterizedTest
  @MethodSource("invalidUploadResponses")
  void givenInvalidUploadResponseWhenGetMigrationUploadThenRejectsResponse(Uploads upload) {
    String orgIpaCode = "orgIpaCode";
    Long uploadId = 123L;
    Mockito.when(puMigrationApisHolderMock.getMigrationFileApi(ACCESS_TOKEN))
      .thenReturn(migrationApiMock);
    Mockito.when(migrationApiMock.getMigrationUpload(orgIpaCode, uploadId)).thenReturn(upload);

    InvalidValueException failure = Assertions.assertThrows(InvalidValueException.class,
      () -> puMigrationFileClient.getMigrationUpload(orgIpaCode, uploadId, ACCESS_TOKEN));

    Assertions.assertEquals("TRANSFER_OUTCOME_INVALID_RESPONSE", failure.getCode());
    Assertions.assertEquals("PU returned a missing upload or status for upload 123", failure.getMessage());
    Mockito.verify(puMigrationApisHolderMock).getMigrationFileApi(ACCESS_TOKEN);
    Mockito.verify(migrationApiMock).getMigrationUpload(orgIpaCode, uploadId);
  }

  static Stream<Arguments> invalidUploadResponses() {
    return Stream.of(
      Arguments.of((Uploads) null),
      Arguments.of(new Uploads().uploadId(123L))
    );
  }

  @ParameterizedTest
  @EnumSource(IngestionFlowFileStatus.class)
  void givenUploadIdWhenGetMigrationUploadDetailsThenReturnsDetails(IngestionFlowFileStatus status) {
    String orgIpaCode = "orgIpaCode";
    Long uploadId = 123L;
    List<UploadDetails> expectedResponse = List.of(uploadDetail().status(status));
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

  private static UploadDetails uploadDetail() {
    return new UploadDetails().uploadId(123L).organizationId(1L)
      .ingestionFlowFileType(IngestionFlowFileTypeEnum.ORGANIZATIONS)
      .filePathName("organizations.csv").fileName("organizations.csv")
      .fileSize(500L).status(IngestionFlowFileStatus.PROCESSING);
  }

  @ParameterizedTest
  @MethodSource("invalidDetailResponses")
  void givenInvalidDetailResponseWhenGetMigrationUploadDetailsThenRejectsResponse(List<UploadDetails> details) {
    String orgIpaCode = "orgIpaCode";
    Long uploadId = 123L;
    Mockito.when(puMigrationApisHolderMock.getMigrationFileApi(ACCESS_TOKEN))
      .thenReturn(migrationApiMock);
    Mockito.when(migrationApiMock.getMigrationUploadDetails(orgIpaCode, uploadId)).thenReturn(details);

    InvalidValueException failure = Assertions.assertThrows(InvalidValueException.class,
      () -> puMigrationFileClient.getMigrationUploadDetails(orgIpaCode, uploadId, ACCESS_TOKEN));

    Assertions.assertEquals("TRANSFER_OUTCOME_INVALID_RESPONSE", failure.getCode());
    Assertions.assertEquals("PU returned invalid details for upload 123", failure.getMessage());
    Mockito.verify(puMigrationApisHolderMock).getMigrationFileApi(ACCESS_TOKEN);
    Mockito.verify(migrationApiMock).getMigrationUploadDetails(orgIpaCode, uploadId);
  }

  static Stream<Arguments> invalidDetailResponses() {
    return Stream.of(
      Arguments.of((List<UploadDetails>) null),
      Arguments.of(Collections.singletonList(null)),
      Arguments.of(List.of(uploadDetail().uploadId(null))),
      Arguments.of(List.of(uploadDetail().organizationId(null))),
      Arguments.of(List.of(uploadDetail().ingestionFlowFileType(null))),
      Arguments.of(List.of(uploadDetail().filePathName(null))),
      Arguments.of(List.of(uploadDetail().fileName(null))),
      Arguments.of(List.of(uploadDetail().fileSize(null))),
      Arguments.of(List.of(uploadDetail().status(null)))
    );
  }

  @Test
  void givenEmptyDetailResponseWhenGetMigrationUploadDetailsThenReturnsEmptyList() {
    String orgIpaCode = "orgIpaCode";
    Long uploadId = 123L;
    List<UploadDetails> expectedResponse = List.of();
    Mockito.when(puMigrationApisHolderMock.getMigrationFileApi(ACCESS_TOKEN))
      .thenReturn(migrationApiMock);
    Mockito.when(migrationApiMock.getMigrationUploadDetails(orgIpaCode, uploadId)).thenReturn(expectedResponse);

    List<UploadDetails> response =
      puMigrationFileClient.getMigrationUploadDetails(orgIpaCode, uploadId, ACCESS_TOKEN);

    Assertions.assertSame(expectedResponse, response);
    Mockito.verify(puMigrationApisHolderMock).getMigrationFileApi(ACCESS_TOKEN);
    Mockito.verify(migrationApiMock).getMigrationUploadDetails(orgIpaCode, uploadId);
  }

  @Test
  void givenOpenApiValidDetailWithoutDomainRequirementsWhenGetMigrationUploadDetailsThenReturnsPayload() {
    String orgIpaCode = "orgIpaCode";
    Long uploadId = 123L;
    List<UploadDetails> expectedResponse = List.of(uploadDetail().fileName(" "));
    Mockito.when(puMigrationApisHolderMock.getMigrationFileApi(ACCESS_TOKEN))
      .thenReturn(migrationApiMock);
    Mockito.when(migrationApiMock.getMigrationUploadDetails(orgIpaCode, uploadId)).thenReturn(expectedResponse);

    List<UploadDetails> response =
      puMigrationFileClient.getMigrationUploadDetails(orgIpaCode, uploadId, ACCESS_TOKEN);

    Assertions.assertSame(expectedResponse, response);
    Assertions.assertNull(response.getFirst().getUploadDetailId());
    Assertions.assertEquals(" ", response.getFirst().getFileName());
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
    Uploads expectedResponse = new Uploads().uploadId(uploadId).status(UploadsStatusEnum.PROCESSING);
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
    List<UploadDetails> expectedResponse = List.of(uploadDetail());
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

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "\t"})
  void givenInvalidBrokerIpaCodeWhenConstructThenThrowsInvalidValueException(String brokerIpaCode) {
    InvalidValueException exception = Assertions.assertThrows(
      InvalidValueException.class,
      () -> new PuMigrationServiceImpl(
        puMigrationFileClientMock, brokerIpaCode, puAuthnServiceMock, puMigrationFileTypeMapper
      )
    );

    Assertions.assertEquals("TRANSFER_BROKER_IPA_CODE_MISSING", exception.getCode());
  }

  private PuMigrationService migrationService(String brokerIpaCode) {
    Mockito.when(puAuthnServiceMock.getAccessToken()).thenReturn(ACCESS_TOKEN);
    return new PuMigrationServiceImpl(
      puMigrationFileClientMock,
      brokerIpaCode,
      puAuthnServiceMock,
      puMigrationFileTypeMapper
    );
  }

}
