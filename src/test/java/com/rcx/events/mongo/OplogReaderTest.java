package com.rcx.events.mongo;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bson.BsonTimestamp;
import org.bson.types.ObjectId;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.agent.PowerMockAgent;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;
import org.powermock.modules.junit4.rule.PowerMockRule;
import org.powermock.reflect.Whitebox;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.data.mongodb.core.CollectionOptions;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.convert.MongoConverter;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.context.junit4.SpringRunner;
import com.mongodb.reactivestreams.client.MongoDatabase;
import com.rcx.events.kafka.KafkaPublisher;
import com.rcx.events.mongo.model.CheckPointDocument;
import com.rcx.events.mongo.model.OplogDocument;
import com.rcx.events.mongo.model.StartupLogDocument;
import com.rcx.events.properties.RcxEventsProperties;
import com.rcx.events.properties.RcxKafkaProperties;
import com.rcx.events.properties.RcxMongoProperties;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RunWith(SpringRunner.class)
@PrepareForTest(value = OplogReader.class)
@PowerMockRunnerDelegate(SpringRunner.class)
@PowerMockIgnore("javax.management.*")
public class OplogReaderTest {

  @Rule
  public PowerMockRule rule = new PowerMockRule();
  static {
    PowerMockAgent.initializeIfNeeded();
  }

  @Mock
  AutowireCapableBeanFactory beanFactory;

  @Mock
  KafkaPublisher kafkaPublisher;

  @Mock
  private RcxMongoProperties mongoProperties;

  @Mock
  private RcxKafkaProperties rcxKafkaProperties;

  @Mock
  private RcxEventsProperties rcxProperties;

  @Mock
  MongoDatabase mongoDatabase;

  @Mock
  MongoConverter converter;

  @Mock
  CollectionOptions collOptions;

  @Mock
  Mono oplogMono;

  @Mock
  Mono checkPointMono;

  @Mock
  Flux checkPointFlux;

  @Mock
  @Qualifier("reactiveCheckpointMongoTemplate")
  private ReactiveMongoTemplate reactiveCheckpointMongoTemplate;

  @Mock
  @Qualifier("reactiveOplogMongoTemplate")
  private ReactiveMongoTemplate reactiveOplogMongoTemplate;

  @InjectMocks
  private OplogReader oplogReaderSpy = new OplogReader("1", "oplog_1");

  @Before
  public void setup() throws Exception {
    when(mongoProperties.getTrustStoreLocation()).thenReturn("/certs/mongo.client.truststore.jks");
    when(mongoProperties.getTrustStoreType()).thenReturn("jks");
    when(mongoProperties.getTrustStorePassword()).thenReturn("rcxdev");
    when(mongoProperties.getKeyStoreLocation()).thenReturn("/certs/mongo.client.keystore.jks");
    when(mongoProperties.getKeyStoreType()).thenReturn("jks");
    when(mongoProperties.getKeyStorePassword()).thenReturn("rcxdev");
    Mockito.when(mongoProperties.getNsFilter()).thenReturn("fortest.coll1,fortest.coll2");
    Mockito.when(rcxKafkaProperties.getTopicName()).thenReturn("test-topic");

    Mockito.when(rcxProperties.getPartitionCount()).thenReturn(3);
    Mockito.when(rcxProperties.getConsumesEvents()).thenReturn("all");

    Mockito.when(reactiveOplogMongoTemplate.getConverter()).thenReturn(converter);

    StartupLogDocument sld = new StartupLogDocument();
    sld.setHostname("test host");

    Mockito.when(reactiveOplogMongoTemplate.findOne(Mockito.any(Query.class), Mockito.any(),
        Mockito.anyString())).thenReturn(oplogMono);
    Mockito.when(oplogMono.block()).thenReturn(sld);

    Mockito.when(reactiveCheckpointMongoTemplate.findOne(Mockito.any(Query.class), Mockito.any(),
        Mockito.any())).thenReturn(checkPointMono);
    Mockito.when(checkPointMono.block()).thenReturn(null);

  }

