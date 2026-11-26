package it.gov.pagopa.mypay2pu.orchestrator.service.operations;

import it.gov.pagopa.mypay2pu.extractor.dto.generated.ExtractionRequest;
import it.gov.pagopa.mypay2pu.extractor.dto.generated.MigrationFileType;
import it.gov.pagopa.mypay2pu.orchestrator.exception.InvalidValueException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RejectExtractionRequestFactoryTest {
  private final RejectExtractionRequestFactory factory = new RejectExtractionRequestFactory();

  @ParameterizedTest
  @EnumSource(value = MigrationFileType.class, mode = EnumSource.Mode.EXCLUDE,
    names = {"ORGANIZATIONS", "ORG_SIL_SERVICES", "PAYMENTS_REPORTING_MYPIVOT"})
  void createsOneFilteredRequestForEachSupportedType(MigrationFileType type) {
    String key = type == MigrationFileType.DEBT_POSITIONS_TYPE_ORG_OPERATORS
      || type == MigrationFileType.TREASURY_CSV_COMPLETE ? "key1,key2|key3,key4" : "key1,key2";
    ExtractionRequest request = factory.create(type, "IPA1", key);

    assertEquals(List.of("IPA1"), request.getIpaCodes());
    assertEquals(type, request.getFileTypes());
    assertNull(request.getLastExtractionDate());
    assertEquals(key, request.getFilters().getLogicalKey());
    assertNull(request.getFilters().getDateFrom());
    assertNull(request.getFilters().getDateTo());
  }

  @ParameterizedTest
  @EnumSource(value = MigrationFileType.class, names = {"ORGANIZATIONS", "ORG_SIL_SERVICES"})
  void createsIpaOnlyRequestForTypesWithoutLogicalKey(MigrationFileType type) {
    ExtractionRequest request = factory.create(type, "IPA1", null);

    assertEquals(List.of("IPA1"), request.getIpaCodes());
    assertEquals(type, request.getFileTypes());
    assertNull(request.getFilters());
    assertNull(request.getLastExtractionDate());
    assertThrows(InvalidValueException.class, () -> factory.create(type, "IPA1", "key"));
    assertThrows(InvalidValueException.class, () -> factory.create(type, "IPA1", " "));
  }

  @Test
  void preservesTheFormattedPairedKey() {
    ExtractionRequest request = factory.create(
      MigrationFileType.DEBT_POSITIONS_TYPE_ORG_OPERATORS, "IPA1", "CF1,CF2|TYPE1,TYPE2");
    assertEquals("CF1,CF2|TYPE1,TYPE2", request.getFilters().getLogicalKey());
  }

  @Test
  void rejectsMissingScopeOrKeyAndUnsupportedType() {
    assertThrows(InvalidValueException.class, () -> factory.create(null, "IPA1", "key"));
    assertThrows(InvalidValueException.class, () -> factory.create(MigrationFileType.DEBT_POSITIONS, null, "key"));
    assertThrows(InvalidValueException.class, () -> factory.create(MigrationFileType.DEBT_POSITIONS, " ", "key"));
    assertThrows(InvalidValueException.class, () -> factory.create(MigrationFileType.DEBT_POSITIONS, "IPA1", null));
    assertThrows(InvalidValueException.class, () -> factory.create(MigrationFileType.DEBT_POSITIONS, "IPA1", " "));
    assertThrows(InvalidValueException.class,
      () -> factory.create(MigrationFileType.PAYMENTS_REPORTING_MYPIVOT, "IPA1", "key"));
  }
}
