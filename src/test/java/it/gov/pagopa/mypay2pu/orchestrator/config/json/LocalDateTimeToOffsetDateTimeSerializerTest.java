package it.gov.pagopa.mypay2pu.orchestrator.config.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.Month;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class LocalDateTimeToOffsetDateTimeSerializerTest {

  @Mock
  private JsonGenerator jsonGenerator;

  @Mock
  private SerializerProvider serializerProvider;

  private final LocalDateTimeToOffsetDateTimeSerializer dateTimeSerializer =
      new LocalDateTimeToOffsetDateTimeSerializer();

  @AfterEach
  void verifyMocks() {
    verifyNoMoreInteractions(jsonGenerator, serializerProvider);
  }

  @Test
  void serializesWinterDateWithStandardOffset() throws IOException {
    LocalDateTime localDateTime = LocalDateTime.of(2025, Month.JANUARY, 16, 9, 15, 20);
    dateTimeSerializer.serialize(localDateTime, jsonGenerator, serializerProvider);

    verify(jsonGenerator).writeString("2025-01-16T09:15:20+01:00");
  }

  @Test
  void serializesSummerDateWithDaylightSavingOffset() throws IOException {
    LocalDateTime localDateTime = LocalDateTime.of(2025, Month.JULY, 16, 9, 15, 20);
    dateTimeSerializer.serialize(localDateTime, jsonGenerator, serializerProvider);

    verify(jsonGenerator).writeString("2025-07-16T09:15:20+02:00");
  }

  @Test
  void doesNotWriteNullValue() throws IOException {
    dateTimeSerializer.serialize(null, jsonGenerator, serializerProvider);

    verifyNoInteractions(jsonGenerator, serializerProvider);
  }
}
