package it.gov.pagopa.mypay2pu.orchestrator.service.operations;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationRequestDto;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MigrationCompleteOperationTest {

  @Test
  void givenMatchingOperationsWhenExecuteThenExportBeforeTransfer() {
    List<String> executionOrder = new ArrayList<>();
    MigrationCompleteOperation completeOperation = new MigrationCompleteOperation(
      exportOperation(executionOrder),
      transferOperation(executionOrder)
    );

    completeOperation.execute(new MigrationRequestDto());

    assertEquals(List.of("export", "transfer"), executionOrder);
  }

  private MigrationExportOperation exportOperation(List<String> executionOrder) {
    return new MigrationExportOperation() {
      @Override
      public MigrationFileType getMigrationFileType() {
        return MigrationFileType.ORGANIZATIONS;
      }

      @Override
      public void execute(MigrationRequestDto request) {
        executionOrder.add("export");
      }
    };
  }

  private MigrationTransferOperation transferOperation(List<String> executionOrder) {
    return new MigrationTransferOperation() {
      @Override
      public MigrationFileType getMigrationFileType() {
        return MigrationFileType.ORGANIZATIONS;
      }

      @Override
      public void execute(MigrationRequestDto request) {
        executionOrder.add("transfer");
      }
    };
  }
}
