package it.gov.pagopa.mypay2pu.orchestrator.service.tmp;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationRequestDto;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;

@Service
public class MigrationOrchestrator {
  private static final MigrationFileType planRoot = MigrationFileType.DEBT_POSITIONS_TYPE;

  private static final Map<MigrationFileType, Set<MigrationFileType>> MIGRATION_PLAN = Map.ofEntries(
    Map.entry(planRoot, Set.of(
      MigrationFileType.ORGANIZATIONS
    )),
    Map.entry(MigrationFileType.ORGANIZATIONS, Set.of(
      MigrationFileType.DEBT_POSITIONS_TYPE_ORG,
      MigrationFileType.ORG_SIL_SERVICES
    )),
    Map.entry(MigrationFileType.DEBT_POSITIONS_TYPE_ORG, Set.of(
      MigrationFileType.DEBT_POSITIONS
    ))
  );

  public void execute(MigrationRequestDto request, Map<MigrationFileType, MigrationOperation> operationMap) {
    executePlan(request, planRoot, operationMap);
  }

  private void executePlan(MigrationRequestDto request, MigrationFileType migrationFileType, Map<MigrationFileType, MigrationOperation> operationMap) {
    if(request.getFileTypesToInclude().contains(migrationFileType)) {
      operationMap.get(migrationFileType).execute(request);
    }

    Set<MigrationFileType> childs = MIGRATION_PLAN.get(migrationFileType);

    childs.parallelStream().forEach(child ->
      executePlan(request, child, operationMap)
    );
  }

}
