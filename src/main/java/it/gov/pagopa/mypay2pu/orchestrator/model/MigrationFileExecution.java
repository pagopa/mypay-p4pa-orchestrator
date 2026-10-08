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
@Table(name = "migration_file_execution", schema = "orchestrator")
@AllArgsConstructor
@NoArgsConstructor
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = false)
public class MigrationFileExecution {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @NotNull
  @Column(name = "migration_id", nullable = false)
  private UUID migrationId;

  @NotNull
  @Column(name = "file_type", nullable = false, length = 50)
  private String fileType;

  @NotNull
  @Column(nullable = false, length = 20)
  private String status;

  @Column(name = "extractor_id", length = 100)
  private String extractorId;

  @Column(name = "file_path", length = 512)
  private String filePath;

  @Column(name = "extractor_error_code", length = 100)
  private String extractorErrorCode;

  @Column(name = "extractor_error_msg", columnDefinition = "TEXT")
  private String extractorErrorMsg;

  @Column(name = "upload_id")
  private Long uploadId;

  @Column(name = "upload_status", length = 20)
  private String uploadStatus;

  @Column(name = "upload_error_code", length = 100)
  private String uploadErrorCode;

  @Column(name = "upload_error_msg", columnDefinition = "TEXT")
  private String uploadErrorMsg;

  @Column(name = "extraction_started_at")
  private LocalDateTime extractionStartedAt;

  @Column(name = "extraction_completed_at")
  private LocalDateTime extractionCompletedAt;

  @Column(name = "upload_started_at")
  private LocalDateTime uploadStartedAt;

  @Column(name = "upload_completed_at")
  private LocalDateTime uploadCompletedAt;

  @NotNull
  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @NotNull
  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;
}
