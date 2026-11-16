package it.gov.pagopa.mypay2pu.orchestrator.connector.pu.migration.mapper;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.pu.migration.dto.generated.MigrationFileTypeEnum;
import org.springframework.stereotype.Component;

@Component
public class PuMigrationFileTypeMapper {

  public MigrationFileTypeEnum toPuMigrationFileType(MigrationFileType migrationFileType) {
    return switch (migrationFileType) {
      case ORGANIZATIONS -> MigrationFileTypeEnum.ORGANIZATIONS;
      case ORG_SIL_SERVICES -> MigrationFileTypeEnum.ORG_SIL_SERVICES;
      case DEBT_POSITIONS_TYPE -> MigrationFileTypeEnum.DEBT_POSITIONS_TYPE;
      case DEBT_POSITIONS_TYPE_ORG -> MigrationFileTypeEnum.DEBT_POSITIONS_TYPE_ORG;
      case DEBT_POSITIONS_TYPE_ORG_OPERATORS -> MigrationFileTypeEnum.DEBT_POSITIONS_TYPE_ORG_OPERATORS;
      case DEBT_POSITIONS -> MigrationFileTypeEnum.DEBT_POSITIONS;
      case DEBT_POSITIONS_PAID -> MigrationFileTypeEnum.DEBT_POSITIONS_PAID;
      case PAYMENT_NOTIFICATION -> MigrationFileTypeEnum.PAYMENT_NOTIFICATION;
      case PAYMENTS_REPORTING_MYPAY, PAYMENTS_REPORTING_MYPIVOT -> MigrationFileTypeEnum.PAYMENTS_REPORTING;
      case TREASURY_CSV_COMPLETE -> MigrationFileTypeEnum.TREASURY_CSV_COMPLETE;
      case ASSESSMENTS -> MigrationFileTypeEnum.ASSESSMENTS;
      case ASSESSMENTS_REGISTRY -> MigrationFileTypeEnum.ASSESSMENTS_REGISTRY;
    };
  }
}
