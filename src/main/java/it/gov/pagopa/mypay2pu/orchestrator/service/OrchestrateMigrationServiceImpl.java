package it.gov.pagopa.mypay2pu.orchestrator.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationExecutionStatus;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationPhase;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationRequestDto;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationResponseDto;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.PeriodDto;
import it.gov.pagopa.mypay2pu.orchestrator.exception.InvalidValueException;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationExecution;
import it.gov.pagopa.mypay2pu.orchestrator.repository.MigrationExecutionRepository;
import it.gov.pagopa.mypay2pu.orchestrator.service.coordinator.CompleteMigrationPhaseCoordinator;
import it.gov.pagopa.mypay2pu.orchestrator.service.coordinator.TransferPhaseCoordinator;
import it.gov.pagopa.mypay2pu.orchestrator.validation.MigrationRequestValidator;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class OrchestrateMigrationServiceImpl implements OrchestrateMigrationService {

  private static final int INITIAL_CYCLE_NUMBER = 1;

  private final TransferPhaseCoordinator transferPhaseCoordinator;
  private final CompleteMigrationPhaseCoordinator completeMigrationPhaseCoordinator;
  private final MigrationRequestValidator migrationRequestValidator;
  private final MigrationExecutionRepository migrationExecutionRepository;
  private final ObjectMapper objectMapper;
  private final String brokerIpaCode;

  public OrchestrateMigrationServiceImpl(
    TransferPhaseCoordinator transferPhaseCoordinator,
    CompleteMigrationPhaseCoordinator completeMigrationPhaseCoordinator,
    MigrationRequestValidator migrationRequestValidator,
    MigrationExecutionRepository migrationExecutionRepository,
    ObjectMapper objectMapper,
    @Value("${orchestrator.transfer.broker-ipa-code}") String brokerIpaCode
  ) {
    this.transferPhaseCoordinator = transferPhaseCoordinator;
    this.completeMigrationPhaseCoordinator = completeMigrationPhaseCoordinator;
    this.migrationRequestValidator = migrationRequestValidator;
    this.migrationExecutionRepository = migrationExecutionRepository;
    this.objectMapper = objectMapper;
    this.brokerIpaCode = brokerIpaCode;
  }

  @Override
  public MigrationResponseDto startMigration(MigrationRequestDto request) {
    migrationRequestValidator.validate(request);
    MigrationPhase phase = request.getPhase();

    UUID migrationId;
    switch (phase) {
      case TRANSFER -> {
        migrationId = request.getMigrationId();
        if (migrationId == null) {
          throw new InvalidValueException("INVALID_MIGRATION_REQUEST", "migrationId is required for TRANSFER");
        }
        MigrationExecution execution = migrationExecutionRepository.findById(migrationId)
          .orElseThrow(() -> new InvalidValueException(
            "INVALID_MIGRATION_REQUEST",
            "migrationId does not identify an existing migration"
          ));
        execution.setCycleMode(request.getCycleMode());
        execution.setStatus(MigrationExecutionStatus.RUNNING);
        migrationExecutionRepository.save(execution);
        transferPhaseCoordinator.start(migrationId)
          .whenComplete((ignored, error) -> {
            if (error != null) {
              // TODO: Persist asynchronous coordinator failures in the migration status.
              log.error("Failed to start TRANSFER coordinator for migration {}", migrationId, error);
            }
          });
      }
      case COMPLETE -> {
        MigrationExecution execution = migrationExecutionRepository.save(toMigrationExecution(request));
        migrationId = execution.getId();
        if (migrationId == null) {
          throw new IllegalStateException("Saved migration execution has no id");
        }
        completeMigrationPhaseCoordinator.start(migrationId);
      }
      case EXPORT -> throw new UnsupportedOperationException("EXPORT orchestration is not implemented yet");
      default -> throw new UnsupportedOperationException("Unsupported migration phase");
    }

    return new MigrationResponseDto()
      .migrationId(migrationId)
      .phase(phase)
      .status(MigrationExecutionStatus.RUNNING)
      .cycleMode(request.getCycleMode())
      .ipaCodes(phase == MigrationPhase.TRANSFER ? null : request.getIpaCodes())
      .createdAt(OffsetDateTime.now(ZoneOffset.UTC));
  }

  private MigrationExecution toMigrationExecution(MigrationRequestDto request) {
    PeriodDto period = request.getPeriod();
    LocalDate dateFrom = null;
    LocalDate dateTo = null;
    if (period != null) {
      dateFrom = period.getFromDate();
      dateTo = period.getToDate();
    }

    return MigrationExecution.builder()
      .brokerIpaCode(brokerIpaCode)
      .status(MigrationExecutionStatus.RUNNING)
      .cycleNumber(INITIAL_CYCLE_NUMBER)
      .ipacodes(serialize(request.getIpaCodes()))
      .cycleMode(request.getCycleMode())
      .dateFrom(dateFrom)
      .dateTo(dateTo)
      .logicalKeys(request.getLogicalKey())
      .fileTypes(serialize(request.getFileTypesToInclude()))
      .build();
  }

  private String serialize(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Unable to serialize migration request for persistence", e);
    }
  }
}
