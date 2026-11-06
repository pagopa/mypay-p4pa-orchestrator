package it.gov.pagopa.mypay2pu.orchestrator.service.operations.debtpositionstypeorg;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.service.operations.AbstractMigrationTransferOperation;
import org.springframework.stereotype.Service;

@Service
public class DebtPositionsTypeOrgMigrationTransferOperation extends AbstractMigrationTransferOperation {

  @Override
  public MigrationFileType getMigrationFileType() {
    return MigrationFileType.DEBT_POSITIONS_TYPE_ORG;
  }
}
