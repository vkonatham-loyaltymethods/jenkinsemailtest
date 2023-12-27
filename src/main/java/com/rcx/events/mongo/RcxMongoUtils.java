package com.rcx.events.mongo;

import java.util.Date;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.stereotype.Component;
import com.rcx.events.mongo.model.CheckPointDocsMap;
import com.rcx.events.mongo.model.CheckPointDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

/**
 * @author kranthi
 *
 */
@Component
@Log4j2
@RequiredArgsConstructor
public class RcxMongoUtils {

  @Autowired
  @Qualifier("reactiveCheckpointMongoTemplate")
  private ReactiveMongoTemplate reactiveCheckpointMongoTemplate;

  public void updateCheckPointCollns() {
    Map<String, CheckPointDocument> checkPointDocMap = CheckPointDocsMap.getCheckPointDocsMap();

    if (checkPointDocMap.isEmpty()) {
      log.info("SIGTERM caught; nothing to update in checkpoint collection");
      return;
    }
    log.info("Updating checkpoint collection for SIGTERM {} ", checkPointDocMap);

    checkPointDocMap.keySet().forEach(collName -> {
      CheckPointDocument checkPointDoc = checkPointDocMap.get(collName);
      try {
        reactiveCheckpointMongoTemplate.save(checkPointDoc, collName).block();
        log.info("Updated the {} collection with ts: {} for SIGTERM ", collName,
            new Date(checkPointDoc.getTs().getTime() * 1000L));
        CheckPointDocsMap.remove(collName);
      } catch (DuplicateKeyException dke) {
        log.warn(
            "The checkpoint record is already updated in {} collection with ts: {} for SIGTERM. doc: {} ",
            collName, checkPointDoc.getTs(), checkPointDoc);
      } catch (Exception ex) {
        log.error("Error while updating the {} collection with ts: {} for SIGTERM ", collName,
            checkPointDoc.getTs(), ex);
      }
    });
  }

}
