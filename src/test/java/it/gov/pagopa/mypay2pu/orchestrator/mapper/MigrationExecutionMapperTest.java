package it.gov.pagopa.mypay2pu.orchestrator.mapper;

import it.gov.pagopa.mypay2pu.orchestrator.dto.MigrationExecutionDto;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.CycleMode;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationExecutionStatus;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationExecution;
import it.gov.pagopa.mypay2pu.orchestrator.utils.TestUtils;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MigrationExecutionMapperTest {

  private final MigrationExecutionMapper mapper = new MigrationExecutionMapper();

  @Test
  void mapsAllModelFieldsToDto() {
    MigrationExecution model = MigrationExecution.builder()
        .id(UUID.fromString("4c434c6d-b840-4a77-8f19-e61b44634c45"))
        .brokerIpaCode("broker-ipa-code")
        .status(MigrationExecutionStatus.EXTRACTED)
        .cycleNumber(12)
        .ipacodes("ipa-code-1,ipa-code-2")
        .cycleMode(CycleMode.DELTA)
        .parentExecutionId(UUID.fromString("9ce20e8d-4457-4076-8c9b-8c5506323b87"))
        .dateFrom(LocalDate.of(2025, Month.JANUARY, 3))
        .dateTo(LocalDate.of(2025, Month.FEBRUARY, 4))
        .sinceDate(LocalDateTime.of(2025, Month.MARCH, 5, 6, 7))
        .lastSuccessfulExtractionAt(LocalDateTime.of(2025, Month.APRIL, 8, 9, 10))
        .errorCode("error-code")
        .errorMsg("error message")
        .logicalKeys("logical-key-1,logical-key-2")
        .fileTypes("FILE_TYPE_A,FILE_TYPE_B")
        .build();

    MigrationExecutionDto result = mapper.toDto(model);

    TestUtils.checkNotNullFields(result);
    assertEquals(MigrationExecutionDto.builder()
        .id(model.getId())
        .brokerIpaCode(model.getBrokerIpaCode())
        .status(model.getStatus())
        .cycleNumber(model.getCycleNumber())
        .ipaCodes(model.getIpacodes())
        .cycleMode(model.getCycleMode())
        .parentExecutionId(model.getParentExecutionId())
        .dateFrom(model.getDateFrom())
        .dateTo(model.getDateTo())
        .sinceDate(model.getSinceDate())
        .lastSuccessfulExtractionAt(model.getLastSuccessfulExtractionAt())
        .errorCode(model.getErrorCode())
        .errorMsg(model.getErrorMsg())
        .logicalKeys(model.getLogicalKeys())
        .fileTypes(model.getFileTypes())
        .build(), result);
  }

  @Test
  void mapsAllDtoFieldsToModel() {
    MigrationExecutionDto dto = MigrationExecutionDto.builder()
        .id(UUID.fromString("4c434c6d-b840-4a77-8f19-e61b44634c45"))
        .brokerIpaCode("broker-ipa-code")
        .status(MigrationExecutionStatus.FAILED)
        .cycleNumber(21)
        .ipaCodes("ipa-code-3,ipa-code-4")
        .cycleMode(CycleMode.REJECTS_ONLY)
        .parentExecutionId(UUID.fromString("9ce20e8d-4457-4076-8c9b-8c5506323b87"))
        .dateFrom(LocalDate.of(2025, Month.JULY, 17))
        .dateTo(LocalDate.of(2025, Month.AUGUST, 18))
        .sinceDate(LocalDateTime.of(2025, Month.SEPTEMBER, 19, 20, 21))
        .lastSuccessfulExtractionAt(LocalDateTime.of(2025, Month.OCTOBER, 22, 23, 24))
        .errorCode("another-error-code")
        .errorMsg("another error message")
        .logicalKeys("logical-key-3,logical-key-4")
        .fileTypes("FILE_TYPE_C,FILE_TYPE_D")
        .build();

    MigrationExecution result = mapper.toModel(dto);

    TestUtils.checkNotNullFields(result);
    assertEquals(MigrationExecution.builder()
        .id(dto.getId())
        .brokerIpaCode(dto.getBrokerIpaCode())
        .status(dto.getStatus())
        .cycleNumber(dto.getCycleNumber())
        .ipacodes(dto.getIpaCodes())
        .cycleMode(dto.getCycleMode())
        .parentExecutionId(dto.getParentExecutionId())
        .dateFrom(dto.getDateFrom())
        .dateTo(dto.getDateTo())
        .sinceDate(dto.getSinceDate())
        .lastSuccessfulExtractionAt(dto.getLastSuccessfulExtractionAt())
        .errorCode(dto.getErrorCode())
        .errorMsg(dto.getErrorMsg())
        .logicalKeys(dto.getLogicalKeys())
        .fileTypes(dto.getFileTypes())
        .build(), result);
  }
}
