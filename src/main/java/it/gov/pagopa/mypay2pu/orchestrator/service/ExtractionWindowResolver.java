package it.gov.pagopa.mypay2pu.orchestrator.service;

import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationAction;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationRequestDto;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.OrchestrationMode;
import it.gov.pagopa.mypay2pu.orchestrator.exception.InvalidValueException;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationExecution;
import it.gov.pagopa.mypay2pu.orchestrator.validation.MigrationRequestValidator;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Resolves extraction bounds before persistence and from the persisted execution for submission. */
@Component
public class ExtractionWindowResolver {

  private final MigrationRequestValidator migrationRequestValidator;

  public ExtractionWindowResolver(MigrationRequestValidator migrationRequestValidator) {
    this.migrationRequestValidator = migrationRequestValidator;
  }

  /**
   * Builds the window for a new extraction from its persisted execution.
   *
   * <p>Only FULL and DELTA extractions use a window. The persisted bounds are authoritative;
   * this method does not recalculate them or inspect a predecessor.
   *
   * @param request migration request identifying the current execution and mode
   * @return persisted bounds converted to UTC, or {@code null} when the mode has no window
   * @throws IllegalStateException if the execution or its persisted exclusive end is missing
   */
  public ExtractionWindow resolvePersistedWindow(MigrationRequestDto request) {
    OrchestrationMode mode = request.getOrchestrationMode();
    if (request.getAction() == MigrationAction.TRANSFER
      || (mode != OrchestrationMode.FULL && mode != OrchestrationMode.DELTA)) {
      return null;
    }

    UUID migrationId = request.getMigrationId();
    MigrationExecution execution = migrationRequestValidator.requireExistingMigration(migrationId);
    LocalDate to = Objects.requireNonNull(
      execution.getDateTo(), "Persisted extraction end is required for migration " + migrationId);
    return new ExtractionWindow(
      execution.getDateFrom() == null ? null : execution.getDateFrom().atStartOfDay().atOffset(ZoneOffset.UTC),
      to.atStartOfDay().atOffset(ZoneOffset.UTC),
      execution.getOrchestrationMode() == OrchestrationMode.DELTA);
  }

  /**
   * Resolves and assigns the extraction window for a migration execution.
   *
   * <p>TRANSFER and REJECTS_ONLY actions are left unchanged. DELTA migrations derive their
   * start date from the previous successful extraction checkpoint and link the previous
   * execution as the parent. For other migrations, the end date defaults to the current UTC
   * date when it is not provided.
   *
   * @param request migration request containing the action, mode, and period constraints
   * @param execution migration execution whose extraction bounds are resolved
   * @throws InvalidValueException if the delta checkpoint or extraction period is invalid
   */
  public void resolve(MigrationRequestDto request, MigrationExecution execution) {
    if (request.getAction() == MigrationAction.TRANSFER
      || request.getOrchestrationMode() == OrchestrationMode.REJECTS_ONLY) {
      return;
    }

    LocalDate from = execution.getDateFrom();
    MigrationExecution previous = null;
    if (request.getOrchestrationMode() == OrchestrationMode.DELTA) {
      previous = migrationRequestValidator.requireDeltaPreviousMigration(request);
      LocalDateTime checkpoint = previous.getLastSuccessfulExtractionAt();
      if (previous.getDateTo() == null || !previous.getDateTo().atStartOfDay().equals(checkpoint)) {
        throw new InvalidValueException("INVALID_MIGRATION_REQUEST",
          "previous checkpoint must equal its exclusive period.toDate at midnight UTC");
      }
      if (from != null && !from.atStartOfDay().equals(checkpoint)) {
        throw new InvalidValueException("INVALID_MIGRATION_REQUEST",
          "period.fromDate must equal the previous extraction checkpoint");
      }
      from = checkpoint.toLocalDate();
    }

    LocalDate today = LocalDate.now(ZoneOffset.UTC);
    LocalDate to = execution.getDateTo() == null ? today : execution.getDateTo();
    if (to.isAfter(today)) {
      throw new InvalidValueException("INVALID_MIGRATION_REQUEST",
        "period.toDate must not be after the current UTC date");
    }
    if (from != null && !from.isBefore(to)) {
      throw new InvalidValueException("INVALID_MIGRATION_REQUEST",
        "extraction period must have a start before its exclusive end");
    }
    execution.setDateFrom(from);
    execution.setDateTo(to);
    if (previous != null) {
      execution.setParentExecutionId(previous.getId());
    }
  }
}