  @Test
  public void getCurrentTimestamp() throws Exception {
    Mockito.when(rcxKafkaProperties.getTopicName()).thenReturn("test-topic-timestamp-0");
    Mockito.when(reactiveCheckpointMongoTemplate.getCollectionNames()).thenReturn(checkPointFlux);
    Mockito
        .when(reactiveCheckpointMongoTemplate.createCollection(Mockito.anyString(), Mockito.any()))
        .thenReturn(checkPointMono);
    Mockito.when(checkPointMono.block()).thenReturn(collOptions);
    Whitebox.invokeMethod(oplogReaderSpy, "createCheckPointColl");

    Mockito.when(checkPointMono.block()).thenReturn(null);

    BsonTimestamp bts = new BsonTimestamp((int) (new Date().getTime() / 1000), 0);
    BsonTimestamp result = Whitebox.invokeMethod(oplogReaderSpy, "getCheckPointTimeStamp");
    Assert.assertTrue("Date should be greater than current date",
        bts.getTime() <= result.getTime());
  }

  @Test
  public void getCheckPointTimestamp() throws Exception {
    CheckPointDocument cpDoc = new CheckPointDocument();
    cpDoc.setHost("test host");

    SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
    Date date = format.parse("2019-07-22");
    BsonTimestamp customBsonTS = new BsonTimestamp((int) (date.getTime() / 1000), 0);

    cpDoc.setTs(customBsonTS);

    Mockito.when(rcxKafkaProperties.getTopicName()).thenReturn("test-topic-timestamp-1");
    Mockito.when(reactiveCheckpointMongoTemplate.getCollectionNames()).thenReturn(checkPointFlux);
    Mockito
        .when(reactiveCheckpointMongoTemplate.createCollection(Mockito.anyString(), Mockito.any()))
        .thenReturn(checkPointMono);
    Mockito.when(checkPointMono.block()).thenReturn(collOptions);
    Whitebox.invokeMethod(oplogReaderSpy, "createCheckPointColl");

    Mockito.when(checkPointMono.block()).thenReturn(cpDoc);
    Mockito.when(reactiveCheckpointMongoTemplate.findOne(Mockito.any(Query.class), Mockito.any(),
        Mockito.any())).thenReturn(checkPointMono);
    BsonTimestamp result = Whitebox.invokeMethod(oplogReaderSpy, "getCheckPointTimeStamp");

    Assert.assertTrue("Date should equal checkpoint timestamp",
        customBsonTS.getTime() == result.getTime());
  }

  @Test
  public void getQuery() throws Exception {
    Mockito.when(rcxKafkaProperties.getTopicName()).thenReturn("test-topic-timestamp-1");
    Mockito.when(reactiveCheckpointMongoTemplate.getCollectionNames()).thenReturn(checkPointFlux);
    Mockito
        .when(reactiveCheckpointMongoTemplate.createCollection(Mockito.anyString(), Mockito.any()))
        .thenReturn(checkPointMono);
    Mockito.when(checkPointMono.block()).thenReturn(collOptions);
    Whitebox.invokeMethod(oplogReaderSpy, "createCheckPointColl");

    CheckPointDocument cpDoc = new CheckPointDocument();
    cpDoc.setHost("test host");

    SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
    Date date = format.parse("2019-07-22");
    BsonTimestamp customBsonTS = new BsonTimestamp((int) (date.getTime() / 1000), 0);

    cpDoc.setTs(customBsonTS);
    Mockito.when(reactiveCheckpointMongoTemplate.findOne(Mockito.any(Query.class), Mockito.any(),
        Mockito.any())).thenReturn(checkPointMono);
    Mockito.when(checkPointMono.block()).thenReturn(cpDoc);

    List<String> listOfColls = new ArrayList<String>();
    listOfColls.add("admin.$cmd");

    Criteria or1 = new Criteria().andOperator(Criteria.where("op").is("u").orOperator(
        Criteria.where("o.$set.events.0").exists(true), Criteria.where("o.events.0").exists(true)));
    Criteria or2 =
        new Criteria().andOperator(Criteria.where("op").is("i").and("o.events.0").exists(true));
    Criteria or3 = Criteria.where("op").is("d");
    Criteria or4 =
        new Criteria().andOperator(Criteria.where("op").is("c").and("o.applyOps").exists(true));

    Query query = new Query();
    query.addCriteria(Criteria.where("ts").gt(customBsonTS).and("ns").in(listOfColls)
        .orOperator(or1, or2, or3, or4));
    Query result = Whitebox.invokeMethod(oplogReaderSpy, "createQuery");

    Assert.assertTrue("Oplog filter is not expected", query.equals(result));
  }

