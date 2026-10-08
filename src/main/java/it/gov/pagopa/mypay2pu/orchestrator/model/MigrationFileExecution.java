package it.gov.pagopa.mypay2pu.orchestrator.model;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationExecutionStatus;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.UploadStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Entity
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
  private UUID migrationId;
  @NotNull
  @Enumerated(EnumType.STRING)
  private MigrationFileType fileType;
  @NotNull
  @Enumerated(EnumType.STRING)
  private MigrationExecutionStatus status;
  private String extractorId;
  private String filePath;
  private String extractorErrorCode;
  private String extractorErrorMsg;
  private Long uploadId;
  @Enumerated(EnumType.STRING)
  private UploadStatus uploadStatus;
  private String uploadErrorCode;
  private String uploadErrorMsg;
  private LocalDateTime extractionStartedAt;
  private LocalDateTime extractionCompletedAt;
  private LocalDateTime uploadStartedAt;
  private LocalDateTime uploadCompletedAt;
  @NotNull
  private LocalDateTime createdAt;
  @NotNull
  private LocalDateTime updatedAt;
}
