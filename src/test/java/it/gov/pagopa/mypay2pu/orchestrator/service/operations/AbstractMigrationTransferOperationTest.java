package it.gov.pagopa.mypay2pu.orchestrator.service.operations;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationRequestDto;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AbstractMigrationTransferOperationTest {

  @ParameterizedTest
  @EnumSource(MigrationFileType.class)
  void givenFileTypeWhenExecuteThenThrowNotImplementedException(MigrationFileType fileType) {
    AbstractMigrationTransferOperation operation = new AbstractMigrationTransferOperation(fileType) { };
    MigrationRequestDto request = new MigrationRequestDto();

    UnsupportedOperationException exception = assertThrows(
      UnsupportedOperationException.class,
      () -> operation.execute(request)
    );

    assertEquals(
      "TRANSFER for %s is not implemented: submitting the extracted file to p4pa-migration and handling its workflow status belong to the Transfer component tasks."
        .formatted(fileType),
      exception.getMessage()
    );
  }
}
