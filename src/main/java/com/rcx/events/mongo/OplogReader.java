package com.rcx.events.mongo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.CRC32;
import org.bson.BsonTimestamp;
import org.jboss.logging.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Order;
import org.springframework.data.mongodb.core.CollectionOptions;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.util.StringUtils;
import com.newrelic.api.agent.Trace;
import com.rcx.events.enums.EventPublisherEnums;
import com.rcx.events.kafka.KafkaPublisher;
import com.rcx.events.mongo.model.CheckPointDocsMap;
import com.rcx.events.mongo.model.CheckPointDocument;
import com.rcx.events.mongo.model.OplogDocument;
import com.rcx.events.mongo.model.StartupLogDocument;
import com.rcx.events.properties.MessageOperation;
import com.rcx.events.properties.RcxEventsProperties;
import com.rcx.events.properties.RcxKafkaProperties;
import com.rcx.events.properties.RcxMongoProperties;
import lombok.extern.log4j.Log4j2;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

@Log4j2
public class OplogReader {

  private final String partition;
  private final String consumerId;
  private int instanceId;
  private String checkPointCollectionName;
  private int instanceNumber = 0;
  private List<String> namespaces = new ArrayList<String>();

  private String hostName = "default";
  private boolean stopOplogReader = false;

  @Autowired
  private RcxMongoProperties mongoProperties;

  @Autowired
  private RcxKafkaProperties rcxKafkaProperties;

  @Autowired
  @Qualifier("reactiveCheckpointMongoTemplate")
  private ReactiveMongoTemplate reactiveCheckpointMongoTemplate;

  @Autowired
  @Qualifier("reactiveOplogMongoTemplate")
  private ReactiveMongoTemplate reactiveOplogMongoTemplate;

  private Disposable subscription;

  private KafkaPublisher kafkaPublisher;

  @Autowired
  private AutowireCapableBeanFactory beanFactory;

  @Autowired
  private RcxEventsProperties rcxEventsProperties;

  Flux<OplogDocument> oplogStream = null;

  AtomicLong oplogReadCounter = null;

  private final ReentrantLock lock = new ReentrantLock();
  private static final String DELETE = "delete";
  private static final String INSERT_AND_UPDATE = "insertAndUpdate";

  public OplogReader(String consumerId, String partition) {
    this.partition = partition;
    String instance = partition.split("_")[1];
    this.instanceId = Integer.parseInt(instance);
    this.consumerId = consumerId;
  }

  public void bindConsumerToOplogReader() {
    setNameSpaces();
    log.info("bindConsumerToOplogReader {} - {}", consumerId, partition);
  }

  public void stopOplogReader() {
    lock.lock();
    stopOplogReader = true;
    if (subscription != null) {
      log.info("OplogReader {} stoping the oplog stream for partition {}", consumerId, instanceId);
      subscription.dispose();
    }
    lock.unlock();
  }

  public Disposable getDisposable() {
    return subscription;
  }

  private BsonTimestamp getCheckPointTimeStamp() {
    CheckPointDocument checkPoint = CheckPointDocsMap.get(checkPointCollectionName);
    if (checkPoint == null) {
      Query query = new Query();
      query.addCriteria(Criteria.where("topic").is(rcxKafkaProperties.getTopicName()));
      // Query Limit
      query.limit(1);
      // Sort on fields
      List<Order> sortOrders = new ArrayList<Order>();
      sortOrders.add(new Order(Sort.Direction.DESC, "ts"));
      query.with(Sort.by(sortOrders));
      // Project Fields
      query.fields().include("ts");

      log.info("Reading timestamp from checkpoint collection for partition {}", instanceId);

      checkPoint = reactiveCheckpointMongoTemplate
          .findOne(query, CheckPointDocument.class, checkPointCollectionName).block();
    } else {
      log.info("Read the timestamp from in-memory Sigterm map for partition {}", instanceId);
    }

    if (checkPoint != null && checkPoint.getTs() != null) {
      log.info(
          "OplogReader '{}' starting from last saved position {} for topic {} for partition {}.",
          consumerId, new Date(checkPoint.getTs().getTime() * 1000L),
          rcxKafkaProperties.getTopicName(), instanceId);
      return checkPoint.getTs();
    }
    final Date now = new Date();
    BsonTimestamp bts = new BsonTimestamp((int) (now.getTime() / 1000), 0);
    log.info(
        "OplogReader '{}' could not find last saved position from topic {} for partition {}. Starting from Date.Now {}.",
        consumerId, rcxKafkaProperties.getTopicName(), instanceId, new Date(bts.getTime() * 1000L));

    return bts;
  }

