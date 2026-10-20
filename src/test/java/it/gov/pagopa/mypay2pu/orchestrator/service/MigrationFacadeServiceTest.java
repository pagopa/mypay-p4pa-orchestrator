package it.gov.pagopa.mypay2pu.orchestrator.service;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationAction;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationRequestDto;
import it.gov.pagopa.mypay2pu.orchestrator.service.operations.MigrationCompleteOperation;
import it.gov.pagopa.mypay2pu.orchestrator.service.operations.MigrationExportOperation;
import it.gov.pagopa.mypay2pu.orchestrator.service.operations.MigrationTransferOperation;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.List;
import java.util.EnumSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MigrationFacadeServiceTest {

  @Test
  void discoversOneComponentPerFileTypeAndPhaseAndFailsExplicitlyForUnimplementedUseCases() {
    try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
      context.scan("it.gov.pagopa.mypay2pu.orchestrator.service.operations");
      context.register(MigrationPlanExecutorService.class, MigrationFacadeService.class);
      context.refresh();
      MigrationFacadeService facade = context.getBean(MigrationFacadeService.class);

      var exportOperations = context.getBeansOfType(MigrationExportOperation.class).values();
      var transferOperations = context.getBeansOfType(MigrationTransferOperation.class).values();
      assertEquals(MigrationFileType.values().length, exportOperations.size());
      assertEquals(MigrationFileType.values().length, transferOperations.size());
      assertEquals(EnumSet.allOf(MigrationFileType.class), exportOperations.stream()
        .map(MigrationExportOperation::migrationFileType).collect(Collectors.toSet()));
      assertEquals(EnumSet.allOf(MigrationFileType.class), transferOperations.stream()
        .map(MigrationTransferOperation::migrationFileType).collect(Collectors.toSet()));

      for (MigrationAction action : MigrationAction.values()) {
        for (MigrationFileType fileType : MigrationFileType.values()) {
          MigrationRequestDto request = new MigrationRequestDto();
          request.setAction(action);
          request.setFileTypesToInclude(List.of(fileType));

          UnsupportedOperationException exception = assertThrows(
            UnsupportedOperationException.class,
            () -> facade.execute(request)
          );
          assertTrue(exception.getMessage().contains(fileType.name()));
          assertTrue(exception.getMessage().contains(
            action == MigrationAction.TRANSFER ? "p4pa-migration" : "Extractor"
          ));
        }
      }
    }
  }

  @Test
  void dispatchesEachActionAndComposesOnlyMatchingExportAndTransferOperations() {
    StringBuilder executionOrder = new StringBuilder();
    MigrationFileType fileType = MigrationFileType.ORGANIZATIONS;
    try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
      context.registerBean("organizationsExport", MigrationExportOperation.class,
        () -> operation(fileType, executionOrder, "export"));
      context.registerBean("organizationsTransfer", MigrationTransferOperation.class,
        () -> transferOperation(fileType, executionOrder, "transfer"));
      context.register(MigrationPlanExecutorService.class, MigrationFacadeService.class);
      context.refresh();
      MigrationFacadeService facade = context.getBean(MigrationFacadeService.class);
      MigrationRequestDto request = new MigrationRequestDto();
      request.setFileTypesToInclude(List.of(fileType));

      request.setAction(MigrationAction.EXPORT);
      facade.execute(request);
      assertEquals("export,", executionOrder.toString());
      executionOrder.setLength(0);

      request.setAction(MigrationAction.TRANSFER);
      facade.execute(request);
      assertEquals("transfer,", executionOrder.toString());
      executionOrder.setLength(0);

      request.setAction(MigrationAction.COMPLETE);
      facade.execute(request);
      assertEquals("export,transfer,", executionOrder.toString());
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

  @Test
  void completeOperationDoesNotTransferWhenExportFails() {
    StringBuilder executionOrder = new StringBuilder();
    MigrationExportOperation export = new MigrationExportOperation() {
      @Override
      public MigrationFileType migrationFileType() {
        return MigrationFileType.ORGANIZATIONS;
      }

      @Override
      public void execute(MigrationRequestDto request) {
        throw new IllegalStateException("export failed");
      }
    };
    MigrationCompleteOperation complete = new MigrationCompleteOperation(export,
      transferOperation(MigrationFileType.ORGANIZATIONS, executionOrder, "transfer"));

    MigrationRequestDto request = new MigrationRequestDto();
    assertThrows(IllegalStateException.class,
      () -> complete.execute(request),
      "export failed"
    );
    assertEquals("", executionOrder.toString());
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
