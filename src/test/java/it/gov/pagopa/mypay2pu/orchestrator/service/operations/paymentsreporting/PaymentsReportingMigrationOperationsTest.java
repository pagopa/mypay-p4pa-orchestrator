package it.gov.pagopa.mypay2pu.orchestrator.service.operations.paymentsreporting;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationRequestDto;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaymentsReportingMigrationOperationsTest {

  @Test
  void givenMyPayExportOperationWhenGetMigrationFileTypeThenReturnMyPayType() {
    PaymentsReportingMyPayMigrationExportOperation operation = new PaymentsReportingMyPayMigrationExportOperation();

    assertEquals(MigrationFileType.PAYMENTS_REPORTING_MYPAY, operation.getMigrationFileType());
  }

  @Test
  void givenMyPayExportOperationWhenExecuteThenThrowNotImplementedException() {
    PaymentsReportingMyPayMigrationExportOperation operation = new PaymentsReportingMyPayMigrationExportOperation();
    MigrationRequestDto request = new MigrationRequestDto();

    UnsupportedOperationException exception = assertThrows(
      UnsupportedOperationException.class,
      () -> operation.execute(request)
    );

    assertEquals(
      "EXPORT for PAYMENTS_REPORTING_MYPAY is not implemented: the Extractor request and extraction-status handling belong to the Extract component tasks.",
      exception.getMessage()
    );
  }

  @Test
  void givenMyPayTransferOperationWhenGetMigrationFileTypeThenReturnMyPayType() {
    PaymentsReportingMyPayMigrationTransferOperation operation = new PaymentsReportingMyPayMigrationTransferOperation();

    assertEquals(MigrationFileType.PAYMENTS_REPORTING_MYPAY, operation.getMigrationFileType());
  }

  @Test
  void givenMyPayTransferOperationWhenExecuteThenThrowNotImplementedException() {
    PaymentsReportingMyPayMigrationTransferOperation operation = new PaymentsReportingMyPayMigrationTransferOperation();
    MigrationRequestDto request = new MigrationRequestDto();

    UnsupportedOperationException exception = assertThrows(
      UnsupportedOperationException.class,
      () -> operation.execute(request)
    );

    assertEquals(
      "TRANSFER for PAYMENTS_REPORTING_MYPAY is not implemented: submitting the extracted file to p4pa-migration and handling its workflow status belong to the Transfer component tasks.",
      exception.getMessage()
    );
  }

  @Test
  void givenMyPivotExportOperationWhenGetMigrationFileTypeThenReturnMyPivotType() {
    PaymentsReportingMyPivotMigrationExportOperation operation = new PaymentsReportingMyPivotMigrationExportOperation();

    assertEquals(MigrationFileType.PAYMENTS_REPORTING_MYPIVOT, operation.getMigrationFileType());
  }

  @Test
  void givenMyPivotExportOperationWhenExecuteThenThrowNotImplementedException() {
    PaymentsReportingMyPivotMigrationExportOperation operation = new PaymentsReportingMyPivotMigrationExportOperation();
    MigrationRequestDto request = new MigrationRequestDto();

    UnsupportedOperationException exception = assertThrows(
      UnsupportedOperationException.class,
      () -> operation.execute(request)
    );

    assertEquals(
      "EXPORT for PAYMENTS_REPORTING_MYPIVOT is not implemented: the Extractor request and extraction-status handling belong to the Extract component tasks.",
      exception.getMessage()
    );
  }

  @Test
  void givenMyPivotTransferOperationWhenGetMigrationFileTypeThenReturnMyPivotType() {
    PaymentsReportingMyPivotMigrationTransferOperation operation = new PaymentsReportingMyPivotMigrationTransferOperation();

    assertEquals(MigrationFileType.PAYMENTS_REPORTING_MYPIVOT, operation.getMigrationFileType());
  }

  @Test
  void givenMyPivotTransferOperationWhenExecuteThenThrowNotImplementedException() {
    PaymentsReportingMyPivotMigrationTransferOperation operation = new PaymentsReportingMyPivotMigrationTransferOperation();
    MigrationRequestDto request = new MigrationRequestDto();

    UnsupportedOperationException exception = assertThrows(
      UnsupportedOperationException.class,
      () -> operation.execute(request)
    );

    assertEquals(
      "TRANSFER for PAYMENTS_REPORTING_MYPIVOT is not implemented: submitting the extracted file to p4pa-migration and handling its workflow status belong to the Transfer component tasks.",
      exception.getMessage()
    );
  }
}
