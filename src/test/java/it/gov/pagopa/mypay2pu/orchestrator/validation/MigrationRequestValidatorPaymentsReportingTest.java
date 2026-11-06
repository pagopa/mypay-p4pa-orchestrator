package it.gov.pagopa.mypay2pu.orchestrator.validation;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationAction;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationRequestDto;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.OrchestrationMode;
import it.gov.pagopa.mypay2pu.orchestrator.exception.InvalidValueException;
import it.gov.pagopa.mypay2pu.orchestrator.repository.MigrationExecutionRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class MigrationRequestValidatorPaymentsReportingTest {
  @Mock
  private MigrationExecutionRepository migrationExecutionRepositoryMock;

  private MigrationRequestValidator validator;

  @BeforeEach
  void setUp() {
    validator = new MigrationRequestValidator(migrationExecutionRepositoryMock);
  }

  @AfterEach
  void verifyMocks() {
    verifyNoMoreInteractions(migrationExecutionRepositoryMock);
  }

  @Test
  void givenRequestWithBothPaymentsReportingTypesWhenValidateThenRejectRequest() {
    for (MigrationAction action : List.of(MigrationAction.EXPORT, MigrationAction.COMPLETE, MigrationAction.TRANSFER)) {
      MigrationRequestDto request = request(action);
      request.setFileTypesToInclude(List.of(
        MigrationFileType.PAYMENTS_REPORTING_MYPAY,
        MigrationFileType.PAYMENTS_REPORTING_MYPIVOT
      ));

      InvalidValueException exception = assertThrows(InvalidValueException.class, () -> validator.validate(request));

      assertEquals(
        "fileTypesToInclude must not contain both PAYMENTS_REPORTING_MYPAY and PAYMENTS_REPORTING_MYPIVOT",
        exception.getMessage()
      );
    }
  }

  @Test
  void givenRequestWithOnePaymentsReportingTypeWhenValidateThenAcceptRequest() {
    for (MigrationAction action : List.of(MigrationAction.EXPORT, MigrationAction.COMPLETE, MigrationAction.TRANSFER)) {
      for (MigrationFileType fileType : List.of(
        MigrationFileType.PAYMENTS_REPORTING_MYPAY,
        MigrationFileType.PAYMENTS_REPORTING_MYPIVOT
      )) {
        MigrationRequestDto request = request(action);
        request.setFileTypesToInclude(List.of(fileType));

        assertDoesNotThrow(() -> validator.validate(request));
      }
    }
  }

  private MigrationRequestDto request(MigrationAction action) {
    MigrationRequestDto request = new MigrationRequestDto();
    request.setAction(action);
    request.setOrchestrationMode(OrchestrationMode.DELTA);
    request.setIpaCodes(List.of("80000000000"));
    request.setFileTypesToInclude(List.of(MigrationFileType.ORGANIZATIONS));
    if (action == MigrationAction.TRANSFER) {
      request.setMigrationId(UUID.randomUUID());
    }
    return request;
  }
}
