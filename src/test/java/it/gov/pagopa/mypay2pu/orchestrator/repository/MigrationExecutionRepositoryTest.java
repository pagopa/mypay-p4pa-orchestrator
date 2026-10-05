package it.gov.pagopa.mypay2pu.orchestrator.repository;

import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.CycleMode;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationExecutionStatus;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationExecution;
import java.time.LocalDate;
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
  "spring.datasource.url=jdbc:h2:mem:migrationExecution;DB_CLOSE_DELAY=-1;INIT=CREATE SCHEMA IF NOT EXISTS orchestrator",
  "spring.datasource.username=sa",
  "spring.datasource.password=",
  "spring.jpa.hibernate.ddl-auto=create-drop"
})
class MigrationExecutionRepositoryTest {

  @Autowired
  private MigrationExecutionRepository repository;

  @Test
  void persistsAndRetrievesAllExecutionFields() {
    MigrationExecution execution = MigrationExecution.builder()
        .brokerIpaCode("broker-ipa-code")
        .status(MigrationExecutionStatus.FAILED)
        .cycleNumber(7)
        .ipacodes("ipa-code-1,ipa-code-2")
        .cycleMode(CycleMode.REJECTS_ONLY)
        .parentExecutionId(UUID.fromString("781ff209-6d75-45a4-90ad-6f12c5fe6550"))
        .dateFrom(LocalDate.of(2025, Month.JANUARY, 3))
        .dateTo(LocalDate.of(2025, Month.FEBRUARY, 4))
        .sinceDate(LocalDateTime.of(2025, Month.MARCH, 5, 6, 7))
        .lastSuccessfulExtractionAt(LocalDateTime.of(2025, Month.APRIL, 8, 9, 10))
        .errorCode("error-code")
        .errorMsg("error message")
        .logicalKeys("logical-key-1,logical-key-2")
        .fileTypes("FILE_TYPE_A,FILE_TYPE_B")
        .build();

    MigrationExecution savedExecution = repository.saveAndFlush(execution);

    MigrationExecution persistedExecution = repository.findById(savedExecution.getId()).orElseThrow();
    assertEquals(savedExecution, persistedExecution);
  }

}
