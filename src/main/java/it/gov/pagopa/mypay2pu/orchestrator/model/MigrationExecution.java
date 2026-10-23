package it.gov.pagopa.mypay2pu.orchestrator.model;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.OrchestrationMode;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationAction;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationExecutionStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = false)
public class MigrationExecution extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;
  @Enumerated(EnumType.STRING)
  @NotNull
  private MigrationExecutionStatus status;
  @NotNull
  private Integer fileTypeCountCompleted;
  @NotEmpty
  private List<String> ipacodes;
  @Enumerated(EnumType.STRING)
  @NotNull
  private OrchestrationMode orchestrationMode;
  @Enumerated(EnumType.STRING)
  @NotNull
  private MigrationAction action;
  private UUID parentExecutionId;
  private LocalDate dateFrom;
  private LocalDate dateTo;
  private LocalDateTime sinceDate;
  private LocalDateTime lastSuccessfulExtractionAt;
  private String errorCode;
  private String errorMsg;
  private String logicalKeys;
  @Enumerated(EnumType.STRING)
  @NotEmpty
  private List<MigrationFileType> fileTypes;
}
