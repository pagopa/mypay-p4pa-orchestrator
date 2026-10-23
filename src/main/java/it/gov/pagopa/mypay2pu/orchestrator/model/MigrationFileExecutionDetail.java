package it.gov.pagopa.mypay2pu.orchestrator.model;

import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationFileExecutionDetailStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = false)
public class MigrationFileExecutionDetail extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;
  @NotNull
  private UUID migrationFileExecutionId;
  @NotNull
  @Enumerated(EnumType.STRING)
  private MigrationFileExecutionDetailStatus status;
  @NotEmpty
  private String fileName;
  private Long uploadDetailId;
  @NotNull
  private Long fileSize;
  private String errorDescription;
  private String discardFileName;
  private Integer numTotalRows;
  private Integer numCorrectlyImportedRows;
}
