package it.gov.pagopa.mypay2pu.orchestrator.service.operations;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.config.MigrationTransferProperties;
import it.gov.pagopa.mypay2pu.orchestrator.connector.pu.auth.PuAuthnService;
import it.gov.pagopa.mypay2pu.orchestrator.connector.pu.migration.PuMigrationService;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationFileExecutionStatus;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationRequestDto;
import it.gov.pagopa.mypay2pu.orchestrator.exception.TransferPhaseException;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationFileExecution;
import it.gov.pagopa.mypay2pu.orchestrator.service.MigrationFileExecutionTransferService;
import it.gov.pagopa.pu.migration.dto.generated.MigrationFileTypeEnum;
import it.gov.pagopa.pu.migration.dto.generated.UploadMigrationFileResponseDTO;
import it.gov.pagopa.pu.migration.dto.generated.UploadsStatusEnum;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AbstractMigrationTransferOperationTest {

  private static final UUID MIGRATION_ID = UUID.fromString("6c4a0f25-3709-43b7-9e03-3d457c7aa14a");
  private static final LocalDateTime STARTED_AT = LocalDateTime.of(2026, Month.NOVEMBER, 11, 10, 55);
  private static final String BROKER_IPA_CODE = "BROKER_IPA";

  @TempDir
  private Path extractorOutputPath;

  @Mock
  private MigrationFileExecutionTransferService transferServiceMock;
  @Mock
  private PuMigrationService puMigrationServiceMock;
  @Mock
  private PuAuthnService puAuthnServiceMock;
  @Mock
  private UploadMigrationFileResponseDTO uploadResponseMock;

  @AfterEach
  void verifyMocks() {
    verifyNoMoreInteractions(
      transferServiceMock,
      puMigrationServiceMock,
      puAuthnServiceMock,
      uploadResponseMock
    );
  }

  @Test
  void givenOrganizationDataAndErrorsArchivesWhenExecuteThenUploadsOnlyDataWithBrokerIpa() throws IOException {
    String dataArchive = "PT_MYPAY2PU_DEV-ORGANIZATIONS-20261111105510-1_0.zip";
    MigrationFileExecution fileExecution = exportedExecution(
      MigrationFileType.ORGANIZATIONS,
      dataArchive + ",PT_MYPAY2PU_DEV-ORGANIZATIONS-20261111105510-1_0.errors.zip"
    );
    Files.createFile(extractorOutputPath.resolve(dataArchive));
    when(transferServiceMock.getRequired(MIGRATION_ID, MigrationFileType.ORGANIZATIONS)).thenReturn(fileExecution);
    when(transferServiceMock.markTransferring(
      eq(MIGRATION_ID), eq(MigrationFileType.ORGANIZATIONS), argThat(Objects::nonNull)
    )).thenReturn(fileExecution);
    when(transferServiceMock.recordUploadAccepted(
      eq(MIGRATION_ID),
      eq(MigrationFileType.ORGANIZATIONS),
      eq(42L),
      eq(UploadsStatusEnum.UPLOADED),
      argThat(Objects::nonNull)
    )).thenReturn(fileExecution);
    when(uploadResponseMock.getUploadId()).thenReturn(42L);
    when(puAuthnServiceMock.getAccessToken()).thenReturn("access-token");
    when(puMigrationServiceMock.uploadMigrationFile(
      eq(BROKER_IPA_CODE),
      eq(MigrationFileTypeEnum.ORGANIZATIONS),
      argThat(resource -> dataArchive.equals(resource.getFilename()) && resource.exists()),
      eq("access-token")
    )).thenReturn(uploadResponseMock);

    operation(MigrationFileType.ORGANIZATIONS).execute(request(List.of("ORG_IPA")));

    verify(transferServiceMock).getRequired(MIGRATION_ID, MigrationFileType.ORGANIZATIONS);
    verify(transferServiceMock).markTransferring(
      eq(MIGRATION_ID), eq(MigrationFileType.ORGANIZATIONS), argThat(Objects::nonNull)
    );
    verify(transferServiceMock).recordUploadAccepted(
      eq(MIGRATION_ID),
      eq(MigrationFileType.ORGANIZATIONS),
      eq(42L),
      eq(UploadsStatusEnum.UPLOADED),
      argThat(Objects::nonNull)
    );
    verify(puAuthnServiceMock).getAccessToken();
    verify(puMigrationServiceMock).uploadMigrationFile(
      eq(BROKER_IPA_CODE),
      eq(MigrationFileTypeEnum.ORGANIZATIONS),
      argThat(resource -> dataArchive.equals(resource.getFilename()) && resource.exists()),
      eq("access-token")
    );
    verify(uploadResponseMock).getUploadId();
  }

  @Test
  void givenMultipleDataArchivesWhenExecuteThenPersistsFailureWithoutUploading() {
    MigrationFileExecution fileExecution = exportedExecution(
      MigrationFileType.PAYMENTS_REPORTING_MYPAY,
      "R_LOMBAR-PAYMENTS_REPORTING_MYPAY-1.zip,IPACODE1-PAYMENTS_REPORTING_MYPAY-2.zip"
    );
    when(transferServiceMock.getRequired(MIGRATION_ID, MigrationFileType.PAYMENTS_REPORTING_MYPAY))
      .thenReturn(fileExecution);
    when(transferServiceMock.markTransferFailed(
      MIGRATION_ID,
      MigrationFileType.PAYMENTS_REPORTING_MYPAY,
      "TRANSFER_MULTIPLE_ARCHIVES_NOT_SUPPORTED",
      "Expected one data archive for migration %s and file type PAYMENTS_REPORTING_MYPAY, found 2"
        .formatted(MIGRATION_ID)
    )).thenReturn(fileExecution);

    AbstractMigrationTransferOperation transferOperation =
      operation(MigrationFileType.PAYMENTS_REPORTING_MYPAY);
    MigrationRequestDto migrationRequest = request(List.of("R_LOMBAR", "IPACODE1"));
    TransferPhaseException exception = assertThrows(
      TransferPhaseException.class,
      () -> transferOperation.execute(migrationRequest)
    );

    assertEquals("TRANSFER_MULTIPLE_ARCHIVES_NOT_SUPPORTED", exception.getCode());
    verify(transferServiceMock).getRequired(MIGRATION_ID, MigrationFileType.PAYMENTS_REPORTING_MYPAY);
    verify(transferServiceMock).markTransferFailed(
      MIGRATION_ID,
      MigrationFileType.PAYMENTS_REPORTING_MYPAY,
      "TRANSFER_MULTIPLE_ARCHIVES_NOT_SUPPORTED",
      "Expected one data archive for migration %s and file type PAYMENTS_REPORTING_MYPAY, found 2"
        .formatted(MIGRATION_ID)
    );
  }

  @Test
  void givenPaymentReportingArchiveWithMultipleIpaCodesWhenExecuteThenUsesCodeInArchiveName() throws IOException {
    String dataArchive = "PT_MYPAY2PU_DEV-R_LOMBAR-PAYMENTS_REPORTING_MYPAY-20261111105512-part001-1.0.zip";
    MigrationFileExecution fileExecution = exportedExecution(
      MigrationFileType.PAYMENTS_REPORTING_MYPAY,
      dataArchive + ",PT_MYPAY2PU_DEV-R_LOMBAR-PAYMENTS_REPORTING_MYPAY-20261111105512-1.0.errors.zip"
    );
    Files.createFile(extractorOutputPath.resolve(dataArchive));
    when(transferServiceMock.getRequired(MIGRATION_ID, MigrationFileType.PAYMENTS_REPORTING_MYPAY))
      .thenReturn(fileExecution);
    when(transferServiceMock.markTransferring(
      eq(MIGRATION_ID), eq(MigrationFileType.PAYMENTS_REPORTING_MYPAY), argThat(Objects::nonNull)
    )).thenReturn(fileExecution);
    when(transferServiceMock.recordUploadAccepted(
      eq(MIGRATION_ID),
      eq(MigrationFileType.PAYMENTS_REPORTING_MYPAY),
      eq(42L),
      eq(UploadsStatusEnum.UPLOADED),
      argThat(Objects::nonNull)
    )).thenReturn(fileExecution);
    when(uploadResponseMock.getUploadId()).thenReturn(42L);
    when(puAuthnServiceMock.getAccessToken()).thenReturn("access-token");
    when(puMigrationServiceMock.uploadMigrationFile(
      eq("R_LOMBAR"),
      eq(MigrationFileTypeEnum.PAYMENTS_REPORTING),
      argThat(resource -> dataArchive.equals(resource.getFilename()) && resource.exists()),
      eq("access-token")
    )).thenReturn(uploadResponseMock);

    operation(MigrationFileType.PAYMENTS_REPORTING_MYPAY)
      .execute(request(List.of("R_LOMBAR", "IPACODE1")));

    verify(transferServiceMock).getRequired(MIGRATION_ID, MigrationFileType.PAYMENTS_REPORTING_MYPAY);
    verify(transferServiceMock).markTransferring(
      eq(MIGRATION_ID), eq(MigrationFileType.PAYMENTS_REPORTING_MYPAY), argThat(Objects::nonNull)
    );
    verify(transferServiceMock).recordUploadAccepted(
      eq(MIGRATION_ID),
      eq(MigrationFileType.PAYMENTS_REPORTING_MYPAY),
      eq(42L),
      eq(UploadsStatusEnum.UPLOADED),
      argThat(Objects::nonNull)
    );
    verify(puAuthnServiceMock).getAccessToken();
    verify(puMigrationServiceMock).uploadMigrationFile(
      eq("R_LOMBAR"),
      eq(MigrationFileTypeEnum.PAYMENTS_REPORTING),
      argThat(resource -> dataArchive.equals(resource.getFilename()) && resource.exists()),
      eq("access-token")
    );
    verify(uploadResponseMock).getUploadId();
  }

  @Test
  void givenWaitingPuExecutionWhenExecuteThenDoesNotSubmitDuplicateUpload() {
    MigrationFileExecution fileExecution = exportedExecution(
      MigrationFileType.ORGANIZATIONS,
      "organizations.zip"
    );
    fileExecution.setStatus(MigrationFileExecutionStatus.WAITING_PU);
    fileExecution.setUploadId(42L);
    when(transferServiceMock.getRequired(MIGRATION_ID, MigrationFileType.ORGANIZATIONS)).thenReturn(fileExecution);

    operation(MigrationFileType.ORGANIZATIONS).execute(request(List.of("ORG_IPA")));

    verify(transferServiceMock).getRequired(MIGRATION_ID, MigrationFileType.ORGANIZATIONS);
  }

  @Test
  void givenUploadResponseWithoutIdWhenExecuteThenPersistsTransferFailure() throws IOException {
    String dataArchive = "organizations.zip";
    MigrationFileExecution fileExecution = exportedExecution(MigrationFileType.ORGANIZATIONS, dataArchive);
    Files.createFile(extractorOutputPath.resolve(dataArchive));
    when(transferServiceMock.getRequired(MIGRATION_ID, MigrationFileType.ORGANIZATIONS)).thenReturn(fileExecution);
    when(transferServiceMock.markTransferring(
      eq(MIGRATION_ID), eq(MigrationFileType.ORGANIZATIONS), argThat(Objects::nonNull)
    )).thenReturn(fileExecution);
    when(transferServiceMock.markTransferFailed(
      MIGRATION_ID,
      MigrationFileType.ORGANIZATIONS,
      "TRANSFER_INVALID_UPLOAD_RESPONSE",
      "PU returned no valid upload ID for migration %s and file type ORGANIZATIONS".formatted(MIGRATION_ID)
    )).thenReturn(fileExecution);
    when(uploadResponseMock.getUploadId()).thenReturn(null);
    when(puAuthnServiceMock.getAccessToken()).thenReturn("access-token");
    when(puMigrationServiceMock.uploadMigrationFile(
      eq(BROKER_IPA_CODE),
      eq(MigrationFileTypeEnum.ORGANIZATIONS),
      argThat(resource -> dataArchive.equals(resource.getFilename()) && resource.exists()),
      eq("access-token")
    )).thenReturn(uploadResponseMock);

    AbstractMigrationTransferOperation transferOperation = operation(MigrationFileType.ORGANIZATIONS);
    MigrationRequestDto migrationRequest = request(List.of("ORG_IPA"));
    TransferPhaseException exception = assertThrows(
      TransferPhaseException.class,
      () -> transferOperation.execute(migrationRequest)
    );

    assertEquals("TRANSFER_INVALID_UPLOAD_RESPONSE", exception.getCode());
    verify(transferServiceMock).getRequired(MIGRATION_ID, MigrationFileType.ORGANIZATIONS);
    verify(transferServiceMock).markTransferring(
      eq(MIGRATION_ID), eq(MigrationFileType.ORGANIZATIONS), argThat(Objects::nonNull)
    );
    verify(transferServiceMock).markTransferFailed(
      MIGRATION_ID,
      MigrationFileType.ORGANIZATIONS,
      "TRANSFER_INVALID_UPLOAD_RESPONSE",
      "PU returned no valid upload ID for migration %s and file type ORGANIZATIONS".formatted(MIGRATION_ID)
    );
    verify(puAuthnServiceMock).getAccessToken();
    verify(puMigrationServiceMock).uploadMigrationFile(
      eq(BROKER_IPA_CODE),
      eq(MigrationFileTypeEnum.ORGANIZATIONS),
      argThat(resource -> dataArchive.equals(resource.getFilename()) && resource.exists()),
      eq("access-token")
    );
    verify(uploadResponseMock).getUploadId();
  }

  private AbstractMigrationTransferOperation operation(MigrationFileType fileType) {
    return new AbstractMigrationTransferOperation(
      fileType,
      transferServiceMock,
      puMigrationServiceMock,
      puAuthnServiceMock,
      new MigrationTransferProperties(
        new MigrationTransferProperties.Storage(extractorOutputPath.toString()),
        new MigrationTransferProperties.Transfer(BROKER_IPA_CODE)
      )
    ) { };
  }

  private MigrationRequestDto request(List<String> ipaCodes) {
    MigrationRequestDto request = new MigrationRequestDto();
    request.setMigrationId(MIGRATION_ID);
    request.setIpaCodes(ipaCodes);
    return request;
  }

  private MigrationFileExecution exportedExecution(MigrationFileType fileType, String filePath) {
    return MigrationFileExecution.builder()
      .id(UUID.randomUUID())
      .migrationId(MIGRATION_ID)
      .fileType(fileType)
      .status(MigrationFileExecutionStatus.EXPORTED)
      .extractionId("extractor-123")
      .filePath(filePath)
      .extractionCompletedAt(STARTED_AT)
      .build();
  }
}
