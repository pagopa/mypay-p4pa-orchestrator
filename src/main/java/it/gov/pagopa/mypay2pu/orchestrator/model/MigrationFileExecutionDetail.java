package it.gov.pagopa.mypay2pu.orchestrator.model;

import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationExecutionStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

@Entity
@AllArgsConstructor
@NoArgsConstructor
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = false)
public class MigrationFileExecutionDetail {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;
  @NotNull
  private UUID migrationFileExecutionId;
  @NotNull
  @Enumerated(EnumType.STRING)
  private MigrationExecutionStatus status;
  @NotNull
  private String fileName;
  private Long uploadDetailId;
  @NotNull
  private Long fileSize;
  private String errorDescription;
  private String discardFileName;
  private Integer numTotalRows;
  private Integer numCorrectlyImportedRows;
  @Column(updatable = false)
  @CreatedDate
  private LocalDateTime creationDate;
  @LastModifiedDate
  private LocalDateTime updateDate;
}
