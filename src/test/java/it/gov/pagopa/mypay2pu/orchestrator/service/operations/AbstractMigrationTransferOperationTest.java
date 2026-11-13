package it.gov.pagopa.mypay2pu.orchestrator.service.operations;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.config.MigrationTransferProperties;
import it.gov.pagopa.mypay2pu.orchestrator.connector.pu.auth.PuAuthnService;
import it.gov.pagopa.mypay2pu.orchestrator.connector.pu.migration.PuMigrationService;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationFileExecutionStatus;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationRequestDto;
import it.gov.pagopa.mypay2pu.orchestrator.exception.BaseBusinessException;
import it.gov.pagopa.mypay2pu.orchestrator.exception.ConflictException;
import it.gov.pagopa.mypay2pu.orchestrator.exception.InvalidValueException;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationFileExecution;
import it.gov.pagopa.mypay2pu.orchestrator.service.MigrationFileExecutionTransferService;
import it.gov.pagopa.pu.migration.dto.generated.MigrationFileTypeEnum;
import it.gov.pagopa.pu.migration.dto.generated.UploadMigrationFileResponseDTO;
import it.gov.pagopa.pu.migration.dto.generated.UploadsStatusEnum;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
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
    createArchive(fileExecution, dataArchive);
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
    InvalidValueException exception = assertThrows(
      InvalidValueException.class,
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
  void givenPaymentReportingArchiveWithMultipleIpaCodesWhenExecuteThenUsesBrokerIpa() throws IOException {
    String dataArchive = "PT_MYPAY2PU_DEV-BROKER_IPA-PAYMENTS_REPORTING_MYPAY-20261111105512-part001-1.0.zip";
    MigrationFileExecution fileExecution = exportedExecution(
      MigrationFileType.PAYMENTS_REPORTING_MYPAY,
      dataArchive + ",PT_MYPAY2PU_DEV-BROKER_IPA-PAYMENTS_REPORTING_MYPAY-20261111105512-1.0.errors.zip"
    );
    createArchive(fileExecution, dataArchive);
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
      eq(BROKER_IPA_CODE),
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
      eq(BROKER_IPA_CODE),
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
    createArchive(fileExecution, dataArchive);
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
    InvalidValueException exception = assertThrows(
      InvalidValueException.class,
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

  @ParameterizedTest
  @EnumSource(value = MigrationFileExecutionStatus.class, names = {"COMPLETED", "WARNING"})
  void givenTerminalExecutionWhenExecuteThenDoesNotSubmitDuplicateUpload(MigrationFileExecutionStatus status) {
    MigrationFileExecution fileExecution = exportedExecution(MigrationFileType.ORGANIZATIONS, "organizations.zip");
    fileExecution.setStatus(status);
    when(transferServiceMock.getRequired(MIGRATION_ID, MigrationFileType.ORGANIZATIONS)).thenReturn(fileExecution);

    operation(MigrationFileType.ORGANIZATIONS).execute(request(List.of("ORG_IPA")));

    verify(transferServiceMock).getRequired(MIGRATION_ID, MigrationFileType.ORGANIZATIONS);
  }

  @Test
  void givenTransferringExecutionWhenExecuteThenThrowsConflict() {
    MigrationFileExecution fileExecution = exportedExecution(MigrationFileType.ORGANIZATIONS, "organizations.zip");
    fileExecution.setStatus(MigrationFileExecutionStatus.TRANSFERING);
    when(transferServiceMock.getRequired(MIGRATION_ID, MigrationFileType.ORGANIZATIONS)).thenReturn(fileExecution);

    AbstractMigrationTransferOperation transferOperation = operation(MigrationFileType.ORGANIZATIONS);
    MigrationRequestDto migrationRequest = request(List.of("ORG_IPA"));
    ConflictException exception = assertThrows(
      ConflictException.class,
      () -> transferOperation.execute(migrationRequest)
    );

    assertEquals("INVALID_MIGRATION_FILE_EXECUTION_TRANSITION", exception.getCode());
    verify(transferServiceMock).getRequired(MIGRATION_ID, MigrationFileType.ORGANIZATIONS);
  }

  @ParameterizedTest
  @EnumSource(value = MigrationFileExecutionStatus.class, names = {"PENDING", "EXPORTING"})
  void givenExecutionNotReadyForTransferWhenExecuteThenThrowsConflict(MigrationFileExecutionStatus status) {
    MigrationFileExecution fileExecution = exportedExecution(MigrationFileType.ORGANIZATIONS, "organizations.zip");
    fileExecution.setStatus(status);
    when(transferServiceMock.getRequired(MIGRATION_ID, MigrationFileType.ORGANIZATIONS)).thenReturn(fileExecution);

    AbstractMigrationTransferOperation transferOperation = operation(MigrationFileType.ORGANIZATIONS);
    MigrationRequestDto migrationRequest = request(List.of("ORG_IPA"));
    ConflictException exception = assertThrows(
      ConflictException.class,
      () -> transferOperation.execute(migrationRequest)
    );

    assertEquals("TRANSFER_FILE_NOT_READY", exception.getCode());
    verify(transferServiceMock).getRequired(MIGRATION_ID, MigrationFileType.ORGANIZATIONS);
  }

  @Test
  void givenFailedExecutionWhenExecuteThenThrowsStoredUploadFailure() {
    MigrationFileExecution fileExecution = exportedExecution(MigrationFileType.ORGANIZATIONS, "organizations.zip");
    fileExecution.setStatus(MigrationFileExecutionStatus.FAILED);
    fileExecution.setUploadErrorCode("UPLOAD_REJECTED");
    fileExecution.setUploadErrorMsg("PU rejected the archive");
    when(transferServiceMock.getRequired(MIGRATION_ID, MigrationFileType.ORGANIZATIONS)).thenReturn(fileExecution);

    AbstractMigrationTransferOperation transferOperation = operation(MigrationFileType.ORGANIZATIONS);
    MigrationRequestDto migrationRequest = request(List.of("ORG_IPA"));
    ConflictException exception = assertThrows(
      ConflictException.class,
      () -> transferOperation.execute(migrationRequest)
    );

    assertEquals("UPLOAD_REJECTED", exception.getCode());
    assertEquals("PU rejected the archive", exception.getMessage());
    verify(transferServiceMock).getRequired(MIGRATION_ID, MigrationFileType.ORGANIZATIONS);
  }

  @Test
  void givenFailedExecutionWithExtractionFailureWhenExecuteThenThrowsExtractionFailure() {
    MigrationFileExecution fileExecution = exportedExecution(MigrationFileType.ORGANIZATIONS, "organizations.zip");
    fileExecution.setStatus(MigrationFileExecutionStatus.FAILED);
    fileExecution.setExtractionErrorCode("EXTRACTION_FAILED");
    fileExecution.setExtractionErrorMsg("Extractor failed");
    when(transferServiceMock.getRequired(MIGRATION_ID, MigrationFileType.ORGANIZATIONS)).thenReturn(fileExecution);

    AbstractMigrationTransferOperation transferOperation = operation(MigrationFileType.ORGANIZATIONS);
    MigrationRequestDto migrationRequest = request(List.of("ORG_IPA"));
    ConflictException exception = assertThrows(
      ConflictException.class,
      () -> transferOperation.execute(migrationRequest)
    );

    assertEquals("EXTRACTION_FAILED", exception.getCode());
    assertEquals("Extractor failed", exception.getMessage());
    verify(transferServiceMock).getRequired(MIGRATION_ID, MigrationFileType.ORGANIZATIONS);
  }

  @Test
  void givenFailedExecutionWithoutStoredErrorsWhenExecuteThenUsesDefaultFailure() {
    MigrationFileExecution fileExecution = exportedExecution(MigrationFileType.ORGANIZATIONS, "organizations.zip");
    fileExecution.setStatus(MigrationFileExecutionStatus.FAILED);
    when(transferServiceMock.getRequired(MIGRATION_ID, MigrationFileType.ORGANIZATIONS)).thenReturn(fileExecution);

    AbstractMigrationTransferOperation transferOperation = operation(MigrationFileType.ORGANIZATIONS);
    MigrationRequestDto migrationRequest = request(List.of("ORG_IPA"));
    ConflictException exception = assertThrows(
      ConflictException.class,
      () -> transferOperation.execute(migrationRequest)
    );

    assertEquals("TRANSFER_ALREADY_FAILED", exception.getCode());
    assertEquals("Transfer is already FAILED", exception.getMessage());
    verify(transferServiceMock).getRequired(MIGRATION_ID, MigrationFileType.ORGANIZATIONS);
  }

  @ParameterizedTest
  @MethodSource("invalidArchiveCases")
  void givenInvalidArchiveMetadataWhenExecuteThenPersistsFailure(
    String filePath,
    String extractionId,
    String expectedErrorCode
  ) {
    MigrationFileExecution fileExecution = exportedExecution(MigrationFileType.ORGANIZATIONS, filePath);
    fileExecution.setExtractionId(extractionId);
    when(transferServiceMock.getRequired(MIGRATION_ID, MigrationFileType.ORGANIZATIONS)).thenReturn(fileExecution);
    when(transferServiceMock.markTransferFailed(
      eq(MIGRATION_ID),
      eq(MigrationFileType.ORGANIZATIONS),
      eq(expectedErrorCode),
      argThat(Objects::nonNull)
    )).thenReturn(fileExecution);

    AbstractMigrationTransferOperation transferOperation = operation(MigrationFileType.ORGANIZATIONS);
    MigrationRequestDto migrationRequest = request(List.of("ORG_IPA"));
    BaseBusinessException exception = assertThrows(
      BaseBusinessException.class,
      () -> transferOperation.execute(migrationRequest)
    );

    assertEquals(expectedErrorCode, exception.getCode());
    verify(transferServiceMock).getRequired(MIGRATION_ID, MigrationFileType.ORGANIZATIONS);
    verify(transferServiceMock).markTransferFailed(
      eq(MIGRATION_ID),
      eq(MigrationFileType.ORGANIZATIONS),
      eq(expectedErrorCode),
      argThat(Objects::nonNull)
    );
  }

  @ParameterizedTest
  @MethodSource("supportedFileTypeMappings")
  void givenSupportedFileTypeWhenExecuteThenUploadsMappedPuFileType(
    MigrationFileType fileType,
    MigrationFileTypeEnum expectedPuFileType
  ) throws IOException {
    String dataArchive = "data.zip";
    MigrationFileExecution fileExecution = exportedExecution(fileType, dataArchive);
    createArchive(fileExecution, dataArchive);
    stubSuccessfulUpload(fileExecution, fileType, expectedPuFileType, dataArchive);

    AbstractMigrationTransferOperation transferOperation = operation(fileType);
    assertEquals(fileType, transferOperation.getMigrationFileType());
    transferOperation.execute(request(List.of("ORG_IPA")));

    verifySuccessfulUpload(fileType, expectedPuFileType, dataArchive);
  }

  @Test
  void givenMissingBrokerIpaCodeWhenExecuteThenPersistsTransferFailure() throws IOException {
    String dataArchive = "organizations.zip";
    MigrationFileExecution fileExecution = exportedExecution(MigrationFileType.ORGANIZATIONS, dataArchive);
    createArchive(fileExecution, dataArchive);
    when(transferServiceMock.getRequired(MIGRATION_ID, MigrationFileType.ORGANIZATIONS)).thenReturn(fileExecution);
    when(transferServiceMock.markTransferFailed(
      MIGRATION_ID,
      MigrationFileType.ORGANIZATIONS,
      "TRANSFER_BROKER_IPA_CODE_MISSING",
      "Broker IPA code is not configured"
    )).thenReturn(fileExecution);

    AbstractMigrationTransferOperation transferOperation = operation(MigrationFileType.ORGANIZATIONS, " ");
    MigrationRequestDto migrationRequest = request(List.of("ORG_IPA"));
    InvalidValueException exception = assertThrows(
      InvalidValueException.class,
      () -> transferOperation.execute(migrationRequest)
    );

    assertEquals("TRANSFER_BROKER_IPA_CODE_MISSING", exception.getCode());
    verify(transferServiceMock).getRequired(MIGRATION_ID, MigrationFileType.ORGANIZATIONS);
    verify(transferServiceMock).markTransferFailed(
      MIGRATION_ID,
      MigrationFileType.ORGANIZATIONS,
      "TRANSFER_BROKER_IPA_CODE_MISSING",
      "Broker IPA code is not configured"
    );
  }

  @Test
  void givenNullUploadResponseWhenExecuteThenPersistsTransferFailure() throws IOException {
    String dataArchive = "organizations.zip";
    MigrationFileExecution fileExecution = exportedExecution(MigrationFileType.ORGANIZATIONS, dataArchive);
    createArchive(fileExecution, dataArchive);
    when(transferServiceMock.getRequired(MIGRATION_ID, MigrationFileType.ORGANIZATIONS)).thenReturn(fileExecution);
    stubTransferClaim(fileExecution, MigrationFileType.ORGANIZATIONS);
    when(transferServiceMock.markTransferFailed(
      eq(MIGRATION_ID),
      eq(MigrationFileType.ORGANIZATIONS),
      eq("TRANSFER_INVALID_UPLOAD_RESPONSE"),
      argThat(Objects::nonNull)
    )).thenReturn(fileExecution);
    when(puAuthnServiceMock.getAccessToken()).thenReturn("access-token");
    when(puMigrationServiceMock.uploadMigrationFile(
      eq(BROKER_IPA_CODE),
      eq(MigrationFileTypeEnum.ORGANIZATIONS),
      argThat(resource -> dataArchive.equals(resource.getFilename()) && resource.exists()),
      eq("access-token")
    )).thenReturn(null);

    AbstractMigrationTransferOperation transferOperation = operation(MigrationFileType.ORGANIZATIONS);
    MigrationRequestDto migrationRequest = request(List.of("ORG_IPA"));
    InvalidValueException exception = assertThrows(
      InvalidValueException.class,
      () -> transferOperation.execute(migrationRequest)
    );

    assertEquals("TRANSFER_INVALID_UPLOAD_RESPONSE", exception.getCode());
    verify(transferServiceMock).getRequired(MIGRATION_ID, MigrationFileType.ORGANIZATIONS);
    verifyTransferClaim(MigrationFileType.ORGANIZATIONS);
    verify(transferServiceMock).markTransferFailed(
      eq(MIGRATION_ID),
      eq(MigrationFileType.ORGANIZATIONS),
      eq("TRANSFER_INVALID_UPLOAD_RESPONSE"),
      argThat(Objects::nonNull)
    );
    verify(puAuthnServiceMock).getAccessToken();
    verify(puMigrationServiceMock).uploadMigrationFile(
      eq(BROKER_IPA_CODE),
      eq(MigrationFileTypeEnum.ORGANIZATIONS),
      argThat(resource -> dataArchive.equals(resource.getFilename()) && resource.exists()),
      eq("access-token")
    );
  }

  @ParameterizedTest
  @ValueSource(longs = {0, -1})
  void givenNonPositiveUploadIdWhenExecuteThenPersistsTransferFailure(Long uploadId) throws IOException {
    String dataArchive = "organizations.zip";
    MigrationFileExecution fileExecution = exportedExecution(MigrationFileType.ORGANIZATIONS, dataArchive);
    createArchive(fileExecution, dataArchive);
    when(transferServiceMock.getRequired(MIGRATION_ID, MigrationFileType.ORGANIZATIONS)).thenReturn(fileExecution);
    stubTransferClaim(fileExecution, MigrationFileType.ORGANIZATIONS);
    when(transferServiceMock.markTransferFailed(
      eq(MIGRATION_ID),
      eq(MigrationFileType.ORGANIZATIONS),
      eq("TRANSFER_INVALID_UPLOAD_RESPONSE"),
      argThat(Objects::nonNull)
    )).thenReturn(fileExecution);
    when(uploadResponseMock.getUploadId()).thenReturn(uploadId);
    when(puAuthnServiceMock.getAccessToken()).thenReturn("access-token");
    when(puMigrationServiceMock.uploadMigrationFile(
      eq(BROKER_IPA_CODE),
      eq(MigrationFileTypeEnum.ORGANIZATIONS),
      argThat(resource -> dataArchive.equals(resource.getFilename()) && resource.exists()),
      eq("access-token")
    )).thenReturn(uploadResponseMock);

    AbstractMigrationTransferOperation transferOperation = operation(MigrationFileType.ORGANIZATIONS);
    MigrationRequestDto migrationRequest = request(List.of("ORG_IPA"));
    InvalidValueException exception = assertThrows(
      InvalidValueException.class,
      () -> transferOperation.execute(migrationRequest)
    );

    assertEquals("TRANSFER_INVALID_UPLOAD_RESPONSE", exception.getCode());
    verify(transferServiceMock).getRequired(MIGRATION_ID, MigrationFileType.ORGANIZATIONS);
    verifyTransferClaim(MigrationFileType.ORGANIZATIONS);
    verify(transferServiceMock).markTransferFailed(
      eq(MIGRATION_ID),
      eq(MigrationFileType.ORGANIZATIONS),
      eq("TRANSFER_INVALID_UPLOAD_RESPONSE"),
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
  void givenTooManyRequestsWhenExecuteThenPersistsSpecificFailureCode() throws IOException {
    String dataArchive = "organizations.zip";
    MigrationFileExecution fileExecution = exportedExecution(MigrationFileType.ORGANIZATIONS, dataArchive);
    createArchive(fileExecution, dataArchive);
    when(transferServiceMock.getRequired(MIGRATION_ID, MigrationFileType.ORGANIZATIONS)).thenReturn(fileExecution);
    stubTransferClaim(fileExecution, MigrationFileType.ORGANIZATIONS);
    HttpClientErrorException tooManyRequests = HttpClientErrorException.create(
      HttpStatus.TOO_MANY_REQUESTS,
      "Too Many Requests",
      HttpHeaders.EMPTY,
      null,
      null
    );
    when(transferServiceMock.markTransferFailed(
      MIGRATION_ID,
      MigrationFileType.ORGANIZATIONS,
      "TOO_MANY_REQUESTS",
      tooManyRequests.getMessage()
    )).thenReturn(fileExecution);
    when(puAuthnServiceMock.getAccessToken()).thenReturn("access-token");
    when(puMigrationServiceMock.uploadMigrationFile(
      eq(BROKER_IPA_CODE),
      eq(MigrationFileTypeEnum.ORGANIZATIONS),
      argThat(resource -> dataArchive.equals(resource.getFilename()) && resource.exists()),
      eq("access-token")
    )).thenThrow(tooManyRequests);

    AbstractMigrationTransferOperation transferOperation = operation(MigrationFileType.ORGANIZATIONS);
    MigrationRequestDto migrationRequest = request(List.of("ORG_IPA"));
    HttpClientErrorException exception = assertThrows(
      HttpClientErrorException.class,
      () -> transferOperation.execute(migrationRequest)
    );

    assertSame(tooManyRequests, exception);
    verify(transferServiceMock).getRequired(MIGRATION_ID, MigrationFileType.ORGANIZATIONS);
    verifyTransferClaim(MigrationFileType.ORGANIZATIONS);
    verify(transferServiceMock).markTransferFailed(
      MIGRATION_ID,
      MigrationFileType.ORGANIZATIONS,
      "TOO_MANY_REQUESTS",
      tooManyRequests.getMessage()
    );
    verify(puAuthnServiceMock).getAccessToken();
    verify(puMigrationServiceMock).uploadMigrationFile(
      eq(BROKER_IPA_CODE),
      eq(MigrationFileTypeEnum.ORGANIZATIONS),
      argThat(resource -> dataArchive.equals(resource.getFilename()) && resource.exists()),
      eq("access-token")
    );
  }

  @Test
  void givenUnexpectedUploadFailureAndFailurePersistenceErrorWhenExecuteThenPreservesOriginalFailure()
    throws IOException {
    String dataArchive = "organizations.zip";
    MigrationFileExecution fileExecution = exportedExecution(MigrationFileType.ORGANIZATIONS, dataArchive);
    createArchive(fileExecution, dataArchive);
    when(transferServiceMock.getRequired(MIGRATION_ID, MigrationFileType.ORGANIZATIONS)).thenReturn(fileExecution);
    stubTransferClaim(fileExecution, MigrationFileType.ORGANIZATIONS);
    IllegalStateException uploadFailure = new IllegalStateException();
    IllegalStateException persistenceFailure = new IllegalStateException("database unavailable");
    when(transferServiceMock.markTransferFailed(
      MIGRATION_ID,
      MigrationFileType.ORGANIZATIONS,
      "TRANSFER_FAILED",
      "IllegalStateException"
    )).thenThrow(persistenceFailure);
    when(puAuthnServiceMock.getAccessToken()).thenReturn("access-token");
    when(puMigrationServiceMock.uploadMigrationFile(
      eq(BROKER_IPA_CODE),
      eq(MigrationFileTypeEnum.ORGANIZATIONS),
      argThat(resource -> dataArchive.equals(resource.getFilename()) && resource.exists()),
      eq("access-token")
    )).thenThrow(uploadFailure);

    AbstractMigrationTransferOperation transferOperation = operation(MigrationFileType.ORGANIZATIONS);
    MigrationRequestDto migrationRequest = request(List.of("ORG_IPA"));
    IllegalStateException exception = assertThrows(
      IllegalStateException.class,
      () -> transferOperation.execute(migrationRequest)
    );

    assertSame(uploadFailure, exception);
    assertEquals(1, exception.getSuppressed().length);
    assertSame(persistenceFailure, exception.getSuppressed()[0]);
    verify(transferServiceMock).getRequired(MIGRATION_ID, MigrationFileType.ORGANIZATIONS);
    verifyTransferClaim(MigrationFileType.ORGANIZATIONS);
    verify(transferServiceMock).markTransferFailed(
      MIGRATION_ID,
      MigrationFileType.ORGANIZATIONS,
      "TRANSFER_FAILED",
      "IllegalStateException"
    );
    verify(puAuthnServiceMock).getAccessToken();
    verify(puMigrationServiceMock).uploadMigrationFile(
      eq(BROKER_IPA_CODE),
      eq(MigrationFileTypeEnum.ORGANIZATIONS),
      argThat(resource -> dataArchive.equals(resource.getFilename()) && resource.exists()),
      eq("access-token")
    );
  }

  private static Stream<Arguments> invalidArchiveCases() {
    return Stream.of(
      Arguments.of(null, "extractor-123", "TRANSFER_ARCHIVE_NOT_FOUND"),
      Arguments.of(" ", "extractor-123", "TRANSFER_ARCHIVE_NOT_FOUND"),
      Arguments.of(",organizations.zip", "extractor-123", "TRANSFER_ARCHIVE_NOT_FOUND"),
      Arguments.of("organizations.errors.zip", "extractor-123", "TRANSFER_ARCHIVE_NOT_FOUND"),
      Arguments.of("../outside.zip", "extractor-123", "TRANSFER_ARCHIVE_NOT_FOUND"),
      Arguments.of("organizations.zip", null, "TRANSFER_EXTRACTION_ID_INVALID"),
      Arguments.of("organizations.zip", " ", "TRANSFER_EXTRACTION_ID_INVALID"),
      Arguments.of("organizations.zip", ".", "TRANSFER_EXTRACTION_ID_INVALID"),
      Arguments.of("organizations.zip", "../outside", "TRANSFER_EXTRACTION_ID_INVALID"),
      Arguments.of("organizations.zip", "extractor-123", "TRANSFER_ARCHIVE_NOT_FOUND")
    );
  }

  private static Stream<Arguments> supportedFileTypeMappings() {
    return Stream.of(
      Arguments.of(MigrationFileType.ORGANIZATIONS, MigrationFileTypeEnum.ORGANIZATIONS),
      Arguments.of(MigrationFileType.ORG_SIL_SERVICES, MigrationFileTypeEnum.ORG_SIL_SERVICES),
      Arguments.of(MigrationFileType.DEBT_POSITIONS_TYPE, MigrationFileTypeEnum.DEBT_POSITIONS_TYPE),
      Arguments.of(MigrationFileType.DEBT_POSITIONS_TYPE_ORG, MigrationFileTypeEnum.DEBT_POSITIONS_TYPE_ORG),
      Arguments.of(
        MigrationFileType.DEBT_POSITIONS_TYPE_ORG_OPERATORS,
        MigrationFileTypeEnum.DEBT_POSITIONS_TYPE_ORG_OPERATORS
      ),
      Arguments.of(MigrationFileType.DEBT_POSITIONS, MigrationFileTypeEnum.DEBT_POSITIONS),
      Arguments.of(MigrationFileType.DEBT_POSITIONS_PAID, MigrationFileTypeEnum.DEBT_POSITIONS_PAID),
      Arguments.of(MigrationFileType.PAYMENT_NOTIFICATION, MigrationFileTypeEnum.PAYMENT_NOTIFICATION),
      Arguments.of(MigrationFileType.PAYMENTS_REPORTING_MYPAY, MigrationFileTypeEnum.PAYMENTS_REPORTING),
      Arguments.of(MigrationFileType.PAYMENTS_REPORTING_MYPIVOT, MigrationFileTypeEnum.PAYMENTS_REPORTING),
      Arguments.of(MigrationFileType.TREASURY_CSV_COMPLETE, MigrationFileTypeEnum.TREASURY_CSV_COMPLETE),
      Arguments.of(MigrationFileType.ASSESSMENTS, MigrationFileTypeEnum.ASSESSMENTS),
      Arguments.of(MigrationFileType.ASSESSMENTS_REGISTRY, MigrationFileTypeEnum.ASSESSMENTS_REGISTRY)
    );
  }

  private void stubSuccessfulUpload(
    MigrationFileExecution fileExecution,
    MigrationFileType fileType,
    MigrationFileTypeEnum puFileType,
    String dataArchive
  ) {
    when(transferServiceMock.getRequired(MIGRATION_ID, fileType)).thenReturn(fileExecution);
    stubTransferClaim(fileExecution, fileType);
    when(transferServiceMock.recordUploadAccepted(
      eq(MIGRATION_ID),
      eq(fileType),
      eq(42L),
      eq(UploadsStatusEnum.UPLOADED),
      argThat(Objects::nonNull)
    )).thenReturn(fileExecution);
    when(uploadResponseMock.getUploadId()).thenReturn(42L);
    when(puAuthnServiceMock.getAccessToken()).thenReturn("access-token");
    when(puMigrationServiceMock.uploadMigrationFile(
      eq(BROKER_IPA_CODE),
      eq(puFileType),
      argThat(resource -> dataArchive.equals(resource.getFilename()) && resource.exists()),
      eq("access-token")
    )).thenReturn(uploadResponseMock);
  }

  private void verifySuccessfulUpload(
    MigrationFileType fileType,
    MigrationFileTypeEnum puFileType,
    String dataArchive
  ) {
    verify(transferServiceMock).getRequired(MIGRATION_ID, fileType);
    verifyTransferClaim(fileType);
    verify(transferServiceMock).recordUploadAccepted(
      eq(MIGRATION_ID),
      eq(fileType),
      eq(42L),
      eq(UploadsStatusEnum.UPLOADED),
      argThat(Objects::nonNull)
    );
    verify(puAuthnServiceMock).getAccessToken();
    verify(puMigrationServiceMock).uploadMigrationFile(
      eq(BROKER_IPA_CODE),
      eq(puFileType),
      argThat(resource -> dataArchive.equals(resource.getFilename()) && resource.exists()),
      eq("access-token")
    );
    verify(uploadResponseMock).getUploadId();
  }

  private void stubTransferClaim(MigrationFileExecution fileExecution, MigrationFileType fileType) {
    when(transferServiceMock.markTransferring(
      eq(MIGRATION_ID), eq(fileType), argThat(Objects::nonNull)
    )).thenReturn(fileExecution);
  }

  private void verifyTransferClaim(MigrationFileType fileType) {
    verify(transferServiceMock).markTransferring(
      eq(MIGRATION_ID), eq(fileType), argThat(Objects::nonNull)
    );
  }

  private AbstractMigrationTransferOperation operation(MigrationFileType fileType) {
    return operation(fileType, BROKER_IPA_CODE);
  }

  private AbstractMigrationTransferOperation operation(MigrationFileType fileType, String brokerIpaCode) {
    return new AbstractMigrationTransferOperation(
      fileType,
      transferServiceMock,
      puMigrationServiceMock,
      puAuthnServiceMock,
      new MigrationTransferProperties(
        new MigrationTransferProperties.Storage(extractorOutputPath.toString()),
        new MigrationTransferProperties.Transfer(brokerIpaCode)
      )
    ) { };
  }

  private MigrationRequestDto request(List<String> ipaCodes) {
    MigrationRequestDto request = new MigrationRequestDto();
    request.setMigrationId(MIGRATION_ID);
    request.setIpaCodes(ipaCodes);
    return request;
  }

  private void createArchive(MigrationFileExecution fileExecution, String fileName) throws IOException {
    Path archiveDirectory = extractorOutputPath
      .resolve(fileExecution.getExtractionId());
    Files.createDirectories(archiveDirectory);
    Files.createFile(archiveDirectory.resolve(fileName));
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
