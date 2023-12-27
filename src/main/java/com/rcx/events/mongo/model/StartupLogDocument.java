package com.rcx.events.mongo.model;

import java.util.Date;
import org.springframework.data.mongodb.core.mapping.Document;
import lombok.Data;

@Data
@Document(collection = "startup_log")
public class StartupLogDocument {

  private String _id;
  private String hostname;
  private Date startTime;
  private String startTimeLocal;

}
