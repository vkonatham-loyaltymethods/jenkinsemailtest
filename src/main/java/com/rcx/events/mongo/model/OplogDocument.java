package com.rcx.events.mongo.model;

import java.util.Date;
import java.util.Map;
import org.bson.BsonTimestamp;
import org.springframework.data.mongodb.core.mapping.Document;
import lombok.Data;

@Data
@Document(collection = "oplog.rs")
public class OplogDocument {

  /**
   * Timestamp
   */
  private BsonTimestamp ts;

  private Long t;

  /**
   * Unique id for this entry
   */
  private Long h;

  private Integer v;

  /**
   * The operation that was performed
   */
  private String op;

  /**
   * DB and collection name of change.
   */
  private String ns;

  private String ui;
  private Map<String, Object> o2;
  private Date wall;

  /**
   * The actual document that was modified/inserted/deleted
   */
  private Map<String, Object> o;

  private boolean fromMigrate;

}