  private void registerContainer(Query query) {
    log.trace("OplogReader '{}' registering container for partition {}", consumerId, instanceId);
    log.info("OplogReader '{}' and partition {} starting oplog listener with query: {} ",
        consumerId, partition, query);

    oplogStream = reactiveOplogMongoTemplate.tail(query, OplogDocument.class);
    log.trace("OplogReader '{}' registered container for partition {}", consumerId, instanceId);
    subscribe();
  }

  @SuppressWarnings("unchecked")
  private synchronized void subscribe() {
    lock.lock();
    if (stopOplogReader) {
      return;
    }
    subscription = oplogStream.subscribe(opLogDoc -> {
      MDC.put(EventPublisherEnums.OPLOG_PARTION.getValue(), partition);
      MDC.put(EventPublisherEnums.CONSUMER_ID.getValue(), consumerId);
      if (null != oplogReadCounter
          && oplogReadCounter.incrementAndGet() % mongoProperties.getLogFrequencyReads() == 0) {
        log.info("Oplog read message counter for counsumer {} and partition {} is: {}", consumerId,
            partition, oplogReadCounter.get());
      }
      publishMessage(opLogDoc);
    }, new Consumer() {
      @Override
      public void accept(Object t) {
        MDC.put(EventPublisherEnums.OPLOG_PARTION.getValue(), partition);
        MDC.put(EventPublisherEnums.CONSUMER_ID.getValue(), consumerId);
        log.error("OplogReader '{}' mongo generated error for partition {}", consumerId, partition,
            t);
        subscription.dispose();
        tailOplog();
      }
    });
    lock.unlock();
  }

  private void setNameSpaces() {
    if (StringUtils.isEmpty(mongoProperties.getNsFilter())) {
      throw new RuntimeException("NS_FILTER cannot be null");
    }
    namespaces = Arrays.asList(mongoProperties.getNsFilter().split(","));
  }

  private Query createQuery() {
    log.info("OplogReader '{}' creating the filter query for partition {}", consumerId, instanceId);
    BsonTimestamp timeStamp = getCheckPointTimeStamp();

    List<String> listOfColls = namespaces.stream().map(String::new).collect(Collectors.toList());
    listOfColls.add("admin.$cmd");
    Query query = new Query();
    Criteria or1, or2, or3, or4;
    switch (rcxEventsProperties.getConsumesEvents()) {
      case DELETE: {
        or1 = Criteria.where("op").is("d");
        query.addCriteria(
            Criteria.where("ts").gt(timeStamp).and("ns").in(listOfColls).orOperator(or1));
        break;
      }

      case INSERT_AND_UPDATE: {
        or1 = new Criteria().andOperator(
            Criteria.where("op").is("u").orOperator(Criteria.where("o.$set.events.0").exists(true),
                Criteria.where("o.events.0").exists(true)));
        or2 =
            new Criteria().andOperator(Criteria.where("op").is("i").and("o.events.0").exists(true));
        or3 =
            new Criteria().andOperator(Criteria.where("op").is("c").and("o.applyOps").exists(true));
        query.addCriteria(
            Criteria.where("ts").gt(timeStamp).and("ns").in(listOfColls).orOperator(or1, or2, or3));
        break;
      }

      default: {
        or1 = new Criteria().andOperator(
            Criteria.where("op").is("u").orOperator(Criteria.where("o.$set.events.0").exists(true),
                Criteria.where("o.events.0").exists(true)));
        or2 =
            new Criteria().andOperator(Criteria.where("op").is("i").and("o.events.0").exists(true));
        or3 = Criteria.where("op").is("d");
        or4 =
            new Criteria().andOperator(Criteria.where("op").is("c").and("o.applyOps").exists(true));

        query.addCriteria(Criteria.where("ts").gt(timeStamp).and("ns").in(listOfColls)
            .orOperator(or1, or2, or3, or4));
      }

    }


    log.trace("OplogReader '{}' created the filter query for partition {}", consumerId, instanceId);
    return query;
  }

  private void connectMongo() {
    try {
      createCheckPointColl();
      String hn = getHostName();
      if (hn != null) {
        hostName = hn;
      }
    } catch (Exception ex) {
      log.error(
          "Got exception in OplogReader {} for partition {} while trying to connect to mongo: ",
          consumerId, partition, ex);
      connectMongo();
    }
  }

