package it.gov.pagopa.mypay2pu.orchestrator.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "migration_file_execution_detail", schema = "orchestrator")
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
  @Column(name = "migration_file_execution_id", nullable = false)
  private UUID migrationFileExecutionId;

  @NotNull
  @Column(nullable = false, length = 20)
  private String status;

  @NotNull
  @Column(name = "file_name", nullable = false, length = 256)
  private String fileName;

  @Column(name = "upload_detail_id")
  private Long uploadDetailId;

  @NotNull
  @Column(name = "file_size", nullable = false)
  private Long fileSize;

  @Column(name = "error_description", columnDefinition = "TEXT")
  private String errorDescription;

  @Column(name = "discard_file_name", columnDefinition = "TEXT")
  private String discardFileName;

  @Column(name = "num_total_rows")
  private Integer numTotalRows;

  @Column(name = "num_correctly_imported_rows")
  private Integer numCorrectlyImportedRows;

  @NotNull
  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @NotNull
  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;
}
