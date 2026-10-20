package it.gov.pagopa.mypay2pu.orchestrator.service.operations;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationRequestDto;
import it.gov.pagopa.mypay2pu.orchestrator.service.operations.assessments.AssessmentsMigrationExportOperation;
import it.gov.pagopa.mypay2pu.orchestrator.service.operations.assessments.AssessmentsMigrationTransferOperation;
import it.gov.pagopa.mypay2pu.orchestrator.service.operations.assessmentsregistry.AssessmentsRegistryMigrationExportOperation;
import it.gov.pagopa.mypay2pu.orchestrator.service.operations.assessmentsregistry.AssessmentsRegistryMigrationTransferOperation;
import it.gov.pagopa.mypay2pu.orchestrator.service.operations.debtpositions.DebtPositionsMigrationExportOperation;
import it.gov.pagopa.mypay2pu.orchestrator.service.operations.debtpositions.DebtPositionsMigrationTransferOperation;
import it.gov.pagopa.mypay2pu.orchestrator.service.operations.debtpositionspaid.DebtPositionsPaidMigrationExportOperation;
import it.gov.pagopa.mypay2pu.orchestrator.service.operations.debtpositionspaid.DebtPositionsPaidMigrationTransferOperation;
import it.gov.pagopa.mypay2pu.orchestrator.service.operations.debtpositionstype.DebtPositionsTypeMigrationExportOperation;
import it.gov.pagopa.mypay2pu.orchestrator.service.operations.debtpositionstype.DebtPositionsTypeMigrationTransferOperation;
import it.gov.pagopa.mypay2pu.orchestrator.service.operations.debtpositionstypeorg.DebtPositionsTypeOrgMigrationExportOperation;
import it.gov.pagopa.mypay2pu.orchestrator.service.operations.debtpositionstypeorg.DebtPositionsTypeOrgMigrationTransferOperation;
import it.gov.pagopa.mypay2pu.orchestrator.service.operations.debtpositionstypeorgoperators.DebtPositionsTypeOrgOperatorsMigrationExportOperation;
import it.gov.pagopa.mypay2pu.orchestrator.service.operations.debtpositionstypeorgoperators.DebtPositionsTypeOrgOperatorsMigrationTransferOperation;
import it.gov.pagopa.mypay2pu.orchestrator.service.operations.organizations.OrganizationsMigrationExportOperation;
import it.gov.pagopa.mypay2pu.orchestrator.service.operations.organizations.OrganizationsMigrationTransferOperation;
import it.gov.pagopa.mypay2pu.orchestrator.service.operations.orgsilservices.OrgSilServicesMigrationExportOperation;
import it.gov.pagopa.mypay2pu.orchestrator.service.operations.orgsilservices.OrgSilServicesMigrationTransferOperation;
import it.gov.pagopa.mypay2pu.orchestrator.service.operations.paymentnotification.PaymentNotificationMigrationExportOperation;
import it.gov.pagopa.mypay2pu.orchestrator.service.operations.paymentnotification.PaymentNotificationMigrationTransferOperation;
import it.gov.pagopa.mypay2pu.orchestrator.service.operations.paymentsreporting.PaymentsReportingMigrationExportOperation;
import it.gov.pagopa.mypay2pu.orchestrator.service.operations.paymentsreporting.PaymentsReportingMigrationTransferOperation;
import it.gov.pagopa.mypay2pu.orchestrator.service.operations.treasurycsvcomplete.TreasuryCsvCompleteMigrationExportOperation;
import it.gov.pagopa.mypay2pu.orchestrator.service.operations.treasurycsvcomplete.TreasuryCsvCompleteMigrationTransferOperation;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MigrationOperationTest {

  @ParameterizedTest
  @MethodSource("operations")
  void givenConcreteOperationWhenGetMigrationFileTypeThenReturnExpectedFileType(MigrationFileType expectedFileType, MigrationOperation operation) {
    assertEquals(expectedFileType, operation.getMigrationFileType());
  }

  @ParameterizedTest
  @MethodSource("operations")
  void givenUnimplementedOperationWhenExecuteThenThrowUnsupportedOperationException(MigrationFileType expectedFileType, MigrationOperation operation) {
    UnsupportedOperationException exception = assertThrows(
      UnsupportedOperationException.class, () -> operation.execute(new MigrationRequestDto())
    );

    assertTrue(exception.getMessage().contains(expectedFileType.name()));
  }

  private static Stream<Arguments> operations() {
    return Stream.of(
      Arguments.of(MigrationFileType.DEBT_POSITIONS_TYPE, new DebtPositionsTypeMigrationExportOperation()),
      Arguments.of(MigrationFileType.DEBT_POSITIONS_TYPE, new DebtPositionsTypeMigrationTransferOperation()),
      Arguments.of(MigrationFileType.ORGANIZATIONS, new OrganizationsMigrationExportOperation()),
      Arguments.of(MigrationFileType.ORGANIZATIONS, new OrganizationsMigrationTransferOperation()),
      Arguments.of(MigrationFileType.ORG_SIL_SERVICES, new OrgSilServicesMigrationExportOperation()),
      Arguments.of(MigrationFileType.ORG_SIL_SERVICES, new OrgSilServicesMigrationTransferOperation()),
      Arguments.of(MigrationFileType.DEBT_POSITIONS_TYPE_ORG, new DebtPositionsTypeOrgMigrationExportOperation()),
      Arguments.of(MigrationFileType.DEBT_POSITIONS_TYPE_ORG, new DebtPositionsTypeOrgMigrationTransferOperation()),
      Arguments.of(MigrationFileType.DEBT_POSITIONS_TYPE_ORG_OPERATORS, new DebtPositionsTypeOrgOperatorsMigrationExportOperation()),
      Arguments.of(MigrationFileType.DEBT_POSITIONS_TYPE_ORG_OPERATORS, new DebtPositionsTypeOrgOperatorsMigrationTransferOperation()),
      Arguments.of(MigrationFileType.DEBT_POSITIONS, new DebtPositionsMigrationExportOperation()),
      Arguments.of(MigrationFileType.DEBT_POSITIONS, new DebtPositionsMigrationTransferOperation()),
      Arguments.of(MigrationFileType.DEBT_POSITIONS_PAID, new DebtPositionsPaidMigrationExportOperation()),
      Arguments.of(MigrationFileType.DEBT_POSITIONS_PAID, new DebtPositionsPaidMigrationTransferOperation()),
      Arguments.of(MigrationFileType.PAYMENT_NOTIFICATION, new PaymentNotificationMigrationExportOperation()),
      Arguments.of(MigrationFileType.PAYMENT_NOTIFICATION, new PaymentNotificationMigrationTransferOperation()),
      Arguments.of(MigrationFileType.PAYMENTS_REPORTING, new PaymentsReportingMigrationExportOperation()),
      Arguments.of(MigrationFileType.PAYMENTS_REPORTING, new PaymentsReportingMigrationTransferOperation()),
      Arguments.of(MigrationFileType.TREASURY_CSV_COMPLETE, new TreasuryCsvCompleteMigrationExportOperation()),
      Arguments.of(MigrationFileType.TREASURY_CSV_COMPLETE, new TreasuryCsvCompleteMigrationTransferOperation()),
      Arguments.of(MigrationFileType.ASSESSMENTS_REGISTRY, new AssessmentsRegistryMigrationExportOperation()),
      Arguments.of(MigrationFileType.ASSESSMENTS_REGISTRY, new AssessmentsRegistryMigrationTransferOperation()),
      Arguments.of(MigrationFileType.ASSESSMENTS, new AssessmentsMigrationExportOperation()),
      Arguments.of(MigrationFileType.ASSESSMENTS, new AssessmentsMigrationTransferOperation())
    );
  }
}
