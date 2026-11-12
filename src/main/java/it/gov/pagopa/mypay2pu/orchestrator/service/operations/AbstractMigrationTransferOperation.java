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
import it.gov.pagopa.mypay2pu.orchestrator.exception.NotFoundException;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationFileExecution;
import it.gov.pagopa.mypay2pu.orchestrator.service.MigrationFileExecutionTransferService;
import it.gov.pagopa.pu.migration.dto.generated.MigrationFileTypeEnum;
import it.gov.pagopa.pu.migration.dto.generated.UploadMigrationFileResponseDTO;
import it.gov.pagopa.pu.migration.dto.generated.UploadsStatusEnum;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.web.client.HttpClientErrorException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Executes the shared upload lifecycle for a single migration file type.
 *
 * <p>Subclasses configure their file type and inherit archive validation, PU submission, and
 * persistence behavior. PU processing results remain owned by the separate Evidence phase.
 */
public abstract class AbstractMigrationTransferOperation implements MigrationTransferOperation {

  private static final String DEFAULT_TRANSFER_ERROR = "TRANSFER_FAILED";

  private final MigrationFileType fileType;
  private final MigrationFileExecutionTransferService transferService;
  private final PuMigrationService puMigrationService;
  private final PuAuthnService puAuthnService;
  private final Path extractorOutputPath;
  private final String brokerIpaCode;

  protected AbstractMigrationTransferOperation(
    MigrationFileType fileType,
    MigrationFileExecutionTransferService transferService,
    PuMigrationService puMigrationService,
    PuAuthnService puAuthnService,
    MigrationTransferProperties transferProperties
  ) {
    this.fileType = Objects.requireNonNull(fileType, "fileType");
    this.transferService = Objects.requireNonNull(transferService, "transferService");
    this.puMigrationService = Objects.requireNonNull(puMigrationService, "puMigrationService");
    this.puAuthnService = Objects.requireNonNull(puAuthnService, "puAuthnService");
    this.extractorOutputPath = Path.of(transferProperties.storage().extractorOutputPath())
      .toAbsolutePath()
      .normalize();
    this.brokerIpaCode = transferProperties.transfer().brokerIpaCode();
  }

  @Override
  public final MigrationFileType getMigrationFileType() {
    return fileType;
  }

  /**
   * Uploads the exported archive once and persists the accepted upload as WAITING_PU.
   *
   * @param request migration request containing its ID and requested IPA codes
   */
  @Override
  public final void execute(MigrationRequestDto request) {
    UUID migrationId = request.getMigrationId();
    MigrationFileExecution fileExecution = transferService.getRequired(migrationId, fileType);

    MigrationFileExecutionStatus status = fileExecution.getStatus();
    if (status == MigrationFileExecutionStatus.WAITING_PU
      || status == MigrationFileExecutionStatus.COMPLETED
      || status == MigrationFileExecutionStatus.WARNING) {
      return;
    }

    switch (status) {
      case TRANSFERING -> throw new ConflictException(
        "INVALID_MIGRATION_FILE_EXECUTION_TRANSITION",
        "Transfer for migration %s and file type %s is already in progress"
          .formatted(migrationId, fileType)
      );
      case FAILED -> throw alreadyFailed(fileExecution);
      case EXPORTED -> uploadExportedFile(request, fileExecution);
      default -> throw new ConflictException(
        "TRANSFER_FILE_NOT_READY",
        "File type %s for migration %s is not ready for transfer (status=%s)"
          .formatted(fileType, migrationId, fileExecution.getStatus())
      );
    }
  }

