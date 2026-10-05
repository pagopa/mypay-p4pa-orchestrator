package it.gov.pagopa.mypay2pu.orchestrator.dto;

import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.CycleMode;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationExecutionStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MigrationExecutionDto {

  private UUID id;
  private String brokerIpaCode;
  private MigrationExecutionStatus status;
  private Integer cycleNumber;
  private String ipaCodes;
  private CycleMode cycleMode;
  private UUID parentExecutionId;
  private LocalDate dateFrom;
  private LocalDate dateTo;
  private LocalDateTime sinceDate;
  private LocalDateTime lastSuccessfulExtractionAt;
  private String errorCode;
  private String errorMsg;
  private String logicalKeys;
  private String fileTypes;
}