  public void startContainer() {
    connectMongo();

    kafkaPublisher = new KafkaPublisher(hostName, checkPointCollectionName, partition, consumerId);
    beanFactory.autowireBean(kafkaPublisher);

    tailOplog();
  }

  private void tailOplog() {
    log.info("OplogReader {} tailing oplog for partition {}", consumerId, partition);
    try {
      Query filterQuery = createQuery();
      registerContainer(filterQuery);
      oplogReadCounter = new AtomicLong(1);
    } catch (Exception ex) {
      log.error("Got exception in OplogReader {} for partition {} while tailing oplog: ",
          consumerId, partition, ex);
      tailOplog();
    }
  }

  private void createCheckPointColl() {
    log.info("OplogReader '{}' checking the checkpoint collection exists for partition {}",
        consumerId, instanceId);
    if (rcxEventsProperties.getConsumesEvents().equals(DELETE))
      checkPointCollectionName = "oplog_checkpoint_" + "delete_"
          + rcxKafkaProperties.getTopicName() + "_" + instanceId;
    else
      checkPointCollectionName =
          "oplog_checkpoint_" + rcxKafkaProperties.getTopicName() + "_" + instanceId;

    Long size = Long.valueOf(1024);
    Long maxDocs = Long.valueOf(5);

    Stream<String> allCheckPointCollectionNames =
        reactiveCheckpointMongoTemplate.getCollectionNames().toStream();
    boolean collectionExists = allCheckPointCollectionNames
        .anyMatch(collectionName -> collectionName.equalsIgnoreCase(checkPointCollectionName));

    if (!collectionExists) {
      CollectionOptions collOptions =
          CollectionOptions.empty().capped().size(size).maxDocuments(maxDocs);
      reactiveCheckpointMongoTemplate.createCollection(checkPointCollectionName, collOptions)
          .block();
      log.info("OplogReader '{}' created the checkpoint collection for partition {}", consumerId,
          instanceId);
    } else {
      log.info("OplogReader '{}' the checkpoint collection already exist for partition {}",
          consumerId, instanceId);
    }
  }

  private String getHostName() {
    StartupLogDocument startupLog = reactiveOplogMongoTemplate
        .findOne(new Query(), StartupLogDocument.class, "startup_log").block();
    if (startupLog != null) {
      return startupLog.getHostname();
    } else {
      return null;
    }
  }

  @Trace(dispatcher = true)
  private void publishMessage(OplogDocument raw) {
    MDC.put(EventPublisherEnums.OPLOG_PARTION.getValue(), partition);
    MDC.put(EventPublisherEnums.CONSUMER_ID.getValue(), consumerId);
    if (raw == null) {
      log.trace("OplogReader '{}' Skipping message {} for partition {}", consumerId, raw,
          instanceId);
      return;
    }

    List<OplogDocument> odLists = filterMessage(raw);
    odLists.forEach(odList -> {
      String _id = null;
      if (odList.getO() != null && odList.getO().get("_id") != null) {
        _id = odList.getO().get("_id").toString();
      } else if (odList.getO2() != null && odList.getO2().get("_id") != null) {
        _id = odList.getO2().get("_id").toString();
      }
      log.trace("OplogReader '{}' publishing {} message for partition {}", consumerId, _id,
          instanceId);
      kafkaPublisher.send(odList, _id);
    });
  }

  @SuppressWarnings("unchecked")
  @Trace
  private List<OplogDocument> filterMessage(OplogDocument raw) {
    Boolean isInstanceMessage = isInstanceMessage(raw);
    List<OplogDocument> odList = new ArrayList<OplogDocument>();
    if (isInstanceMessage) {
      if ("c".equals(raw.getOp()) && raw.getO() != null && raw.getO().get("applyOps") != null) {
        List<Map<String, Object>> li = (List<Map<String, Object>>) raw.getO().get("applyOps");
        if (li != null && !li.isEmpty()) {
          log.trace(
              "OplogReader '{}' transforming the transaction oplog with timestamp {} into individual events for instanceId {}",
              consumerId, new Date(raw.getTs().getTime() * 1000L), instanceId);
          li.forEach(opItem -> {
            if (namespaces.contains(opItem.get("ns"))) {
              OplogDocument od = new OplogDocument();

              od.setOp(String.valueOf(opItem.get("op")));
              od.setNs(String.valueOf(opItem.get("ns")));
              od.setUi(String.valueOf(opItem.get("ui")));
              od.setO((Map<String, Object>) opItem.get("o"));
              od.setO2((Map<String, Object>) opItem.get("o2"));

              od.setTs(raw.getTs());
              od.setT(raw.getT());
              od.setH(raw.getH());
              od.setV(raw.getV());
              od.setWall(raw.getWall());
              if (isInstanceMessage(od)) {
                odList.add(od); // Publish this message
              }
            }
          });
        }
      } else {
        log.trace("OplogReader '{}' is regular oplog event with timestamp {} for instanceId {}",
            consumerId, new Date(raw.getTs().getTime() * 1000L), instanceId);
        odList.add(raw); // Publish this message
      }
    }
    return odList;
  }

