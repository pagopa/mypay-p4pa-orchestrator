package it.gov.pagopa.mypay2pu.orchestrator.model;


import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Entity
@Getter
public class MigrationDagDependency {
  @Id
  private Integer id;
  @NotNull
  @Enumerated(EnumType.STRING)
  private MigrationFileType fileType;
  @NotNull
  private String dependsOn;
  private String label;
  private Integer maxExecutions;
}
