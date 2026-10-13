package it.gov.pagopa.mypay2pu.orchestrator.service.mapper;

import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationPhase;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationResponseDto;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationExecution;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Component
public class MigrationResponseMapper {

  private static final TypeReference<List<String>> IPA_CODES_TYPE = new TypeReference<>() {};

  private final JsonMapper jsonMapper;

  public MigrationResponseMapper(JsonMapper jsonMapper) {
    this.jsonMapper = jsonMapper;
  }

  public MigrationResponseDto toMigrationResponseDto(MigrationExecution execution, MigrationPhase phase) {
    List<String> ipaCodes = null;
    OffsetDateTime createdAt = execution.getCreationDate() == null
      ? null
      : execution.getCreationDate().atOffset(ZoneOffset.UTC);
    if (phase != MigrationPhase.TRANSFER) {
      try {
        ipaCodes = jsonMapper.readValue(execution.getIpacodes(), IPA_CODES_TYPE);
      } catch (JacksonException e) {
        throw new IllegalStateException("Unable to deserialize migration execution IPA codes", e);
      }
    }

    return new MigrationResponseDto()
      .migrationId(execution.getId())
      .phase(phase)
      .status(execution.getStatus())
      .cycleMode(execution.getCycleMode())
      .ipaCodes(ipaCodes)
      .createdAt(createdAt);
  }
}