  private void uploadExportedFile(MigrationRequestDto request, MigrationFileExecution fileExecution) {
    UUID migrationId = request.getMigrationId();
    Resource archive;
    String orgIpaCode;
    MigrationFileTypeEnum puFileType;
    try {
      archive = dataArchive(fileExecution);
      orgIpaCode = organizationIpaCode(request, archive.getFilename());
      puFileType = puFileType();
    } catch (RuntimeException failure) {
      markTransferFailed(migrationId, failure);
      throw failure;
    }

    transferService.markTransferring(migrationId, fileType, LocalDateTime.now(ZoneOffset.UTC));
    try {
      UploadMigrationFileResponseDTO response = puMigrationService.uploadMigrationFile(
        orgIpaCode,
        puFileType,
        archive,
        puAuthnService.getAccessToken()
      );
      Long uploadId = response == null ? null : response.getUploadId();
      if (uploadId == null || uploadId <= 0) {
        throw new InvalidValueException(
          "TRANSFER_INVALID_UPLOAD_RESPONSE",
          "PU returned no valid upload ID for migration %s and file type %s".formatted(migrationId, fileType)
        );
      }
      transferService.recordUploadAccepted(
        migrationId,
        fileType,
        uploadId,
        UploadsStatusEnum.UPLOADED,
        LocalDateTime.now(ZoneOffset.UTC)
      );
    } catch (RuntimeException failure) {
      markTransferFailed(migrationId, failure);
      throw failure;
    }
  }

  private Resource dataArchive(MigrationFileExecution fileExecution) {
    String filePath = fileExecution.getFilePath();
    if (filePath == null || filePath.isBlank()) {
      throw new NotFoundException(
        "TRANSFER_ARCHIVE_NOT_FOUND",
        "No exported archive is available for migration %s and file type %s"
          .formatted(fileExecution.getMigrationId(), fileType)
      );
    }

    List<String> archives = List.of(filePath.split(",")).stream()
      .map(String::trim)
      .filter(name -> !name.isEmpty())
      .filter(name -> !name.toLowerCase(Locale.ROOT).endsWith(".errors.zip"))
      .toList();
    if (archives.isEmpty()) {
      throw new NotFoundException(
        "TRANSFER_ARCHIVE_NOT_FOUND",
        "No data archive is available for migration %s and file type %s"
          .formatted(fileExecution.getMigrationId(), fileType)
      );
    }
    if (archives.size() != 1) {
      throw new InvalidValueException(
        "TRANSFER_MULTIPLE_ARCHIVES_NOT_SUPPORTED",
        "Expected one data archive for migration %s and file type %s, found %d"
          .formatted(fileExecution.getMigrationId(), fileType, archives.size())
      );
    }

    Path archivePath = extractorOutputPath.resolve(archives.getFirst()).normalize();
    if (!archivePath.startsWith(extractorOutputPath)
      || !Files.isRegularFile(archivePath)
      || !Files.isReadable(archivePath)) {
      throw new NotFoundException(
        "TRANSFER_ARCHIVE_NOT_FOUND",
        "Exported archive %s is missing or unreadable".formatted(archives.getFirst())
      );
    }
    return new FileSystemResource(archivePath);
  }

  private String organizationIpaCode(MigrationRequestDto request, String archiveName) {
    if (fileType == MigrationFileType.ORGANIZATIONS) {
      if (brokerIpaCode == null || brokerIpaCode.isBlank()) {
        throw new InvalidValueException(
          "TRANSFER_BROKER_IPA_CODE_MISSING",
          "Broker IPA code is not configured"
        );
      }
      return brokerIpaCode;
    }

    List<String> ipaCodes = request.getIpaCodes();
    if (ipaCodes == null || ipaCodes.isEmpty()) {
      throw new InvalidValueException(
        "TRANSFER_IPA_CODE_NOT_FOUND",
        "No IPA code was provided for file type %s".formatted(fileType)
      );
    }
    List<String> distinctCodes = ipaCodes.stream().filter(Objects::nonNull).distinct().toList();
    if (distinctCodes.size() == 1) {
      return distinctCodes.getFirst();
    }

    List<String> matchingCodes = distinctCodes.stream()
      .filter(code -> Pattern.compile(
        "(?i)(?<![A-Z0-9_])%s(?![A-Z0-9_])".formatted(Pattern.quote(code))
      ).matcher(archiveName).find())
      .toList();
    if (matchingCodes.size() != 1) {
      throw new InvalidValueException(
        matchingCodes.isEmpty() ? "TRANSFER_IPA_CODE_NOT_FOUND" : "TRANSFER_IPA_CODE_AMBIGUOUS",
        "Unable to determine one IPA code from the archive name for file type %s".formatted(fileType)
      );
    }
    return matchingCodes.getFirst();
  }

