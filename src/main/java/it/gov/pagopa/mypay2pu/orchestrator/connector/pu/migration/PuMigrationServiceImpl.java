package it.gov.pagopa.mypay2pu.orchestrator.connector.pu.migration;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.connector.pu.auth.PuAuthnService;
import it.gov.pagopa.mypay2pu.orchestrator.connector.pu.migration.client.PuMigrationFileClient;
import it.gov.pagopa.mypay2pu.orchestrator.connector.pu.migration.config.PuMigrationApiClientConfig;
import it.gov.pagopa.mypay2pu.orchestrator.connector.pu.migration.mapper.PuMigrationFileTypeMapper;
import it.gov.pagopa.mypay2pu.orchestrator.exception.InvalidValueException;
import it.gov.pagopa.pu.migration.dto.generated.UploadDetails;
import it.gov.pagopa.pu.migration.dto.generated.UploadMigrationFileResponseDTO;
import it.gov.pagopa.pu.migration.dto.generated.Uploads;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PuMigrationServiceImpl implements PuMigrationService {

  private final PuMigrationFileClient puMigrationFileClient;
  private final PuMigrationApiClientConfig clientConfig;
  private final PuAuthnService puAuthnService;
  private final PuMigrationFileTypeMapper puMigrationFileTypeMapper;

  public PuMigrationServiceImpl(
    PuMigrationFileClient puMigrationFileClient,
    PuMigrationApiClientConfig clientConfig,
    PuAuthnService puAuthnService,
    PuMigrationFileTypeMapper puMigrationFileTypeMapper
  ) {
    this.puMigrationFileClient = puMigrationFileClient;
    this.clientConfig = clientConfig;
    this.puAuthnService = puAuthnService;
    this.puMigrationFileTypeMapper = puMigrationFileTypeMapper;
  }

  @Override
  public UploadMigrationFileResponseDTO uploadMigrationFile(
    MigrationFileType migrationFileType,
    Resource file
  ) {
    return puMigrationFileClient.uploadMigrationFile(
      brokerIpaCode(),
      puMigrationFileTypeMapper.toPuMigrationFileType(migrationFileType),
      file,
      puAuthnService.getAccessToken()
    );
  }

  @Override
  public Uploads getMigrationUpload(Long uploadId) {
    return puMigrationFileClient.getMigrationUpload(
      brokerIpaCode(), uploadId, puAuthnService.getAccessToken()
    );
  }

  @Override
  public List<UploadDetails> getMigrationUploadDetails(Long uploadId) {
    return puMigrationFileClient.getMigrationUploadDetails(
      brokerIpaCode(), uploadId, puAuthnService.getAccessToken()
    );
  }

  @Override
  public Resource getMigrationErrors(Long uploadId) {
    return puMigrationFileClient.getMigrationErrors(
      brokerIpaCode(), uploadId, puAuthnService.getAccessToken()
    );
  }

  private String brokerIpaCode() {
    String brokerIpaCode = clientConfig.getBrokerIpaCode();
    if (brokerIpaCode == null || brokerIpaCode.isBlank()) {
      throw new InvalidValueException(
        "TRANSFER_BROKER_IPA_CODE_MISSING",
        "Broker IPA code is not configured"
      );
    }
    return brokerIpaCode;
  }
}
