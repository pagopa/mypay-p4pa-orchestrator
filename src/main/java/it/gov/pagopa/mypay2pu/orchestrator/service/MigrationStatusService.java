package it.gov.pagopa.mypay2pu.orchestrator.service;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.ExportFileStatusDto;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationStatusDto;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.TransferFileStatusDto;
import java.util.UUID;

public interface MigrationStatusService {

  MigrationStatusDto getMigrationStatus(UUID migrationId);

  ExportFileStatusDto getExportFileStatus(UUID migrationId, MigrationFileType fileType);

  TransferFileStatusDto getTransferFileStatus(UUID migrationId, MigrationFileType fileType);
}
