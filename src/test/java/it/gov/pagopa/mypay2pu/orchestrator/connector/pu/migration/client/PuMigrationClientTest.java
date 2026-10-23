package it.gov.pagopa.mypay2pu.orchestrator.connector.pu.migration.client;

import it.gov.pagopa.mypay2pu.orchestrator.connector.pu.migration.config.PuMigrationApisHolder;
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
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@ExtendWith(MockitoExtension.class)
class PuMigrationClientTest {

  private static final String ACCESS_TOKEN = "accessToken";

  @Mock
  private PuMigrationApisHolder puMigrationApisHolderMock;
  @Mock
  private MigrationFileApi migrationApiMock;

  private PuMigrationFileClient puMigrationFileClient;

  @BeforeEach
  void setUp() {
    Mockito.when(puMigrationApisHolderMock.getMigrationFileApi(ACCESS_TOKEN))
      .thenReturn(migrationApiMock);
    puMigrationFileClient = new PuMigrationFileClient(puMigrationApisHolderMock);
  }

  @AfterEach
  void verifyNoMoreInteractions() {
    Mockito.verifyNoMoreInteractions(
      puMigrationApisHolderMock,
      migrationApiMock
    );
  }

  @Test
  void whenUploadMigrationFileThenInvokeWithAccessToken() {
    // Given
    String orgIpaCode = "orgIpaCode";
    MigrationFileTypeEnum fileType = MigrationFileTypeEnum.ORGANIZATIONS;
    String fileName = "fileName";
    String fileContent = "BODY";
    byte[] fileContentBytes = fileContent.getBytes();
    ArgumentMatcher<Resource> expectedFile = argument -> {
      try {
        return argument != null
          && fileName.equals(argument.getFilename())
          && fileContent.equals(argument.getContentAsString(StandardCharsets.UTF_8));
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    };

    UploadMigrationFileResponseDTO expectedResult =
      new UploadMigrationFileResponseDTO(123L, "workflowId", "runId");

    Mockito.when(migrationApiMock.uploadMigrationFile(Mockito.same(orgIpaCode), Mockito.same(fileType),
        Mockito.argThat(expectedFile)
      ))
      .thenReturn(expectedResult);

    // When
    UploadMigrationFileResponseDTO result = puMigrationFileClient.uploadMigrationFile(
      orgIpaCode, fileType, fileName, fileContentBytes, ACCESS_TOKEN
    );

    // Then
    Assertions.assertSame(expectedResult, result);
    Assertions.assertEquals("workflowId", result.getWorkflowId());
    Mockito.verify(puMigrationApisHolderMock).getMigrationFileApi(ACCESS_TOKEN);
    Mockito.verify(migrationApiMock).uploadMigrationFile(
      Mockito.same(orgIpaCode), Mockito.same(fileType), Mockito.argThat(expectedFile)
    );
  }

  @Test
  void givenUploadIdWhenGetMigrationUploadThenReturnsUploadStatus() {
    String orgIpaCode = "orgIpaCode";
    Long uploadId = 123L;
    Uploads expectedResponse = new Uploads();
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
    Mockito.when(migrationApiMock.getMigrationErrors(orgIpaCode, uploadId)).thenReturn(expectedResponse);

    Resource response = puMigrationFileClient.getMigrationErrors(orgIpaCode, uploadId, ACCESS_TOKEN);

    Assertions.assertSame(expectedResponse, response);
    Mockito.verify(puMigrationApisHolderMock).getMigrationFileApi(ACCESS_TOKEN);
    Mockito.verify(migrationApiMock).getMigrationErrors(orgIpaCode, uploadId);
  }
}
