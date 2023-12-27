package com.rcx.events.kafka.serializer;

import java.util.Date;
import java.util.Map;
import org.apache.kafka.common.serialization.Serializer;
import org.bson.BsonTimestamp;
import org.bson.types.ObjectId;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.newrelic.api.agent.Trace;
import com.rcx.events.mongo.model.OplogDocument;

public class OplogDocumentSerializer implements Serializer<OplogDocument> {

  ObjectMapper objectMapper = new ObjectMapper();

  @Override
  public void configure(Map configs, boolean isKey) {
    SimpleModule oidMod = new SimpleModule("ObjectId", new Version(1, 0, 0, null, null, null));
    oidMod.addSerializer(ObjectId.class, new ObjectIdSerializer());
    objectMapper.registerModule(oidMod);

    SimpleModule bsonTimestampModule =
        new SimpleModule("BsonTimestamp", new Version(1, 0, 0, null, null, null));
    bsonTimestampModule.addSerializer(BsonTimestamp.class, new BsonTimestampSerializer());
    objectMapper.registerModule(bsonTimestampModule);

    SimpleModule isoDateModule = new SimpleModule("Date", new Version(1, 0, 0, null, null, null));
    isoDateModule.addSerializer(Date.class, new DateSerializer());
    objectMapper.registerModule(isoDateModule);
  }

  @Override
  @Trace
  public byte[] serialize(String topic, OplogDocument od) {
    byte[] retVal = null;
    try {
      retVal = objectMapper.writeValueAsString(od).getBytes();
    } catch (Exception e) {
      e.printStackTrace();
    }
    return retVal;
  }

  @Override
  public void close() {}

}
