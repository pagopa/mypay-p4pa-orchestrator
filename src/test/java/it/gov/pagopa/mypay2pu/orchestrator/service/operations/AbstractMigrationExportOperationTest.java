package it.gov.pagopa.mypay2pu.orchestrator.service.operations;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationRequestDto;
import it.gov.pagopa.mypay2pu.orchestrator.service.MigrationExportOperationExecutor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class AbstractMigrationExportOperationTest {

  @Mock
  private MigrationExportOperationExecutor operationExecutorMock;

  @AfterEach
  void verifyMocks() {
    verifyNoMoreInteractions(operationExecutorMock);
  }

  @ParameterizedTest
  @EnumSource(MigrationFileType.class)
  void givenFileTypeWhenExecuteThenDelegateOriginalRequestAndFileType(MigrationFileType fileType) {
    MigrationRequestDto request = new MigrationRequestDto();
    AbstractMigrationExportOperation operation = operation(fileType);

    operation.execute(request);

    verify(operationExecutorMock).execute(same(request), eq(fileType));
  }

  @Test
  void givenExecutorFailureWhenExecuteThenPropagateSameException() {
    MigrationRequestDto request = new MigrationRequestDto();
    MigrationFileType fileType = MigrationFileType.ORGANIZATIONS;
    AbstractMigrationExportOperation operation = operation(fileType);
    IllegalStateException failure = new IllegalStateException("Extraction failed");
    doThrow(failure).when(operationExecutorMock).execute(same(request), eq(fileType));

    IllegalStateException exception = assertThrows(IllegalStateException.class, () -> operation.execute(request));

    assertSame(failure, exception);
    verify(operationExecutorMock).execute(same(request), eq(fileType));
  }

  private AbstractMigrationExportOperation operation(MigrationFileType fileType) {
    return new AbstractMigrationExportOperation(operationExecutorMock) {
      @Override
      public MigrationFileType getMigrationFileType() {
        return fileType;
      }
    };
  }
}
