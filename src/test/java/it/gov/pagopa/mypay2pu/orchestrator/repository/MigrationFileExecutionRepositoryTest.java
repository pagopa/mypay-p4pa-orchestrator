package it.gov.pagopa.mypay2pu.orchestrator.repository;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationFileExecutionStatus;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationFileExecution;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.Set;
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
  void givenDifferentMigrationsAndTypesWhenLookingUpExecutionsThenReturnsOnlyMatchingOnes() {
    UUID migrationId = UUID.randomUUID();
    MigrationFileExecution first = repository.saveAndFlush(MigrationFileExecution.builder()
      .migrationId(migrationId).fileType(MigrationFileType.ORGANIZATIONS)
      .status(MigrationFileExecutionStatus.EXPORTED).build());
    MigrationFileExecution second = repository.saveAndFlush(MigrationFileExecution.builder()
      .migrationId(migrationId).fileType(MigrationFileType.ORGANIZATIONS)
      .status(MigrationFileExecutionStatus.EXPORTED).build());
    repository.saveAndFlush(MigrationFileExecution.builder()
      .migrationId(migrationId).fileType(MigrationFileType.DEBT_POSITIONS)
      .status(MigrationFileExecutionStatus.EXPORTED).build());
    repository.saveAndFlush(MigrationFileExecution.builder()
      .migrationId(UUID.randomUUID()).fileType(MigrationFileType.ORGANIZATIONS)
      .status(MigrationFileExecutionStatus.EXPORTED).build());

    var executions = repository.findAllByMigrationIdAndFileTypeOrderByCreationDateAscIdAsc(
      migrationId, MigrationFileType.ORGANIZATIONS);
    assertEquals(Set.of(first.getId(), second.getId()),
      executions.stream().map(MigrationFileExecution::getId).collect(java.util.stream.Collectors.toSet()));
    assertEquals(2, executions.size());
  }

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
      execution.getId(),
      STARTED_AT
    );
    int duplicateClaim = repository.claimTransfer(
      execution.getId(),
      STARTED_AT.plusMinutes(1)
    );
    MigrationFileExecution persisted = repository.findById(execution.getId()).orElseThrow();

    assertEquals(1, firstClaim);
    assertEquals(0, duplicateClaim);
    assertEquals(MigrationFileExecutionStatus.TRANSFERING, persisted.getStatus());
    assertEquals(STARTED_AT, persisted.getUploadStartedAt());
  }

  @Test
  void givenExecutionsOfSameTypeWhenClaimingOneThenOtherIsUnchanged() {
    UUID migrationId = UUID.randomUUID();
    MigrationFileExecution first = repository.saveAndFlush(MigrationFileExecution.builder()
      .migrationId(migrationId)
      .fileType(MigrationFileType.ORGANIZATIONS)
      .status(MigrationFileExecutionStatus.EXPORTED)
      .build());
    MigrationFileExecution second = repository.saveAndFlush(MigrationFileExecution.builder()
      .migrationId(migrationId)
      .fileType(MigrationFileType.ORGANIZATIONS)
      .status(MigrationFileExecutionStatus.EXPORTED)
      .build());

    assertEquals(1, repository.claimTransfer(first.getId(), STARTED_AT));
    assertEquals(MigrationFileExecutionStatus.TRANSFERING,
      repository.findById(first.getId()).orElseThrow().getStatus());
    assertEquals(MigrationFileExecutionStatus.EXPORTED,
      repository.findById(second.getId()).orElseThrow().getStatus());
    assertEquals(1, repository.claimTransfer(second.getId(), STARTED_AT));
  }

  @Test
  void givenUnreadyOrAlreadyUploadedExecutionWhenClaimingThenDoesNotChangeIt() {
    MigrationFileExecution unready = repository.saveAndFlush(MigrationFileExecution.builder()
      .migrationId(UUID.randomUUID())
      .fileType(MigrationFileType.ORGANIZATIONS)
      .status(MigrationFileExecutionStatus.PENDING)
      .build());
    MigrationFileExecution uploaded = repository.saveAndFlush(MigrationFileExecution.builder()
      .migrationId(UUID.randomUUID())
      .fileType(MigrationFileType.ORGANIZATIONS)
      .status(MigrationFileExecutionStatus.EXPORTED)
      .uploadId(42L)
      .build());

    assertEquals(0, repository.claimTransfer(unready.getId(), STARTED_AT));
    assertEquals(0, repository.claimTransfer(uploaded.getId(), STARTED_AT));
    assertEquals(0, repository.claimTransfer(UUID.randomUUID(), STARTED_AT));
    assertEquals(MigrationFileExecutionStatus.PENDING,
      repository.findById(unready.getId()).orElseThrow().getStatus());
    assertEquals(MigrationFileExecutionStatus.EXPORTED,
      repository.findById(uploaded.getId()).orElseThrow().getStatus());
  }
}
