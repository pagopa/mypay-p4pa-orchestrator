package it.gov.pagopa.mypay2pu.orchestrator.service;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.dto.generated.MigrationFileExecutionDetailStatus;
import it.gov.pagopa.mypay2pu.orchestrator.exception.InvalidValueException;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationFileExecution;
import it.gov.pagopa.mypay2pu.orchestrator.model.MigrationFileExecutionDetail;
import it.gov.pagopa.mypay2pu.orchestrator.repository.MigrationFileExecutionDetailRepository;
import it.gov.pagopa.mypay2pu.orchestrator.repository.MigrationFileExecutionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RejectDetailLookupServiceTest {

  private static final UUID PARENT_ID = UUID.randomUUID();
  private static final UUID EXECUTION_ID = UUID.randomUUID();
  private static final UUID DETAIL_ID = UUID.randomUUID();
  private static final MigrationFileType TYPE = MigrationFileType.ORGANIZATIONS;

  @Mock private MigrationFileExecutionRepository executionRepository;
  @Mock private MigrationFileExecutionDetailRepository detailRepository;
  private RejectDetailLookupService service;

  @BeforeEach
  void setUp() {
    service = new RejectDetailLookupService(executionRepository, detailRepository);
  }

  @AfterEach
  void verifyMocks() {
    verifyNoMoreInteractions(executionRepository, detailRepository);
  }

  @Test
  void returnsEmptyWithoutQueryingDetailsWhenTypeIsAbsent() {
    when(executionRepository.findAllByMigrationIdAndFileType(PARENT_ID, TYPE)).thenReturn(List.of());

    assertTrue(service.findByParentAndType(PARENT_ID, TYPE).isEmpty());
  }

  @ParameterizedTest
  @MethodSource("incompleteDetails")
  void rejectsPartialDetailsWithoutRequiredSourceMetadata(MigrationFileExecution execution,
                                                          MigrationFileExecutionDetail detail) {
    when(executionRepository.findAllByMigrationIdAndFileType(PARENT_ID, TYPE)).thenReturn(List.of(execution));
    when(detailRepository.findAllByMigrationFileExecutionIdInAndStatus(
      anyCollection(), eq(MigrationFileExecutionDetailStatus.PARTIAL))).thenReturn(List.of(detail));

    InvalidValueException exception = assertThrows(InvalidValueException.class,
      () -> service.findByParentAndType(PARENT_ID, TYPE));
    assertTrue(exception.getMessage().contains(DETAIL_ID.toString()));
  }

  private static Stream<Arguments> incompleteDetails() {
    return Stream.of(
      Arguments.of(execution(12L), detail("IPA", null)),
      Arguments.of(execution(12L), detail("IPA", " ")),
      Arguments.of(execution(null), detail("IPA", "errors.csv"))
    );
  }

  private static MigrationFileExecution execution(Long uploadId) {
    return MigrationFileExecution.builder().id(EXECUTION_ID).uploadId(uploadId).build();
  }

  private static MigrationFileExecutionDetail detail(String ipa, String discardFileName) {
    return MigrationFileExecutionDetail.builder().id(DETAIL_ID).migrationFileExecutionId(EXECUTION_ID)
      .ipaCode(ipa).discardFileName(discardFileName).build();
  }
}
