package it.gov.pagopa.mypay2pu.orchestrator.repository;

import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationFileExecution;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@TestPropertySource(properties = {
  "spring.datasource.driver-class-name=org.h2.Driver",
  "spring.datasource.url=jdbc:h2:mem:migrationFileExecution;DB_CLOSE_DELAY=-1;INIT=CREATE SCHEMA IF NOT EXISTS orchestrator",
  "spring.datasource.hikari.jdbc-url=jdbc:h2:mem:migrationFileExecution;DB_CLOSE_DELAY=-1;INIT=CREATE SCHEMA IF NOT EXISTS orchestrator",
  "spring.datasource.username=sa",
  "spring.jpa.hibernate.ddl-auto=create-drop"
})
class MigrationFileExecutionRepositoryTest {

  @Autowired
  private MigrationFileExecutionRepository repository;

  @Test
  void persistsAndRetrievesAllExecutionFields() {
    LocalDateTime timestamp = LocalDateTime.of(2025, 5, 6, 7, 8);
    MigrationFileExecution execution = MigrationFileExecution.builder()
        .migrationId(UUID.fromString("781ff209-6d75-45a4-90ad-6f12c5fe6550"))
        .fileType("PAYMENT_NOTICE")
        .status("COMPLETED")
        .extractorId("extractor-id")
        .filePath("/tmp/file.csv")
        .extractorErrorCode("extractor-error")
        .extractorErrorMsg("extractor error message")
        .uploadId(123L)
        .uploadStatus("UPLOADED")
        .uploadErrorCode("upload-error")
        .uploadErrorMsg("upload error message")
        .extractionStartedAt(timestamp)
        .extractionCompletedAt(timestamp.plusMinutes(1))
        .uploadStartedAt(timestamp.plusMinutes(2))
        .uploadCompletedAt(timestamp.plusMinutes(3))
        .createdAt(timestamp)
        .updatedAt(timestamp)
        .build();

    MigrationFileExecution savedExecution = repository.saveAndFlush(execution);

    MigrationFileExecution persistedExecution = repository.findById(savedExecution.getId()).orElseThrow();
    assertEquals(savedExecution, persistedExecution);
  }
}