  private MigrationFileTypeEnum puFileType() {
    return switch (fileType) {
      case ORGANIZATIONS -> MigrationFileTypeEnum.ORGANIZATIONS;
      case ORG_SIL_SERVICES -> MigrationFileTypeEnum.ORG_SIL_SERVICES;
      case DEBT_POSITIONS_TYPE -> MigrationFileTypeEnum.DEBT_POSITIONS_TYPE;
      case DEBT_POSITIONS_TYPE_ORG -> MigrationFileTypeEnum.DEBT_POSITIONS_TYPE_ORG;
      case DEBT_POSITIONS_TYPE_ORG_OPERATORS -> MigrationFileTypeEnum.DEBT_POSITIONS_TYPE_ORG_OPERATORS;
      case DEBT_POSITIONS -> MigrationFileTypeEnum.DEBT_POSITIONS;
      case DEBT_POSITIONS_PAID -> MigrationFileTypeEnum.DEBT_POSITIONS_PAID;
      case PAYMENT_NOTIFICATION -> MigrationFileTypeEnum.PAYMENT_NOTIFICATION;
      case PAYMENTS_REPORTING_MYPAY, PAYMENTS_REPORTING_MYPIVOT -> MigrationFileTypeEnum.PAYMENTS_REPORTING;
      case TREASURY_CSV_COMPLETE -> MigrationFileTypeEnum.TREASURY_CSV_COMPLETE;
      case ASSESSMENTS -> MigrationFileTypeEnum.ASSESSMENTS;
      case ASSESSMENTS_REGISTRY -> MigrationFileTypeEnum.ASSESSMENTS_REGISTRY;
    };
  }

  private void markTransferFailed(UUID migrationId, RuntimeException failure) {
    String errorCode = transferErrorCode(failure);
    String errorMessage = failure.getMessage() == null || failure.getMessage().isBlank()
      ? failure.getClass().getSimpleName()
      : failure.getMessage();
    try {
      transferService.markTransferFailed(migrationId, fileType, errorCode, errorMessage);
    } catch (RuntimeException persistenceFailure) {
      failure.addSuppressed(persistenceFailure);
    }
  }

  private String transferErrorCode(RuntimeException failure) {
    if (failure instanceof BaseBusinessException businessFailure) {
      return businessFailure.getCode();
    }
    if (failure instanceof HttpClientErrorException.TooManyRequests) {
      return "TOO_MANY_REQUESTS";
    }
    return DEFAULT_TRANSFER_ERROR;
  }

  private ConflictException alreadyFailed(MigrationFileExecution fileExecution) {
    String errorCode = fileExecution.getUploadErrorCode();
    if (errorCode == null || errorCode.isBlank()) {
      errorCode = fileExecution.getExtractionErrorCode();
    }
    if (errorCode == null || errorCode.isBlank()) {
      errorCode = "TRANSFER_ALREADY_FAILED";
    }
    String errorMessage = fileExecution.getUploadErrorMsg();
    if (errorMessage == null || errorMessage.isBlank()) {
      errorMessage = fileExecution.getExtractionErrorMsg();
    }
    if (errorMessage == null || errorMessage.isBlank()) {
      errorMessage = "Transfer is already FAILED";
    }
    return new ConflictException(errorCode, errorMessage);
  }
}
