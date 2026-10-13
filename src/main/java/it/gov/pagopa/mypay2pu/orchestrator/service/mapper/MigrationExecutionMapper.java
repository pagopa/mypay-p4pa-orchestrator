package it.gov.pagopa.mypay2pu.orchestrator.service.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationRequestDto;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.PeriodDto;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationExecution;
import org.springframework.stereotype.Component;

@Component
public class MigrationExecutionMapper {

  private final ObjectMapper objectMapper;

  public MigrationExecutionMapper(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public MigrationExecution toMigrationExecution(
    MigrationRequestDto request,
    String brokerIpaCode
  ) {
    PeriodDto period = request.getPeriod();

    return MigrationExecution.builder()
      .brokerIpaCode(brokerIpaCode)
      .ipacodes(serialize(request.getIpaCodes()))
      .cycleMode(request.getCycleMode())
      .dateFrom(period == null ? null : period.getFromDate())
      .dateTo(period == null ? null : period.getToDate())
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
