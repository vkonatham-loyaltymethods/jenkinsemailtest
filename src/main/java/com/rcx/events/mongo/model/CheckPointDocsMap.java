package com.rcx.events.mongo.model;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.lang.NonNull;
import lombok.extern.log4j.Log4j2;

/**
 * @author kranthi
 *
 */

@Log4j2
public class CheckPointDocsMap {

  private static Map<String, CheckPointDocument> checkPointContextMap =
      new ConcurrentHashMap<String, CheckPointDocument>();

  public static void put(@NonNull final String key, final CheckPointDocument value) {
    checkPointContextMap.put(key, value);
    log.debug("Added the key {} to checkpoint map for Sigterm ", () -> key);
  }

  public static CheckPointDocument get(@NonNull final String key) {
    return checkPointContextMap.get(key);
  }

  public static void remove(final String key) {
    checkPointContextMap.remove(key);
    log.debug("Removed the key {} from checkpoint map for Sigterm ", () -> key);
  }

  public static Map<String, CheckPointDocument> getCheckPointDocsMap() {
    return checkPointContextMap;
  }

}