  @SuppressWarnings("rawtypes")
  @Trace
  private Boolean isInstanceMessage(OplogDocument raw) {
    if (raw.isFromMigrate()) {
      log.trace(
          "OplogReader '{}' returning without publishing payload on to oplog queue as fromMigrate is true for partition {}",
          consumerId, instanceId);
      return false;
    }

    if ("c".equals(raw.getOp()) && raw.getO() != null) {
      List li = (List) raw.getO().get("applyOps");
      if (li != null && !li.isEmpty())
        return true;
      return false;
    } else if ("c".equals(raw.getOp())) {
      return false;
    }

    if (raw.getNs() != null && raw.getNs().indexOf("oplog_checkpoint_") > -1) {
      log.trace("Checkpoint filtered out namespace= {} for partition in the OplogReader '{}'",
          raw.getNs(), instanceId, consumerId);
      return false;
    }

    String _id = null;
    if (raw != null && raw.getO() != null && raw.getO().get("_id") != null) {
      _id = raw.getO().get("_id").toString();
    } else if (raw != null && raw.getO2() != null && raw.getO2().get("_id") != null) {
      _id = raw.getO2().get("_id").toString();
    }

    boolean isSkipMessage = false;
    if (raw.getNs() != null && rcxEventsProperties.getMessageSkipMap() != null
        && rcxEventsProperties.getMessageSkipMap().size() > 0) {
      for (MessageOperation mOp : rcxEventsProperties.getMessageSkipMap()) {
        if (raw.getNs().contains(mOp.getNs()) && raw.getOp().contains(mOp.getOp())) {
          isSkipMessage = true;
          break;
        }
      }
    }

    if (isSkipMessage) {
      log.debug("Skipping a message {} with ns '{}' and op '{}' for consumer {} on partition {}",
          _id, raw.getNs(), raw.getOp(), consumerId, partition);
      return false;
    }

    if (raw.getNs() != null && raw.getNs().contains("memberpreferences") && raw.getO() != null
        && raw.getO().get("$set") != null) {
      Map<String, Object> updatedFields = (Map<String, Object>) raw.getO().get("$set");
      String[] fields = new String[] {"__v", "events", "updatedAt"};
      if (updatedFields.size() == 3 && updatedFields.keySet().containsAll(Arrays.asList(fields))
          || updatedFields.size() == 2 && updatedFields.keySet()
              .containsAll(Arrays.asList(new String[] {"events", "updatedAt"}))) {
        log.debug(
            "Filtering out a message {} which has only updatedAt field for consumer {} on partition {}",
            _id, consumerId, partition);
        return false;
      }
    }

    if (_id == null) {
      log.error("OplogReader '{}' received a message without _id: {} for partition {}", consumerId,
          raw, instanceId);
    }

    int id = findInstanceId(_id);

    if (instanceId != id) {
      log.trace("OplogReader '{}' listener with id: {} discarding message with key: {}", consumerId,
          instanceId, _id);
      return false;
    }
    log.trace(
        "Message with key: {} should be handled by partition:{} in the OplogReader '{}' which is ME={}",
        _id, id, consumerId, instanceId);
    return true;
  }

  @Trace
  private int findInstanceId(String _id) {
    final Integer partitionCount = rcxEventsProperties.getPartitionCount();
    if (partitionCount != null && _id != null) {
      CRC32 crc = new CRC32();
      crc.update(_id.getBytes());
      return (int) (crc.getValue() % partitionCount);
    }
    instanceNumber++;
    if (instanceNumber >= partitionCount) {
      instanceNumber = 0;
    }
    return instanceNumber;
  }

}
