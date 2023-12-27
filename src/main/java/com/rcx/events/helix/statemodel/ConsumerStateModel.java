package com.rcx.events.helix.statemodel;

import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.apache.helix.NotificationContext;
import org.apache.helix.ZNRecord;
import org.apache.helix.model.Message;
import org.apache.helix.participant.statemachine.StateModel;
import org.apache.helix.participant.statemachine.StateModelInfo;
import org.apache.helix.participant.statemachine.Transition;
import org.apache.helix.store.zk.ZkHelixPropertyStore;
import org.apache.logging.log4j.ThreadContext;
import org.jboss.logging.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import com.rcx.events.enums.EventPublisherEnums;
import com.rcx.events.mongo.OplogReader;
import com.rcx.events.mongo.RcxMongoUtils;
import com.rcx.events.properties.RcxEventsProperties;
import lombok.extern.log4j.Log4j2;

@StateModelInfo(initialState = "OFFLINE", states = {"ONLINE", "DROPPED", "ERROR"})
@Log4j2
public class ConsumerStateModel extends StateModel {
  private final String consumerId;
  private final String partition;
  private OplogReader oplogReader = null;

  private ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
  private Future future;

  @Autowired
  private RcxEventsProperties rcxEventsProperties;

  @Autowired
  private AutowireCapableBeanFactory beanFactory;

  @Autowired
  private RcxMongoUtils rcxMongoUtils;

  public ConsumerStateModel(String consumerId, String partition,
      ZkHelixPropertyStore<ZNRecord> propertyStore) {
    this.consumerId = consumerId;
    this.partition = partition;
  }

  @Transition(from = "OFFLINE", to = "ONLINE")
  public void onBecomeOnlineFromOffline(Message message, NotificationContext context)
      throws InterruptedException {
    log.info("Received ONLINE event for OplogReader {} for partition {}", consumerId, partition);
    long delayBeforeCheck = rcxEventsProperties.getStateModelOnlineWaittime();
    future = executor.schedule(new Runnable() {
      @Override
      public void run() {
        if (oplogReader == null) {
          try {
            MDC.put(EventPublisherEnums.OPLOG_PARTION.getValue(), partition);
            MDC.put(EventPublisherEnums.CONSUMER_ID.getValue(), consumerId);
            oplogReader = new OplogReader(consumerId, partition);
            beanFactory.autowireBean(oplogReader);
            oplogReader.bindConsumerToOplogReader();
            log.info("Starting OplogReader {} for partition {}", consumerId, partition);
            oplogReader.startContainer();
            log.info("OplogReader {} is ONLINE for partition {}", consumerId, partition);
          } catch (Exception e) {
            log.error(
                "Got exception in OplogReader {} for partition {} while trying to connect to mongo: ",
                consumerId, partition, e);
            throw e;
          }
        }
      }
    }, delayBeforeCheck, TimeUnit.MILLISECONDS);
  }

  private void stopOplogReader(String from) {
    if (future != null) {
      future.cancel(true);
    }
    if (oplogReader != null) {
      log.info("Stopping OplogReader {} for partition {} ", consumerId, partition);
      oplogReader.stopOplogReader();
      oplogReader = null;
    }
    log.info("OplogReader {} is OFFLINE from {} for partition {}", consumerId, from, partition);
  }

  @Transition(from = "OFFLINE", to = "DROPPED")
  public void onBecomeDroppedFromOffline(Message message, NotificationContext context) {
    log.info("Received DROPPED event from OFFLINE for OplogReader {} for partition {}", consumerId,
        partition);
    stopOplogReader("DROPPED");
    rcxMongoUtils.updateCheckPointCollns();
  }

  @Transition(from = "OFFLINE", to = "ERROR")
  public void onBecomeErrorFromOffline(Message message, NotificationContext context) {
    log.info("Received ERROR event from OFFLINE for OplogReader {} for partition {}", consumerId,
        partition);
    stopOplogReader("ERROR");
  }

  @Transition(from = "ONLINE", to = "OFFLINE")
  public void onBecomeOfflineFromOnline(Message message, NotificationContext context) {
    log.info("Received OFFLINE event from ONLINE for OplogReader {} for partition {}", consumerId,
        partition);
    stopOplogReader("ONLINE");
  }

  @Transition(from = "ERROR", to = "OFFLINE")
  public void onBecomeOfflineFromError(Message message, NotificationContext context) {
    log.info("Received OFFLINE event from ERROR for OplogReader {} for partition {}", consumerId,
        partition);
    stopOplogReader("ERROR");
  }

  @Override
  public void reset() {
    stopOplogReader("reset");
  }
}
