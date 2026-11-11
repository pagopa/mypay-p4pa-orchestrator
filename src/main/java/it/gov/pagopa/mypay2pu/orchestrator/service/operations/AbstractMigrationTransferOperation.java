package it.gov.pagopa.mypay2pu.orchestrator.service.operations;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.connector.pu.auth.PuAuthnService;
import it.gov.pagopa.mypay2pu.orchestrator.connector.pu.migration.PuMigrationService;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationFileExecutionStatus;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationRequestDto;
import it.gov.pagopa.mypay2pu.orchestrator.exception.BaseBusinessException;
import it.gov.pagopa.mypay2pu.orchestrator.exception.ConflictException;
import it.gov.pagopa.mypay2pu.orchestrator.exception.TransferPhaseException;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationFileExecution;
import it.gov.pagopa.mypay2pu.orchestrator.service.MigrationFileExecutionTransferService;
import it.gov.pagopa.pu.migration.dto.generated.MigrationFileTypeEnum;
import it.gov.pagopa.pu.migration.dto.generated.UploadMigrationFileResponseDTO;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.web.client.HttpClientErrorException;

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
    String extractorOutputPath,
    String brokerIpaCode
  ) {
    this.fileType = Objects.requireNonNull(fileType, "fileType");
    this.transferService = Objects.requireNonNull(transferService, "transferService");
    this.puMigrationService = Objects.requireNonNull(puMigrationService, "puMigrationService");
    this.puAuthnService = Objects.requireNonNull(puAuthnService, "puAuthnService");
    String outputPath = Objects.requireNonNull(extractorOutputPath, "extractorOutputPath");
    if (outputPath.isBlank()) {
      throw new IllegalArgumentException("extractorOutputPath must not be blank");
    }
    this.extractorOutputPath = Path.of(outputPath);
    this.brokerIpaCode = Objects.requireNonNull(brokerIpaCode, "brokerIpaCode");
  }

  @Override
  public final MigrationFileType getMigrationFileType() {
    return fileType;
  }

  @Override
  public final void execute(MigrationRequestDto request) {
    if (request == null || request.getMigrationId() == null) {
      throw new TransferPhaseException("TRANSFER_MIGRATION_ID_MISSING", "Transfer requires a migration ID");
    }

    UUID migrationId = request.getMigrationId();
    MigrationFileExecution fileExecution = transferService.getRequired(migrationId, fileType);
    if (isAlreadyStartedOrCompleted(fileExecution.getStatus())) {
      return;
    }
    if (fileExecution.getStatus() != MigrationFileExecutionStatus.EXPORTED) {
      throw new TransferPhaseException(
        "TRANSFER_FILE_NOT_READY",
        "File type %s for migration %s is not ready for transfer (status=%s)"
          .formatted(fileType, migrationId, fileExecution.getStatus())
      );
    }

    Path archive = resolveDataArchive(fileExecution.getFilePath());
    String orgIpaCode = resolveIpaCode(request, archive.getFileName().toString());
    try {
      transferService.markTransferring(migrationId, fileType, LocalDateTime.now(ZoneOffset.UTC));
    } catch (ConflictException conflict) {
      MigrationFileExecution current = transferService.getRequired(migrationId, fileType);
      if (isAlreadyStartedOrCompleted(current.getStatus())
        || current.getStatus() == MigrationFileExecutionStatus.FAILED) {
        return;
      }
      throw conflict;
    }

    try {
      Resource resource = new FileSystemResource(archive);
      UploadMigrationFileResponseDTO response = puMigrationService.uploadMigrationFile(
        orgIpaCode,
        migrationFileType(),
        resource,
        puAuthnService.getAccessToken()
      );
      Long uploadId = response == null ? null : response.getUploadId();
      if (uploadId == null || uploadId <= 0) {
        throw new TransferPhaseException(
          "TRANSFER_INVALID_UPLOAD_RESPONSE",
          "PU returned no valid upload ID for migration %s and file type %s".formatted(migrationId, fileType)
        );
      }
      transferService.recordUploadAccepted(
        migrationId,
        fileType,
        uploadId,
        LocalDateTime.now(ZoneOffset.UTC)
      );
    } catch (RuntimeException failure) {
      markTransferFailed(migrationId, failure);
      throw failure;
    }
  }

  private boolean isAlreadyStartedOrCompleted(MigrationFileExecutionStatus status) {
    return status == MigrationFileExecutionStatus.TRANSFERING
      || status == MigrationFileExecutionStatus.WAITING_PU
      || status == MigrationFileExecutionStatus.COMPLETED
      || status == MigrationFileExecutionStatus.WARNING;
  }

  private Path resolveDataArchive(String persistedFilePath) {
    if (persistedFilePath == null || persistedFilePath.isBlank()) {
      throw new TransferPhaseException("TRANSFER_ARCHIVE_NOT_FOUND", "Exported file path is missing");
    }
    List<String> archiveNames = Arrays.stream(persistedFilePath.split(",", -1))
      .map(String::trim)
      .filter(name -> !name.isEmpty())
      .filter(name -> !name.toLowerCase(Locale.ROOT).endsWith(".errors.zip"))
      .toList();
    if (archiveNames.isEmpty()) {
      throw new TransferPhaseException("TRANSFER_ARCHIVE_NOT_FOUND", "No data archive was produced by Export");
    }
    if (archiveNames.size() > 1) {
      throw new TransferPhaseException(
        "TRANSFER_MULTIPLE_ARCHIVES_NOT_SUPPORTED",
        "Export produced more than one data archive for file type %s".formatted(fileType)
      );
    }

    String archiveName = archiveNames.getFirst();
    if (!archiveName.toLowerCase(Locale.ROOT).endsWith(".zip")) {
      throw new TransferPhaseException(
        "TRANSFER_INVALID_ARCHIVE_PATH",
        "Exported data archive for file type %s is not a ZIP file".formatted(fileType)
      );
    }
    try {
      Path relativePath = Path.of(archiveName);
      Path root = extractorOutputPath.toRealPath();
      Path candidate = root.resolve(relativePath).normalize();
      if (relativePath.isAbsolute() || !candidate.startsWith(root)) {
        throw new TransferPhaseException(
          "TRANSFER_INVALID_ARCHIVE_PATH",
          "Exported archive path must stay under the Extractor output directory"
        );
      }
      Path archive = candidate.toRealPath();
      if (!archive.startsWith(root) || !Files.isRegularFile(archive) || !Files.isReadable(archive)) {
        throw new TransferPhaseException(
          "TRANSFER_ARCHIVE_NOT_FOUND",
          "Exported data archive is missing or unreadable for file type %s".formatted(fileType)
        );
      }
      return archive;
    } catch (IOException | InvalidPathException exception) {
      throw new TransferPhaseException(
        "TRANSFER_ARCHIVE_NOT_FOUND",
        "Unable to access the exported data archive for file type %s".formatted(fileType),
        exception
      );
    }
  }

  private String resolveIpaCode(MigrationRequestDto request, String archiveName) {
    if (fileType == MigrationFileType.ORGANIZATIONS) {
      if (brokerIpaCode.isBlank()) {
        throw new TransferPhaseException("TRANSFER_BROKER_IPA_CODE_MISSING", "Broker IPA code is not configured");
      }
      return brokerIpaCode;
    }

    List<String> ipaCodes = request.getIpaCodes() == null
      ? List.of()
      : request.getIpaCodes().stream().filter(Objects::nonNull).map(String::trim).filter(code -> !code.isEmpty())
        .distinct().toList();
    if (ipaCodes.size() == 1) {
      return ipaCodes.getFirst();
    }
    if (ipaCodes.isEmpty()) {
      throw new TransferPhaseException(
        "TRANSFER_IPA_CODE_NOT_FOUND",
        "No IPA code was provided for file type %s".formatted(fileType)
      );
    }

    List<String> matchingCodes = ipaCodes.stream()
      .filter(code -> Pattern.compile(
        "(?i)(?<![A-Z0-9_])%s(?![A-Z0-9_])".formatted(Pattern.quote(code))
      ).matcher(archiveName).find())
      .toList();
    if (matchingCodes.size() != 1) {
      throw new TransferPhaseException(
        matchingCodes.isEmpty() ? "TRANSFER_IPA_CODE_NOT_FOUND" : "TRANSFER_IPA_CODE_AMBIGUOUS",
        "Unable to determine one IPA code from the archive name for file type %s".formatted(fileType)
      );
    }
    return matchingCodes.getFirst();
  }

  private MigrationFileTypeEnum migrationFileType() {
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
    if (failure instanceof TransferPhaseException transferFailure) {
      return transferFailure.getCode();
    }
    if (failure instanceof BaseBusinessException businessFailure) {
      return businessFailure.getCode();
    }
    if (failure instanceof HttpClientErrorException.TooManyRequests) {
      return "TOO_MANY_REQUESTS";
    }
    return DEFAULT_TRANSFER_ERROR;
  }
}
