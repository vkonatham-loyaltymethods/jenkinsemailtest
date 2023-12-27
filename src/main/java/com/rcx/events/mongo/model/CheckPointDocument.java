package com.rcx.events.mongo.model;

import org.bson.BsonObjectId;
import org.bson.BsonTimestamp;
import org.springframework.data.annotation.Id;
import lombok.Data;

@Data
public class CheckPointDocument {

  @Id
  private BsonObjectId id;
  private BsonTimestamp ts;
  private String host;
  private String topic;
}
