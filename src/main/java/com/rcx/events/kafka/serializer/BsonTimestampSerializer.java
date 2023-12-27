package com.rcx.events.kafka.serializer;

import java.io.IOException;
import org.bson.BsonTimestamp;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

public class BsonTimestampSerializer extends JsonSerializer<BsonTimestamp> {

  @Override
  public void serialize(BsonTimestamp value, JsonGenerator gen, SerializerProvider serializers)
      throws IOException {
    gen.writeString(String.valueOf(value.getValue()));
  }
}
