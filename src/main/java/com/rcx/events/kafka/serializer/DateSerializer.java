package com.rcx.events.kafka.serializer;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

public class DateSerializer extends JsonSerializer<Date> {

  @Override
  public void serialize(Date value, JsonGenerator gen, SerializerProvider serializers)
      throws IOException {
    if (value != null) {
      gen.writeString(
          String.valueOf(new SimpleDateFormat("yyyy-MM-dd\'T\'HH:mm:ss.SSS\'Z\'").format(value)));
    }
  }
}
