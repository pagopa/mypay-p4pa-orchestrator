package it.gov.pagopa.mypay2pu.orchestrator.model;

import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.CycleMode;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationExecutionStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = false)
public class MigrationExecution {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;
  @NotNull
  private String brokerIpaCode;
  @Enumerated(EnumType.STRING)
  @NotNull
  private MigrationExecutionStatus status;
  @NotNull
  private Integer cycleNumber;
  @NotNull
  private String ipacodes;
  @Enumerated(EnumType.STRING)
  @NotNull
  private CycleMode cycleMode;
  private UUID parentExecutionId;
  private LocalDate dateFrom;
  private LocalDate dateTo;
  private LocalDateTime sinceDate;
  private LocalDateTime lastSuccessfulExtractionAt;
  private String errorCode;
  private String errorMsg;
  private String logicalKeys;
  @NotNull
  private String fileTypes;
}
