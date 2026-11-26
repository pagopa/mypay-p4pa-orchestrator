package it.gov.pagopa.mypay2pu.orchestrator.service.operations;

import it.gov.pagopa.mypay2pu.orchestrator.exception.InvalidValueException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RejectLogicalKeyFormatterTest {

  private final RejectLogicalKeyFormatter formatter = new RejectLogicalKeyFormatter();

  @Test
  void formatsAndDeduplicatesValuesInSourceOrder() {
    assertEquals("IUV1,IUV2,IUV3", formatter.formatValues(List.of(" IUV1 ", "IUV2", "IUV1", "IUV3")));
  }

  @Test
  void formatsDistinctPairsWithoutSeparatelyDeduplicatingColumns() {
    var first = new RejectLogicalKeyFormatter.KeyPair("2024", "A");
    var second = new RejectLogicalKeyFormatter.KeyPair("2024", "B");
    var third = new RejectLogicalKeyFormatter.KeyPair("2025", "A");

    assertEquals("2024,2024,2025|A,B,A", formatter.formatPairs(List.of(
      first, second, new RejectLogicalKeyFormatter.KeyPair(" 2024 ", " A "), third
    )));
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", " ", "a,b", "a|b"})
  void rejectsInvalidSingleValues(String value) {
    List<String> list = Arrays.asList("valid", value);
    InvalidValueException error = assertThrows(InvalidValueException.class,
      () -> formatter.formatValues(list));

    assertEquals("INVALID_REJECT_LOGICAL_KEY", error.getCode());
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", " ", "a,b", "a|b"})
  void rejectsInvalidValuesOnEitherSideOfPair(String value) {
    List<RejectLogicalKeyFormatter.KeyPair> first = List.of(new RejectLogicalKeyFormatter.KeyPair(value, "valid"));
    assertThrows(InvalidValueException.class, () -> formatter.formatPairs(first));
    List<RejectLogicalKeyFormatter.KeyPair> second = List.of(new RejectLogicalKeyFormatter.KeyPair("valid", value));
    assertThrows(InvalidValueException.class, () -> formatter.formatPairs(second));
  }

  @Test
  void rejectsAbsentKeysInsteadOfCreatingUnfilteredRequest() {
    assertThrows(InvalidValueException.class, () -> formatter.formatValues(null));
    assertThrows(InvalidValueException.class, () -> formatter.formatValues(List.of()));
    assertThrows(InvalidValueException.class, () -> formatter.formatPairs(null));
    assertThrows(InvalidValueException.class, () -> formatter.formatPairs(List.of()));
  }
}
