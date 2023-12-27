package com.rcx.events.properties;

import lombok.Data;

/**
 * @author kranthi
 *
 */
@Data
public class MessageOperation {

  private String ns;
  private String op;
  private String topicName;

}
