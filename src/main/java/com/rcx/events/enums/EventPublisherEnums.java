package com.rcx.events.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum EventPublisherEnums {

  OPLOG_PARTION("oplog-partition"), CONSUMER_ID("consumer-id");

  @Getter
  private final String value;

}
