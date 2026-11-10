package it.gov.pagopa.mypay2pu.orchestrator.service.operations.organizations;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.config.ExtractionPollingProperties;
import it.gov.pagopa.mypay2pu.orchestrator.config.ExtractionPollingProperties.Defaults;
import it.gov.pagopa.mypay2pu.orchestrator.config.ExtractionPollingProperties.Timeouts;
import it.gov.pagopa.mypay2pu.orchestrator.connector.extractor.ExtractorService;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationFileExecutionStatus;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationRequestDto;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationFileExecution;
import it.gov.pagopa.mypay2pu.orchestrator.service.MigrationFileExecutionExportService;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrganizationsMigrationExportOperationTest {

  @Mock
  private ExtractorService extractorServiceMock;
  @Mock
  private MigrationFileExecutionExportService fileExecutionServiceMock;

  @AfterEach
  void verifyMocks() {
    verifyNoMoreInteractions(extractorServiceMock, fileExecutionServiceMock);
  }

  @Test
  void givenOrganizationBeanWhenExecuteThenUsesConfiguredFileType() {
    UUID migrationId = UUID.randomUUID();
    try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
      context.registerBean(ExtractorService.class, () -> extractorServiceMock);
      context.registerBean(JsonMapper.class, () -> JsonMapper.builder().build());
      context.registerBean(MigrationFileExecutionExportService.class, () -> fileExecutionServiceMock);
      context.registerBean(ExtractionPollingProperties.class,
        () -> new ExtractionPollingProperties(Duration.ofSeconds(10), Duration.ofMinutes(30),
          new Timeouts(new Defaults(Duration.ofMinutes(30), Duration.ofHours(1)), null), 5));
      context.register(OrganizationsMigrationExportOperation.class);
      context.refresh();

      OrganizationsMigrationExportOperation operation = context.getBean(OrganizationsMigrationExportOperation.class);
      assertEquals(MigrationFileType.ORGANIZATIONS, operation.getMigrationFileType());
      when(fileExecutionServiceMock.ensurePending(migrationId, MigrationFileType.ORGANIZATIONS))
        .thenReturn(MigrationFileExecution.builder().status(MigrationFileExecutionStatus.EXPORTED).build());

      operation.execute(new MigrationRequestDto().migrationId(migrationId));

      verify(fileExecutionServiceMock).ensurePending(migrationId, MigrationFileType.ORGANIZATIONS);
    }
  }
}
