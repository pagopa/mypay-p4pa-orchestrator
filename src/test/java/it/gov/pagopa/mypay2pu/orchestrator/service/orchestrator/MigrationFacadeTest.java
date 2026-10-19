package it.gov.pagopa.mypay2pu.orchestrator.service.orchestrator;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationAction;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationRequestDto;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MigrationFacadeTest {

  @Test
  void registersImmutableOperationMapsAndFailsExplicitlyForUnimplementedUseCases() {
    try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
      MigrationOperationConfiguration.class,
      MigrationOrchestrator.class,
      MigrationFacade.class
    )) {
      MigrationFacade facade = context.getBean(MigrationFacade.class);

      assertEquals(MigrationFileType.values().length, facade.getMigrationExportOperations().size());
      assertEquals(MigrationFileType.values().length, facade.getMigrationTransferOperations().size());
      assertEquals(MigrationFileType.values().length, facade.getMigrationCompleteOperations().size());
      assertThrows(
        UnsupportedOperationException.class,
        facade.getMigrationExportOperations()::clear
      );
      assertThrows(
        UnsupportedOperationException.class,
        facade.getMigrationTransferOperations()::clear
      );
      assertThrows(
        UnsupportedOperationException.class,
        facade.getMigrationCompleteOperations()::clear
      );

      MigrationRequestDto request = new MigrationRequestDto();
      request.setAction(MigrationAction.EXPORT);
      request.setFileTypesToInclude(List.of(MigrationFileType.ORGANIZATIONS));

      UnsupportedOperationException exception = assertThrows(
        UnsupportedOperationException.class,
        () -> facade.execute(request)
      );
      assertTrue(exception.getMessage().contains("ORGANIZATIONS"));
      assertTrue(exception.getMessage().contains("Extractor"));
    }
  }

  @Test
  void completeOperationRequiresMatchingFileTypesAndExecutesExportBeforeTransfer() {
    MigrationFileType fileType = MigrationFileType.ORGANIZATIONS;
    StringBuilder executionOrder = new StringBuilder();
    MigrationExportOperation export = operation(fileType, executionOrder, "export");
    MigrationTransferOperation transfer = transferOperation(fileType, executionOrder, "transfer");

    MigrationCompleteOperation completeOperation = new MigrationCompleteOperation(export, transfer);
    completeOperation.execute(new MigrationRequestDto());

    MigrationExportOperation export1 = operation(fileType, new StringBuilder(), "export");
    MigrationTransferOperation transfer1 = transferOperation(MigrationFileType.DEBT_POSITIONS, new StringBuilder(), "transfer");
    assertEquals("export,transfer,", executionOrder.toString());
    assertThrows(IllegalArgumentException.class, () -> new MigrationCompleteOperation(export1, transfer1));
  }

  private MigrationExportOperation operation(
    MigrationFileType fileType,
    StringBuilder executionOrder,
    String action
  ) {
    return new MigrationExportOperation() {
      @Override
      public MigrationFileType migrationFileType() {
        return fileType;
      }

      @Override
      public void execute(MigrationRequestDto request) {
        executionOrder.append(action).append(',');
      }
    };
  }

  private MigrationTransferOperation transferOperation(
    MigrationFileType fileType,
    StringBuilder executionOrder,
    String action
  ) {
    return new MigrationTransferOperation() {
      @Override
      public MigrationFileType migrationFileType() {
        return fileType;
      }

      @Override
      public void execute(MigrationRequestDto request) {
        executionOrder.append(action).append(',');
      }
    };
  }
}
