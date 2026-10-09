package it.gov.pagopa.mypay2pu.orchestrator.config.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import it.gov.pagopa.mypay2pu.orchestrator.utils.Constants;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

@Configuration
public class LocalDateTimeToOffsetDateTimeSerializer extends JsonSerializer<LocalDateTime> {

  @Override
  public void serialize(LocalDateTime value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
    if (value != null) {
      OffsetDateTime offsetDateTime = convertToOffsetDateTime(value);
      gen.writeString(offsetDateTime.toString());
    }
  }

  public static OffsetDateTime convertToOffsetDateTime(LocalDateTime value) {
    return value.atZone(Constants.ZONEID).toOffsetDateTime();
  }
}