  @Test
  public void checkHostName() throws Exception {
    Mockito.when(oplogMono.block()).thenReturn(null);
    String result = Whitebox.invokeMethod(oplogReaderSpy, "getHostName");
    Assert.assertTrue("Host name should be null", result == null);

    String hostName = "test-host-name";

    StartupLogDocument stLogDocument = new StartupLogDocument();
    stLogDocument.setHostname(hostName);

    Mockito.when(reactiveOplogMongoTemplate.findOne(Mockito.any(Query.class), Mockito.any(),
        Mockito.anyString())).thenReturn(oplogMono);
    Mockito.when(oplogMono.block()).thenReturn(stLogDocument);
    result = Whitebox.invokeMethod(oplogReaderSpy, "getHostName");

    Assert.assertTrue("Host name should not null", result == hostName);
  }

  @Test
  public void checkInstanceId() throws Exception {
    int id = Whitebox.invokeMethod(oplogReaderSpy, "findInstanceId", null);
    Assert.assertTrue("Instance Id should be 1", id == 1);
    id = Whitebox.invokeMethod(oplogReaderSpy, "findInstanceId", null);
    Assert.assertTrue("Instance Id should be 2", id == 2);
    id = Whitebox.invokeMethod(oplogReaderSpy, "findInstanceId", null);
    Assert.assertTrue("Instance Id should be 0", id == 0);
    id = Whitebox.invokeMethod(oplogReaderSpy, "findInstanceId", null);
    Assert.assertTrue("Instance Id should be 1", id == 1);

    String _id = "5c58d8580bf24c075fb589bd";
    id = Whitebox.invokeMethod(oplogReaderSpy, "findInstanceId", _id);
    Assert.assertTrue("Instance Id should be 0 for _id " + _id, id == 0);
    id = Whitebox.invokeMethod(oplogReaderSpy, "findInstanceId", _id);
    Assert.assertTrue("Instance Id should be 0 for _id " + _id, id == 0);
    id = Whitebox.invokeMethod(oplogReaderSpy, "findInstanceId", _id);
    Assert.assertTrue("Instance Id should be 0 for _id " + _id, id == 0);
  }

  @Test
  public void checkIsInstanceMessage() throws Exception {
    OplogDocument odoc = new OplogDocument();
    odoc.setFromMigrate(true);
    boolean view = Whitebox.invokeMethod(oplogReaderSpy, "isInstanceMessage", odoc);
    Assert.assertTrue("Should return false if set fromMigrate option as true", view == false);

    odoc = new OplogDocument();
    odoc.setOp("c");
    view = Whitebox.invokeMethod(oplogReaderSpy, "isInstanceMessage", odoc);
    Assert.assertTrue("Should return false if op=c and applyOps is not exist", view == false);

    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("applyOps", new ArrayList<Object>());

    odoc.setO(map);
    view = Whitebox.invokeMethod(oplogReaderSpy, "isInstanceMessage", odoc);
    Assert.assertTrue("Should return false if op=c and applyOps has empty list", view == false);

    odoc.setOp("i");
    odoc.setNs("oplog_checkpoint_test_collection");
    view = Whitebox.invokeMethod(oplogReaderSpy, "isInstanceMessage", odoc);
    Assert.assertTrue("Should return false if ns contains oplog_checkpoint_ name", view == false);

    odoc.setNs("members");
    map = new LinkedHashMap<String, Object>();
    map.put("_id", new ObjectId("5c58d8580bf24c075fb589bd"));

    odoc.setO(map);
    view = Whitebox.invokeMethod(oplogReaderSpy, "isInstanceMessage", odoc);
    Assert.assertTrue("Should return false if instance id not matched", view == false);

    map = new LinkedHashMap<String, Object>();
    map.put("_id", new ObjectId("5c46e2367b2bfe0102059ff4"));

    odoc.setOp("u");
    odoc.setO(null);
    odoc.setO2(map);
    odoc.setNs("fortest.coll1");
    view = Whitebox.invokeMethod(oplogReaderSpy, "isInstanceMessage", odoc);
    Assert.assertTrue("Should return true if instance id is matched", view == true);

    odoc.setOp("c");
    Map<String, Object> oMap = new LinkedHashMap<String, Object>();
    List<Object> li = new ArrayList<Object>();
    li.add(oMap);
    map.put("applyOps", li);
    map.put("applyOps", li);
    odoc.setO(map);
    view = Whitebox.invokeMethod(oplogReaderSpy, "isInstanceMessage", odoc);
    Assert.assertTrue("Should return true if op=c and applyOps has list", view == true);
  }

