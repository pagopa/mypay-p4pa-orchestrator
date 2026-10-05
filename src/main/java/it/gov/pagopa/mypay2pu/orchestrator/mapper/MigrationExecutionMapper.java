package it.gov.pagopa.mypay2pu.orchestrator.mapper;

import it.gov.pagopa.mypay2pu.orchestrator.dto.MigrationExecutionDto;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationExecution;
import org.springframework.stereotype.Component;

@Component
public class MigrationExecutionMapper {

  public MigrationExecutionDto toDto(MigrationExecution model) {
    return MigrationExecutionDto.builder()
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
        .build();
  }

  public MigrationExecution toModel(MigrationExecutionDto dto) {
    return MigrationExecution.builder()
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
        .build();
  }
}
