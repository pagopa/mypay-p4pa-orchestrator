package it.gov.pagopa.mypay2pu.orchestrator.repository;

import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationFileExecutionDetail;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@TestPropertySource(properties = {
  "spring.datasource.driver-class-name=org.h2.Driver",
  "spring.datasource.url=jdbc:h2:mem:migrationFileExecutionDetail;DB_CLOSE_DELAY=-1;INIT=CREATE SCHEMA IF NOT EXISTS orchestrator",
  "spring.datasource.hikari.jdbc-url=jdbc:h2:mem:migrationFileExecutionDetail;DB_CLOSE_DELAY=-1;INIT=CREATE SCHEMA IF NOT EXISTS orchestrator",
  "spring.datasource.username=sa",
  "spring.jpa.hibernate.ddl-auto=create-drop"
})
class MigrationFileExecutionDetailRepositoryTest {

  @Autowired
  private MigrationFileExecutionDetailRepository repository;

  @Test
  void persistsAndRetrievesAllExecutionDetailFields() {
    LocalDateTime timestamp = LocalDateTime.of(2025, Month.MAY, 6, 7, 8);
    MigrationFileExecutionDetail executionDetail = MigrationFileExecutionDetail.builder()
        .migrationFileExecutionId(UUID.fromString("781ff209-6d75-45a4-90ad-6f12c5fe6550"))
        .status("COMPLETED")
        .fileName("file.csv")
        .uploadDetailId(123L)
        .fileSize(456L)
        .errorDescription("error description")
        .discardFileName("discard.csv")
        .numTotalRows(10)
        .numCorrectlyImportedRows(9)
        .createdAt(timestamp)
        .updatedAt(timestamp)
        .build();

    MigrationFileExecutionDetail savedExecutionDetail = repository.saveAndFlush(executionDetail);

    MigrationFileExecutionDetail persistedExecutionDetail =
        repository.findById(savedExecutionDetail.getId()).orElseThrow();
    assertEquals(savedExecutionDetail, persistedExecutionDetail);
  }
}
