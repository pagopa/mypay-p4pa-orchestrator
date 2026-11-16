package it.gov.pagopa.mypay2pu.orchestrator.connector.pu.migration.mapper;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.pu.migration.dto.generated.MigrationFileTypeEnum;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PuMigrationFileTypeMapperTest {

  private final PuMigrationFileTypeMapper mapper = new PuMigrationFileTypeMapper();

  @ParameterizedTest
  @MethodSource("fileTypeMappings")
  void givenExtractorFileTypeWhenMapToPuMigrationFileTypeThenReturnsExpectedEnum(
    MigrationFileType extractorFileType,
    MigrationFileTypeEnum expectedPuFileType
  ) {
    MigrationFileTypeEnum result = mapper.toPuMigrationFileType(extractorFileType);

    assertEquals(expectedPuFileType, result);
  }

  private static Stream<Arguments> fileTypeMappings() {
    return Stream.of(
      Arguments.of(MigrationFileType.ORGANIZATIONS, MigrationFileTypeEnum.ORGANIZATIONS),
      Arguments.of(MigrationFileType.ORG_SIL_SERVICES, MigrationFileTypeEnum.ORG_SIL_SERVICES),
      Arguments.of(MigrationFileType.DEBT_POSITIONS_TYPE, MigrationFileTypeEnum.DEBT_POSITIONS_TYPE),
      Arguments.of(MigrationFileType.DEBT_POSITIONS_TYPE_ORG, MigrationFileTypeEnum.DEBT_POSITIONS_TYPE_ORG),
      Arguments.of(
        MigrationFileType.DEBT_POSITIONS_TYPE_ORG_OPERATORS,
        MigrationFileTypeEnum.DEBT_POSITIONS_TYPE_ORG_OPERATORS
      ),
      Arguments.of(MigrationFileType.DEBT_POSITIONS, MigrationFileTypeEnum.DEBT_POSITIONS),
      Arguments.of(MigrationFileType.DEBT_POSITIONS_PAID, MigrationFileTypeEnum.DEBT_POSITIONS_PAID),
      Arguments.of(MigrationFileType.PAYMENT_NOTIFICATION, MigrationFileTypeEnum.PAYMENT_NOTIFICATION),
      Arguments.of(MigrationFileType.PAYMENTS_REPORTING_MYPAY, MigrationFileTypeEnum.PAYMENTS_REPORTING),
      Arguments.of(MigrationFileType.PAYMENTS_REPORTING_MYPIVOT, MigrationFileTypeEnum.PAYMENTS_REPORTING),
      Arguments.of(MigrationFileType.TREASURY_CSV_COMPLETE, MigrationFileTypeEnum.TREASURY_CSV_COMPLETE),
      Arguments.of(MigrationFileType.ASSESSMENTS, MigrationFileTypeEnum.ASSESSMENTS),
      Arguments.of(MigrationFileType.ASSESSMENTS_REGISTRY, MigrationFileTypeEnum.ASSESSMENTS_REGISTRY)
    );
  }
}
