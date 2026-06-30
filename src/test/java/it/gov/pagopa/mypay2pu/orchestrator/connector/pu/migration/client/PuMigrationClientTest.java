package it.gov.pagopa.mypay2pu.orchestrator.connector.pu.migration.client;

import it.gov.pagopa.mypay2pu.orchestrator.connector.pu.migration.config.PuMigrationApisHolder;
import it.gov.pagopa.pu.migration.controller.generated.MigrationFileApi;
import it.gov.pagopa.pu.migration.dto.generated.MigrationFileTypeEnum;
import it.gov.pagopa.pu.migration.dto.generated.UploadMigrationFileResponseDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@ExtendWith(MockitoExtension.class)
class PuMigrationClientTest {
  @Mock
  private PuMigrationApisHolder puMigrationApisHolderMock;
  @Mock
  private MigrationFileApi migrationApiMock;

  private PuMigrationFileClient puMigrationFileClient;

  @BeforeEach
  void setUp() {
    puMigrationFileClient = new PuMigrationFileClient(puMigrationApisHolderMock);
  }

  @AfterEach
  void verifyNoMoreInteractions() {
    Mockito.verifyNoMoreInteractions(
      puMigrationApisHolderMock
    );
  }

  @Test
  void whenUploadMigrationFileThenInvokeWithAccessToken() {
    // Given
    String accessToken = "accessToken";
    String orgIpaCode = "orgIpaCode";
    MigrationFileTypeEnum fileType = MigrationFileTypeEnum.ORGANIZATIONS;
    String fileName = "fileName";
    String fileContent = "BODY";
    byte[] fileContentBytes = fileContent.getBytes();

    UploadMigrationFileResponseDTO expectedResult = new UploadMigrationFileResponseDTO();

    Mockito.when(puMigrationApisHolderMock.getMigrationFileApi(accessToken))
      .thenReturn(migrationApiMock);
    Mockito.when(migrationApiMock.uploadMigrationFile(Mockito.same(orgIpaCode), Mockito.same(fileType),
        Mockito.argThat(argument -> {
          try {
            return argument != null && fileName.equals(argument.getFilename()) && fileContent.equals(argument.getContentAsString(StandardCharsets.UTF_8));
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        })
      ))
      .thenReturn(expectedResult);

    // When
    UploadMigrationFileResponseDTO result = puMigrationFileClient.uploadMigrationFile(orgIpaCode, fileType, fileName, fileContentBytes, accessToken);

    // Then
    Assertions.assertSame(expectedResult, result);
  }
}
