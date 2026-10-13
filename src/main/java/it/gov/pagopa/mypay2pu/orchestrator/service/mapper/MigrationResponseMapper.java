package it.gov.pagopa.mypay2pu.orchestrator.service.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationPhase;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationResponseDto;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationExecution;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class MigrationResponseMapper {

  private static final TypeReference<List<String>> IPA_CODES_TYPE = new TypeReference<>() {};

  private final ObjectMapper objectMapper;

  public MigrationResponseMapper(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public MigrationResponseDto toMigrationResponseDto(MigrationExecution execution, MigrationPhase phase) {
    List<String> ipaCodes = null;
    if (phase != MigrationPhase.TRANSFER) {
      try {
        ipaCodes = objectMapper.readValue(execution.getIpacodes(), IPA_CODES_TYPE);
      } catch (JsonProcessingException e) {
        throw new IllegalStateException("Unable to deserialize migration execution IPA codes", e);
      }
    }

    return new MigrationResponseDto()
      .migrationId(execution.getId())
      .phase(phase)
      .status(execution.getStatus())
      .cycleMode(execution.getCycleMode())
      .ipaCodes(ipaCodes)
      .createdAt(OffsetDateTime.now(ZoneOffset.UTC));
  }
}
