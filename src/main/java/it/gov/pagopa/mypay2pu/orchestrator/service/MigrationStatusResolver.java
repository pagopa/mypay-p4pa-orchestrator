package it.gov.pagopa.mypay2pu.orchestrator.service;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationAction;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationExecutionStatus;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationFileExecutionStatus;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationExecution;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationFileExecution;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class MigrationStatusResolver {

  public MigrationExecutionStatus resolveTransferStatus(MigrationExecution execution, List<MigrationFileExecution> files) {
    if (execution.getAction() == MigrationAction.EXPORT) {
      return MigrationExecutionStatus.RUNNING;
    }
    if (files.stream().anyMatch(file -> file.getStatus() == MigrationFileExecutionStatus.FAILED)) {
      return MigrationExecutionStatus.FAILED;
    }
    if (!hasAllSelectedFiles(execution, files) || !files.stream().allMatch(this::isSuccessfulFile)) {
      return execution.getStatus() == MigrationExecutionStatus.FAILED
        ? MigrationExecutionStatus.FAILED : MigrationExecutionStatus.RUNNING;
    }
    return files.stream().anyMatch(file -> file.getStatus() == MigrationFileExecutionStatus.WARNING)
      ? MigrationExecutionStatus.WARNING : MigrationExecutionStatus.COMPLETED;
  }

  public boolean hasAllSelectedFiles(MigrationExecution execution, List<MigrationFileExecution> files) {
    Set<MigrationFileType> fileTypes = files.stream().map(MigrationFileExecution::getFileType).collect(Collectors.toSet());
    return !files.isEmpty() && execution.getFileTypes().size() == files.size()
      && fileTypes.size() == files.size() && fileTypes.containsAll(execution.getFileTypes());
  }

  public boolean isSuccessfulFile(MigrationFileExecution file) {
    return file.getStatus() == MigrationFileExecutionStatus.COMPLETED
      || file.getStatus() == MigrationFileExecutionStatus.WARNING;
  }
}
