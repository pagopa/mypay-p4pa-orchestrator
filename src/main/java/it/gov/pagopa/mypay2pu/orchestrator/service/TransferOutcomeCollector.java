package it.gov.pagopa.mypay2pu.orchestrator.service;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.config.TransferOutcomePollingProperties;
import it.gov.pagopa.mypay2pu.orchestrator.connector.pu.migration.PuMigrationService;
import it.gov.pagopa.mypay2pu.orchestrator.exception.TransferOutcomeException;
import it.gov.pagopa.mypay2pu.orchestrator.exception.InvalidValueException;
import it.gov.pagopa.mypay2pu.orchestrator.exception.BaseBusinessException;
import it.gov.pagopa.mypay2pu.orchestrator.exception.ConflictException;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationFileExecution;
import it.gov.pagopa.mypay2pu.orchestrator.validation.MigrationFileExecutionTransferOutcomeValidator;
import it.gov.pagopa.pu.migration.dto.generated.UploadDetails;
import it.gov.pagopa.pu.migration.dto.generated.Uploads;
import it.gov.pagopa.pu.migration.dto.generated.UploadsStatusEnum;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionException;
import org.springframework.web.client.RestClientException;

import static it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationFileExecutionStatus.FAILED;
import static it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationFileExecutionStatus.COMPLETED;
import static it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationFileExecutionStatus.WARNING;

/** Waits for one PU upload on the existing asynchronous DAG worker. */
@Service
@Slf4j
public class TransferOutcomeCollector {

  private final MigrationFileExecutionTransferOutcomeService transferOutcomeService;
  private final PuMigrationService puMigrationService;
  private final MigrationFileExecutionTransferOutcomeValidator validator;
  private final TransferOutcomePollingProperties properties;
  private final Clock clock;

  public TransferOutcomeCollector(
    MigrationFileExecutionTransferOutcomeService transferOutcomeService,
    PuMigrationService puMigrationService,
    MigrationFileExecutionTransferOutcomeValidator validator,
    TransferOutcomePollingProperties properties,
    @Qualifier("transferOutcomeClock") Clock clock
  ) {
    this.transferOutcomeService = transferOutcomeService;
    this.puMigrationService = puMigrationService;
    this.validator = validator;
    this.properties = properties;
    this.clock = clock;
  }

  public void collect(UUID migrationId, MigrationFileType fileType) {
    MigrationFileExecution file = transferOutcomeService.getRequired(migrationId, fileType);
    if (validator.isTerminal(file)) {
      requireSuccessfulOutcome(file);
      return;
    }
    try {
      validator.validatePolling(file);
      Instant deadline = file.getUploadCompletedAt().toInstant(ZoneOffset.UTC).plus(properties.statusTimeout());
      poll(file, deadline);
    } catch (RestClientException failure) {
      recordFailure(file, new TransferOutcomeException("TRANSFER_OUTCOME_POLLING_FAILED",
        "PU transfer outcome request failed for upload " + file.getUploadId(), failure));
    } catch (InvalidValueException failure) {
      recordFailure(file, new TransferOutcomeException("TRANSFER_OUTCOME_INVALID_RESPONSE", failure.getMessage(), failure));
    } catch (TransferOutcomeException failure) {
      recordFailure(file, failure);
    } catch (DataAccessException | TransactionException failure) {
      recordFailure(file, new TransferOutcomeException("TRANSFER_OUTCOME_PERSISTENCE_FAILED",
        "Cannot persist PU transfer outcome for upload " + file.getUploadId(), failure));
    } catch (ConflictException failure) {
      MigrationFileExecution current = transferOutcomeService.getRequired(migrationId, fileType);
      if (!Objects.equals(file.getUploadId(), current.getUploadId())
        || !validator.isTerminal(current)) {
        throw failure;
      }
      requireSuccessfulOutcome(current);
    }
  }

  private void poll(MigrationFileExecution file, Instant deadline) {
    while (true) {
      checkDeadline(deadline, file);
      Uploads upload = puMigrationService.getMigrationUpload(file.getUploadId());
      checkDeadline(deadline, file);
      validator.validateUpload(file, file.getUploadId(), upload);
      List<UploadDetails> details = upload.getStatus() == UploadsStatusEnum.PROCESSING
        || upload.getStatus() == UploadsStatusEnum.COMPLETED
        ? puMigrationService.getMigrationUploadDetails(file.getUploadId()) : List.of();
      checkDeadline(deadline, file);
      MigrationFileExecution updated = transferOutcomeService.recordTransferOutcome(file.getId(), file.getUploadId(), upload, details);
      if (validator.isTerminal(updated)) {
        requireSuccessfulOutcome(updated);
        return;
      }
      Duration remaining = checkDeadline(deadline, file);
      Duration delay = remaining.compareTo(properties.statusPollInterval()) < 0
        ? remaining : properties.statusPollInterval();
      try {
        Thread.sleep(delay);
      } catch (InterruptedException failure) {
        Thread.currentThread().interrupt();
        throw new TransferOutcomeException("TRANSFER_OUTCOME_INTERRUPTED",
          "Interrupted while collecting transfer outcome for upload " + file.getUploadId(), failure);
      }
    }
  }

  private Duration checkDeadline(Instant deadline, MigrationFileExecution file) {
    Duration remaining = Duration.between(clock.instant(), deadline);
    if (remaining.isNegative() || remaining.isZero()) {
      throw new TransferOutcomeException("TRANSFER_OUTCOME_TIMEOUT",
        "Timed out waiting for PU transfer outcome for upload " + file.getUploadId());
    }
    return remaining;
  }

  private void requireSuccessfulOutcome(MigrationFileExecution file) {
    if (file.getStatus() == FAILED) {
      throw new TransferOutcomeException(
        file.getUploadErrorCode() == null ? "TRANSFER_OUTCOME_PU_FAILED" : file.getUploadErrorCode(),
        file.getUploadErrorMsg() == null ? "PU transfer outcome failed for file " + file.getId() : file.getUploadErrorMsg()
      );
    }
  }

  private void recordFailure(MigrationFileExecution file, TransferOutcomeException failure) {
    try {
      MigrationFileExecution current = transferOutcomeService.markFailed(
        file.getId(), file.getUploadId(), failure.getCode(), failure.getMessage());
      if (current.getStatus() == COMPLETED || current.getStatus() == WARNING) {
        log.info("File {} already has successful terminal transfer outcome; ignoring obsolete polling failure {}", file.getId(), failure.getCode());
        return;
      }
      if (current.getUploadErrorCode() != null
        && (!Objects.equals(current.getUploadErrorCode(), failure.getCode())
          || !Objects.equals(current.getUploadErrorMsg(), failure.getMessage()))) {
        failure = new TransferOutcomeException(current.getUploadErrorCode(), current.getUploadErrorMsg(), failure);
      }
    } catch (DataAccessException | TransactionException | BaseBusinessException persistenceFailure) {
      failure.addSuppressed(persistenceFailure);
    }
    log.error("Transfer outcome collection failed for file {} and upload {}", file.getId(), file.getUploadId(), failure);
    throw failure;
  }
}