  @Test
  public void checkFilterMessage() throws Exception {

    Whitebox.invokeMethod(oplogReaderSpy, "setNameSpaces");

    OplogDocument odoc = new OplogDocument();
    odoc.setOp("i");
    odoc.setNs("fortest.coll1");
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("_id", new ObjectId("5c46e2367b2bfe0102059ff4"));
    odoc.setO(map);
    odoc.setTs(new BsonTimestamp((int) (new Date().getTime() / 1000), 0));

    List<OplogDocument> li = Whitebox.invokeMethod(oplogReaderSpy, "filterMessage", odoc);
    Assert.assertTrue("Should return list with one instance", li.size() == 1);

    odoc.setOp("c");
    map.put("applyOps", null);
    odoc.setO(map);

    li = Whitebox.invokeMethod(oplogReaderSpy, "filterMessage", odoc);
    Assert.assertTrue("Should return empty list", li.size() == 0);

    List<Object> list = new ArrayList<Object>();
    Map<String, Object> applyOpsMap = new LinkedHashMap<String, Object>();
    applyOpsMap.put("ns", "members");
    list.add(applyOpsMap);
    map.put("applyOps", list);
    odoc.setO(map);

    li = Whitebox.invokeMethod(oplogReaderSpy, "filterMessage", odoc);
    Assert.assertTrue("Should return empty list if applyOps entry ns not matched", li.size() == 0);

    applyOpsMap = new LinkedHashMap<String, Object>();
    applyOpsMap.put("ns", "fortest.coll1");
    applyOpsMap.put("fromMigrate", false);
    applyOpsMap.put("op", "d");
    Map<String, Object> oMap = new LinkedHashMap<String, Object>();
    oMap.put("_id", new ObjectId("5c46e2367b2bfe0102059ff4"));
    applyOpsMap.put("o", oMap);
    list.add(applyOpsMap);
    map.put("applyOps", list);
    odoc.setO(map);
    li = Whitebox.invokeMethod(oplogReaderSpy, "filterMessage", odoc);
    Assert.assertTrue("Should return list with one instace if applyOps entry ns matched",
        li.size() == 1);
  }

  @Test
  public void whenAddCalledVerified() throws Exception {
    OplogReader myList = Mockito.mock(OplogReader.class);

    Mockito.doNothing().when(myList).bindConsumerToOplogReader();
    myList.bindConsumerToOplogReader();
    verify(myList, times(1)).bindConsumerToOplogReader();
  }

  @Test
  public void checkNameSpaces() throws Exception {
    Whitebox.invokeMethod(oplogReaderSpy, "setNameSpaces");

    Field field = Whitebox.getField(OplogReader.class, "namespaces");
    @SuppressWarnings("unchecked")
    List<String> namespaces = (List<String>) field.get(oplogReaderSpy);

    Assert.assertTrue("Should add two namespaces", namespaces.size() == 2);
  }

  @Test
  public void checkCreatingCheckPointColl() throws Exception {
    Mockito.when(reactiveCheckpointMongoTemplate.getCollectionNames()).thenReturn(checkPointFlux);
    Mockito
        .when(reactiveCheckpointMongoTemplate.createCollection(Mockito.anyString(), Mockito.any()))
        .thenReturn(checkPointMono);
    Mockito.when(checkPointMono.block()).thenReturn(collOptions);

    Whitebox.invokeMethod(oplogReaderSpy, "createCheckPointColl");
    verify(reactiveCheckpointMongoTemplate, times(1)).createCollection(Mockito.anyString(),
        Mockito.any(CollectionOptions.class));

    Field field = Whitebox.getField(OplogReader.class, "checkPointCollectionName");
    List<String> collNames = new ArrayList<String>();
    collNames.add((String) field.get(oplogReaderSpy));

    Mockito.when(checkPointFlux.toStream()).thenReturn(collNames.stream());

    Whitebox.invokeMethod(oplogReaderSpy, "createCheckPointColl");
    verify(reactiveCheckpointMongoTemplate, times(1)).createCollection(Mockito.anyString(),
        Mockito.any(CollectionOptions.class));
  }

  @Test
  public void checkPublishMessage() throws Exception {

    OplogDocument odoc = null;

    Whitebox.invokeMethod(oplogReaderSpy, "publishMessage", odoc);

    verify(kafkaPublisher, times(0)).send(null, "5c46e2367b2bfe0102059ff4");

    odoc = new OplogDocument();
    odoc.setOp("i");
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("_id", new ObjectId("5c46e2367b2bfe0102059ff4"));
    odoc.setO(map);
    odoc.setTs(new BsonTimestamp((int) (new Date().getTime() / 1000), 0));
    Whitebox.invokeMethod(oplogReaderSpy, "publishMessage", odoc);

    verify(kafkaPublisher, times(1)).send(odoc, "5c46e2367b2bfe0102059ff4");
  }

}
