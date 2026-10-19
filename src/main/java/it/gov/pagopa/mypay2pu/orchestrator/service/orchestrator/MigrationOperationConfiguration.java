package it.gov.pagopa.mypay2pu.orchestrator.service.orchestrator;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class MigrationOperationConfiguration {

  @Bean
  MigrationExportOperation organizationsExportOperation() {
    return exportOperation(MigrationFileType.ORGANIZATIONS);
  }

  @Bean
  MigrationExportOperation orgSilServicesExportOperation() {
    return exportOperation(MigrationFileType.ORG_SIL_SERVICES);
  }

  @Bean
  MigrationExportOperation debtPositionsTypeExportOperation() {
    return exportOperation(MigrationFileType.DEBT_POSITIONS_TYPE);
  }

  @Bean
  MigrationExportOperation debtPositionsTypeOrgExportOperation() {
    return exportOperation(MigrationFileType.DEBT_POSITIONS_TYPE_ORG);
  }

  @Bean
  MigrationExportOperation debtPositionsTypeOrgOperatorsExportOperation() {
    return exportOperation(MigrationFileType.DEBT_POSITIONS_TYPE_ORG_OPERATORS);
  }

  @Bean
  MigrationExportOperation debtPositionsExportOperation() {
    return exportOperation(MigrationFileType.DEBT_POSITIONS);
  }

  @Bean
  MigrationExportOperation debtPositionsPaidExportOperation() {
    return exportOperation(MigrationFileType.DEBT_POSITIONS_PAID);
  }

  @Bean
  MigrationExportOperation paymentNotificationExportOperation() {
    return exportOperation(MigrationFileType.PAYMENT_NOTIFICATION);
  }

  @Bean
  MigrationExportOperation paymentsReportingExportOperation() {
    return exportOperation(MigrationFileType.PAYMENTS_REPORTING);
  }

  @Bean
  MigrationExportOperation treasuryCsvCompleteExportOperation() {
    return exportOperation(MigrationFileType.TREASURY_CSV_COMPLETE);
  }

  @Bean
  MigrationExportOperation assessmentsExportOperation() {
    return exportOperation(MigrationFileType.ASSESSMENTS);
  }

  @Bean
  MigrationExportOperation assessmentsRegistryExportOperation() {
    return exportOperation(MigrationFileType.ASSESSMENTS_REGISTRY);
  }

  @Bean
  MigrationTransferOperation organizationsTransferOperation() {
    return transferOperation(MigrationFileType.ORGANIZATIONS);
  }

  @Bean
  MigrationTransferOperation orgSilServicesTransferOperation() {
    return transferOperation(MigrationFileType.ORG_SIL_SERVICES);
  }

  @Bean
  MigrationTransferOperation debtPositionsTypeTransferOperation() {
    return transferOperation(MigrationFileType.DEBT_POSITIONS_TYPE);
  }

  @Bean
  MigrationTransferOperation debtPositionsTypeOrgTransferOperation() {
    return transferOperation(MigrationFileType.DEBT_POSITIONS_TYPE_ORG);
  }

  @Bean
  MigrationTransferOperation debtPositionsTypeOrgOperatorsTransferOperation() {
    return transferOperation(MigrationFileType.DEBT_POSITIONS_TYPE_ORG_OPERATORS);
  }

  @Bean
  MigrationTransferOperation debtPositionsTransferOperation() {
    return transferOperation(MigrationFileType.DEBT_POSITIONS);
  }

  @Bean
  MigrationTransferOperation debtPositionsPaidTransferOperation() {
    return transferOperation(MigrationFileType.DEBT_POSITIONS_PAID);
  }

  @Bean
  MigrationTransferOperation paymentNotificationTransferOperation() {
    return transferOperation(MigrationFileType.PAYMENT_NOTIFICATION);
  }

  @Bean
  MigrationTransferOperation paymentsReportingTransferOperation() {
    return transferOperation(MigrationFileType.PAYMENTS_REPORTING);
  }

  @Bean
  MigrationTransferOperation treasuryCsvCompleteTransferOperation() {
    return transferOperation(MigrationFileType.TREASURY_CSV_COMPLETE);
  }

  @Bean
  MigrationTransferOperation assessmentsTransferOperation() {
    return transferOperation(MigrationFileType.ASSESSMENTS);
  }

  @Bean
  MigrationTransferOperation assessmentsRegistryTransferOperation() {
    return transferOperation(MigrationFileType.ASSESSMENTS_REGISTRY);
  }

  private static MigrationExportOperation exportOperation(MigrationFileType fileType) {
    return new MigrationExportOperationImpl(fileType);
  }

  private static MigrationTransferOperation transferOperation(MigrationFileType fileType) {
    return new MigrationTransferOperationImpl(fileType);
  }
}
