package it.gov.pagopa.mypay2pu.orchestrator.service;

import it.gov.pagopa.mypay2pu.orchestrator.config.json.LocalDateTimeToOffsetDateTimeSerializer;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationAction;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationRequestDto;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.OrchestrationMode;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.PeriodDto;
import it.gov.pagopa.mypay2pu.orchestrator.exception.InvalidValueException;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationExecution;
import it.gov.pagopa.mypay2pu.orchestrator.utils.Constants;
import it.gov.pagopa.mypay2pu.orchestrator.validation.MigrationRequestValidator;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Component;

/** Resolves extraction bounds once, keeping the request and execution aligned before dispatch. */
@Component
public class ExtractionWindowResolver {

  private final MigrationRequestValidator migrationRequestValidator;

  public ExtractionWindowResolver(MigrationRequestValidator migrationRequestValidator) {
    this.migrationRequestValidator = migrationRequestValidator;
  }

  /**
   * Resolves and assigns the extraction window for a migration execution.
   *
   * <p>TRANSFER and REJECTS_ONLY actions are left unchanged. DELTA migrations derive their
   * start timestamp from the previous successful extraction's exclusive end and link the
   * previous execution as the parent. Missing ends default to the start of yesterday for
   * DELTA and today for FULL, in the configured application timezone.
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

    PeriodDto period = request.getPeriod();
    OffsetDateTime from = period == null ? null : period.getFromDate();
    MigrationExecution previous = null;
    boolean delta = request.getOrchestrationMode() == OrchestrationMode.DELTA;
    if (delta) {
      previous = migrationRequestValidator.requireDeltaPreviousMigration(request);
      if (previous.getDateTo() == null || !previous.getDateTo().equals(previous.getLastSuccessfulExtractionAt())) {
        throw new InvalidValueException("INVALID_MIGRATION_REQUEST",
          "previous checkpoint must equal its exclusive period.toDate");
      }
      OffsetDateTime previousEnd =
        LocalDateTimeToOffsetDateTimeSerializer.convertToOffsetDateTime(previous.getDateTo());
      if (from != null && !from.isEqual(previousEnd)) {
        throw new InvalidValueException("INVALID_MIGRATION_REQUEST",
          "period.fromDate must equal the previous extraction checkpoint");
      }
      from = previousEnd;
    }

    OffsetDateTime to = resolveUpperBound(period, delta);
    if (from != null && !from.isBefore(to)) {
      throw new InvalidValueException("INVALID_MIGRATION_REQUEST",
        "extraction period must have a start before its exclusive end");
    }
    request.setPeriod(new PeriodDto(from, to));
    execution.setDateFrom(from == null ? null : from.atZoneSameInstant(Constants.ZONEID).toLocalDateTime());
    execution.setDateTo(to.atZoneSameInstant(Constants.ZONEID).toLocalDateTime());
    if (previous != null) {
      execution.setParentExecutionId(previous.getId());
    }
  }

  private OffsetDateTime resolveUpperBound(PeriodDto period, boolean delta) {
    OffsetDateTime to = period == null ? null : period.getToDate();
    if (to == null) {
      LocalDate today = LocalDate.now(Constants.ZONEID);
      to = (delta ? today.minusDays(1) : today).atStartOfDay(Constants.ZONEID).toOffsetDateTime();
    }
    if (to.isAfter(OffsetDateTime.now(Constants.ZONEID))) {
      throw new InvalidValueException("INVALID_MIGRATION_REQUEST",
        "period.toDate must not be in the future");
    }
    return to;
  }
}
