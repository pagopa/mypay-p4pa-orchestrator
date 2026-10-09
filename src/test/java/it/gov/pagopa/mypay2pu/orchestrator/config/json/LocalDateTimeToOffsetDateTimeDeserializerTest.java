package it.gov.pagopa.mypay2pu.orchestrator.config.json;

import com.fasterxml.jackson.core.JsonParser;
import it.gov.pagopa.mypay2pu.orchestrator.utils.Constants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class LocalDateTimeToOffsetDateTimeDeserializerTest {

  private final LocalDateTimeToOffsetDateTimeDeserializer deserializer = new LocalDateTimeToOffsetDateTimeDeserializer();

  @Mock
  private JsonParser parser;

  @AfterEach
  void verifyMocks() {
    verifyNoMoreInteractions(parser);
  }

  @Test
  void givenOffsetDateTimeWhenThenOk() throws IOException {
    OffsetDateTime offsetDateTime = OffsetDateTime.of(
        LocalDateTime.of(2025, Month.JULY, 16, 9, 15, 20),
        ZoneOffset.ofHours(2));
    when(parser.getValueAsString())
      .thenReturn(offsetDateTime.toString());

    OffsetDateTime result = deserializer.deserialize(parser, null);

    Assertions.assertEquals(offsetDateTime, result);
    verify(parser).getValueAsString();
  }

  @Test
  void givenUTCOffsetDateTimeWhenThenOk() throws IOException {
    OffsetDateTime offsetDateTime = OffsetDateTime.of(
        LocalDateTime.of(2025, Month.JULY, 16, 7, 15, 20),
        ZoneOffset.UTC);
    when(parser.getValueAsString())
      .thenReturn(offsetDateTime.toString());

    OffsetDateTime result = deserializer.deserialize(parser, null);

    Assertions.assertEquals(offsetDateTime, result);
    verify(parser).getValueAsString();
  }

  @Test
  void givenLocalDateTimeWhenThenOk() throws IOException {
    LocalDateTime localDateTime = LocalDateTime.of(2025, Month.JULY, 16, 9, 15, 20);
    when(parser.getValueAsString())
      .thenReturn(localDateTime.toString());

    OffsetDateTime result = deserializer.deserialize(parser, null);

    Assertions.assertEquals(ZonedDateTime.of(localDateTime, Constants.ZONEID).toOffsetDateTime(), result);
    verify(parser).getValueAsString();
  }
}
