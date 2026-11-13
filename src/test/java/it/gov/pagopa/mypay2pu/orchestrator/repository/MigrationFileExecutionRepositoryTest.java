package it.gov.pagopa.mypay2pu.orchestrator.repository;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationFileExecutionStatus;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationFileExecution;
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
  "spring.datasource.url=jdbc:h2:mem:transfer-claim-test;DB_CLOSE_DELAY=-1",
  "spring.datasource.username=sa",
  "spring.datasource.password=",
  "spring.jpa.hibernate.ddl-auto=create-drop"
})
class MigrationFileExecutionRepositoryTest {

  private static final UUID MIGRATION_ID = UUID.fromString("6c0bba84-b67b-4ce7-b8cf-5e1fc3209ab7");
  private static final LocalDateTime STARTED_AT = LocalDateTime.of(2026, Month.NOVEMBER, 11, 12, 0);

  @Autowired
  private MigrationFileExecutionRepository repository;

  @Test
  void givenExportedExecutionWhenClaimTransferThenAllowsOnlyOneRequest() {
    MigrationFileExecution execution = repository.saveAndFlush(MigrationFileExecution.builder()
      .migrationId(MIGRATION_ID)
      .fileType(MigrationFileType.ORGANIZATIONS)
      .status(MigrationFileExecutionStatus.EXPORTED)
      .extractionId("extractor-123")
      .filePath("organizations.zip")
      .build());

    int firstClaim = repository.claimTransfer(
      MIGRATION_ID,
      MigrationFileType.ORGANIZATIONS,
      STARTED_AT
    );
    int duplicateClaim = repository.claimTransfer(
      MIGRATION_ID,
      MigrationFileType.ORGANIZATIONS,
      STARTED_AT.plusMinutes(1)
    );
    MigrationFileExecution persisted = repository.findById(execution.getId()).orElseThrow();

    assertEquals(1, firstClaim);
    assertEquals(0, duplicateClaim);
    assertEquals(MigrationFileExecutionStatus.TRANSFERING, persisted.getStatus());
    assertEquals(STARTED_AT, persisted.getUploadStartedAt());
  }
}
