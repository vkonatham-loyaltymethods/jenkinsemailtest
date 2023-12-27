package com.rcx.events.kafka;

import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.bson.BsonObjectId;
import org.jboss.logging.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.util.concurrent.ListenableFutureCallback;
import com.newrelic.api.agent.Trace;
import com.rcx.events.enums.EventPublisherEnums;
import com.rcx.events.mongo.model.CheckPointDocsMap;
import com.rcx.events.mongo.model.CheckPointDocument;
import com.rcx.events.mongo.model.OplogDocument;
import com.rcx.events.properties.MessageOperation;
import com.rcx.events.properties.RcxEventsProperties;
import com.rcx.events.properties.RcxKafkaProperties;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class KafkaPublisher {

  @Autowired
  private KafkaTemplate<String, OplogDocument> kafkaTemplate;

  @Autowired
  private RcxKafkaProperties kafkaProperties;

  @Autowired
  private RcxEventsProperties eventProperties;

  @Autowired
  @Qualifier("reactiveCheckpointMongoTemplate")
  private ReactiveMongoTemplate reactiveCheckpointMongoTemplate;

  Semaphore semaphore = new Semaphore(1);

  private String hostName;
  private String checkPointCollectionName;
  private String consumerId;
  private String partition;
  AtomicLong publisherCount = null;

  public KafkaPublisher(String hostName, String checkPointCollectionName, String partition,
      String consumerId) {
    this.hostName = hostName;
    this.checkPointCollectionName = checkPointCollectionName;
    this.partition = partition;
    this.consumerId = consumerId;
    publisherCount = new AtomicLong(1);
  }

  @Trace
  private void updatedCheckPointCollection(ProducerRecord<String, OplogDocument> prodRecrd) {
    CheckPointDocument checkPointDoc = CheckPointDocsMap.get(checkPointCollectionName);

    try {
      reactiveCheckpointMongoTemplate.save(checkPointDoc, checkPointCollectionName).block();
      CheckPointDocsMap.remove(checkPointCollectionName);

      log.debug("OplogReader '{}' listener with partition id: {} successfully checkpointed ts: {}",
          consumerId, partition, prodRecrd.value().getTs());
      log.trace(
          "OplogReader '{}' sent message key={} with timestamp {} to kafka topic {} & saved successfully for partition {}",
          consumerId, prodRecrd.key(), prodRecrd.value().getTs(), prodRecrd.topic(), partition);
    } catch (Exception ex) {
      log.error(
          "OplogReader '{}' listener for partition {} unable to update check-point for message with timestamp: {} with error: ",
          consumerId, partition, prodRecrd.value().getTs(), ex);
    }
  }

  @Trace
  public void send(OplogDocument payload, String key) {
    try {
      semaphore.acquire();
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    sendMessage(payload, key, 0, false);
  }

  private void sendMessage(OplogDocument payload, String key, int sendRetries,
      boolean isFromRedirectFailed) {

    ListenableFuture<SendResult<String, OplogDocument>> future = null;
    boolean isRedirectTopic = false;
    String topicName = kafkaProperties.getTopicName();
    try {
      if (eventProperties.getMessageRedirectMap() != null && !isFromRedirectFailed) {
        for (MessageOperation mOp : eventProperties.getMessageRedirectMap()) {
          if (payload.getNs().contains(mOp.getNs()) && payload.getOp().contains(mOp.getOp())) {
            topicName = mOp.getTopicName();
            isRedirectTopic = true;
            break;
          }
        }
      }
      future = kafkaTemplate.send(topicName, key, payload);
    } catch (Exception ex) {
      if (isRedirectTopic) {
        log.error("Redirect message with ns '{}' and key {} to {} topic is failed {}",
            payload.getNs(), key, topicName, ex);
        log.info("Redirecting message with ns '{}' and key {} to {} topic.", () -> payload.getNs(),
            () -> key, () -> kafkaProperties.getTopicName());
        sendMessage(payload, key, sendRetries, true);
        return;
      } else {
        log.error(
            "OplogReader '{}' for partition {} unable to post message with timestamp: {} and key: {} to Kafka with error: {}",
            consumerId, partition, payload.getTs(), key, ex);
        semaphore.release();
        throw ex;
      }
    }

    future.addCallback(new ListenableFutureCallback<SendResult<String, OplogDocument>>() {

      @Override
      @Trace
      public void onSuccess(SendResult<String, OplogDocument> result) {
        try {
          MDC.put(EventPublisherEnums.OPLOG_PARTION.getValue(), partition);
          MDC.put(EventPublisherEnums.CONSUMER_ID.getValue(), consumerId);
          if (null != publisherCount
              && publisherCount.incrementAndGet() % kafkaProperties.getLogFrequencyWrites() == 0) {
            log.info("sent message counter for counsumer {} and partition {} is: {}", consumerId,
                partition, publisherCount.get());
          }

          ProducerRecord<String, OplogDocument> prodRecrd = result.getProducerRecord();

          log.info(
              "OplogReader {} on partition {} successfully sent the message ( key: {} ns: '{}' event: '{}' timestamp: {}) to ( topic: {} partition: {} )",
              () -> consumerId, () -> partition, () -> prodRecrd.key(),
              () -> prodRecrd.value().getNs(), () -> prodRecrd.value().getOp(),
              () -> prodRecrd.value().getWall(), () -> prodRecrd.topic(),
              () -> result.getRecordMetadata().partition());

          log.trace(
              "OplogReader '{}' successfully sent the message: {} to topic: {} for partition {}",
              consumerId, prodRecrd.value(), prodRecrd.topic(), partition);

          updateChechPointDocMap(prodRecrd);

          if (null != publisherCount
              && publisherCount.get() % kafkaProperties.getCheckPointFreq() == 0) {
            updatedCheckPointCollection(prodRecrd);
          }
        } catch (Exception e) {
          log.error(
              "Error while printing log statements after successfully publishing a message {}", e);
        }
        semaphore.release();
      }

      @Override
      @Trace
      public void onFailure(Throwable ex) {
        MDC.put(EventPublisherEnums.OPLOG_PARTION.getValue(), partition);
        MDC.put(EventPublisherEnums.CONSUMER_ID.getValue(), consumerId);
        log.error(
            "OplogReader '{}' for partition {} unable to post message with timestamp: {} and key: {} to Kafka with error:",
            consumerId, partition, payload.getTs(), key, ex);
        if (sendRetries < kafkaProperties.getMaxProducerRetries()) {
          log.info(
              "Kafka Publisher retry attempt {} to send message with key {} for consumer {} and partition {}",
              sendRetries + 1, key, consumerId, partition);
          sendMessage(payload, key, sendRetries + 1, false);
        } else {
          semaphore.release();
          log.info(
              "Retry attempts exhausted for message with key {} for consumer {} and partition {}",
              key, consumerId, partition);
        }
      }
    });
  }

  private void updateChechPointDocMap(ProducerRecord<String, OplogDocument> prodRecrd) {
    CheckPointDocument checkPointDoc = new CheckPointDocument();
    checkPointDoc.setId(new BsonObjectId());
    checkPointDoc.setTs(prodRecrd.value().getTs());
    checkPointDoc.setTopic(kafkaProperties.getTopicName());
    checkPointDoc.setHost(hostName);

    CheckPointDocsMap.put(checkPointCollectionName, checkPointDoc);
  }

}
