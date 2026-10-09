package it.gov.pagopa.mypay2pu.orchestrator.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Getter;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Getter
public class MigrationDagDependency {
  @Id
  private Integer id;
  /**
   * The persisted file type, kept as a string because the table also contains
   * the technical {@code ROOT} node, which is not a {@code MigrationFileType}.
   */
  private String fileType;
  private String dependsOn;
  private String label;
  private Integer maxExecutions;

  protected MigrationDagDependency() {
  }
}
