package com.rcx.events.helix.factory;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.lang.reflect.Field;
import org.apache.helix.NotificationContext;
import org.apache.helix.ZNRecord;
import org.apache.helix.model.Message;
import org.apache.helix.store.zk.ZkHelixPropertyStore;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import org.springframework.data.mongodb.core.CollectionOptions;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.convert.MongoConverter;
import org.springframework.data.mongodb.core.messaging.DefaultMessageListenerContainer;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.junit4.SpringRunner;
import com.mongodb.reactivestreams.client.MongoDatabase;
import com.rcx.events.helix.statemodel.ConsumerStateModel;
import com.rcx.events.mongo.OplogReader;
import com.rcx.events.mongo.model.OplogDocument;
import com.rcx.events.mongo.model.StartupLogDocument;
import com.rcx.events.properties.RcxEventsProperties;
import com.rcx.events.properties.RcxKafkaProperties;
import com.rcx.events.properties.RcxMongoProperties;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RunWith(SpringRunner.class)
@PrepareForTest(
    value = {ConsumerStateModel.class, DefaultMessageListenerContainer.class, OplogReader.class})
@PowerMockRunnerDelegate(SpringRunner.class)
@PowerMockIgnore("javax.management.*")
public class ConsumerStateModelTest {

  @Rule
  public PowerMockRule rule = new PowerMockRule();
  static {
    PowerMockAgent.initializeIfNeeded();
  }

  @Autowired
  private ApplicationContext applicationContext;

  @Mock
  private ZNRecord znRecord;
  @Mock
  private ZkHelixPropertyStore<ZNRecord> propertyStore;
  @Mock
  private RcxEventsProperties rcxEventsProperties;
  @Mock
  private DefaultMessageListenerContainer defaultMessageListenerContainer;
  @Mock
  private MongoConverter converter;
  @Mock
  private MongoDatabase mongoDatabase;
  @Mock
  private Message message;
  @Mock
  private NotificationContext context;
  @Mock
  private CollectionOptions collOptions;
  @Mock
  private Mono checkPointMono;
  @Mock
  private Mono oplogMono;
  @Mock
  private Flux checkPointFlux;
  @Mock
  private Flux oplogStream;
  @Mock
  private Disposable subscription;

  @MockBean
  private RcxMongoProperties mongoProperties;
  @MockBean
  private RcxEventsProperties rcxProperties;
  @MockBean
  private RcxKafkaProperties kafkaProperties;
  @MockBean
  private AutowireCapableBeanFactory beanFactory;
  @MockBean
  private KafkaTemplate<String, OplogDocument> kafkaTemplate;

  @MockBean
  @Qualifier("reactiveCheckpointMongoTemplate")
  private ReactiveMongoTemplate reactiveCheckpointMongoTemplate;

  @MockBean
  @Qualifier("reactiveOplogMongoTemplate")
  private ReactiveMongoTemplate reactiveOplogMongoTemplate;

  @InjectMocks
  private ConsumerStateModel consumerStateModelSpy =
      new ConsumerStateModel("consumer_1", "partition_1", propertyStore);

  @Before
  public void setUp() throws Exception {
    Field f1 = consumerStateModelSpy.getClass().getDeclaredField("beanFactory");
    f1.setAccessible(true);
    beanFactory = applicationContext.getAutowireCapableBeanFactory();
    f1.set(consumerStateModelSpy, beanFactory);

    when(mongoProperties.getTrustStoreLocation()).thenReturn("/certs/mongo.client.truststore.jks");
    when(mongoProperties.getTrustStoreType()).thenReturn("jks");
    when(mongoProperties.getTrustStorePassword()).thenReturn("rcxdev");
    when(mongoProperties.getKeyStoreLocation()).thenReturn("/certs/mongo.client.keystore.jks");
    when(mongoProperties.getKeyStoreType()).thenReturn("jks");
    when(mongoProperties.getKeyStorePassword()).thenReturn("rcxdev");
    when(mongoProperties.getNsFilter()).thenReturn("coll1,coll2");
    when(kafkaProperties.getTopicName()).thenReturn("test-topic");

    when(rcxProperties.getPartitionCount()).thenReturn(3);
    Mockito.when(rcxProperties.getConsumesEvents()).thenReturn("all");
    when(rcxEventsProperties.getStateModelOnlineWaittime()).thenReturn(0L);

    when(reactiveCheckpointMongoTemplate.getMongoDatabase()).thenReturn(mongoDatabase);
    when(mongoDatabase.getName()).thenReturn("fortest");

    StartupLogDocument sld = new StartupLogDocument();
    sld.setHostname("test host");
    when(mongoDatabase.getName()).thenReturn("fortest");
  }

  private void setupProperties() {
    when(reactiveCheckpointMongoTemplate.getCollectionNames()).thenReturn(checkPointFlux);
    when(reactiveCheckpointMongoTemplate.createCollection(Mockito.anyString(), Mockito.any()))
        .thenReturn(checkPointMono);
    when(checkPointMono.block()).thenReturn(collOptions);
    StartupLogDocument sld = new StartupLogDocument();
    sld.setHostname("test host");
    when(reactiveOplogMongoTemplate.findOne(Mockito.any(Query.class), Mockito.any(),
        Mockito.anyString())).thenReturn(oplogMono);
    when(oplogMono.block()).thenReturn(sld);
    when(reactiveCheckpointMongoTemplate.findOne(Mockito.any(Query.class), Mockito.any(),
        Mockito.any())).thenReturn(checkPointMono);
    when(checkPointMono.block()).thenReturn(null);
    when(reactiveOplogMongoTemplate.tail(Mockito.any(Query.class), Mockito.any()))
        .thenReturn(oplogStream);
  }

  @Test
  public void TestStateChangeOfflineToOnline() throws InterruptedException {
    setupProperties();

    consumerStateModelSpy.onBecomeOnlineFromOffline(message, context);
    Thread.sleep(1000);

    verify(kafkaProperties, times(3)).getTopicName();
    verify(reactiveCheckpointMongoTemplate, times(1)).getCollectionNames();
    verify(reactiveCheckpointMongoTemplate, times(1)).createCollection(Mockito.anyString(),
        Mockito.any(CollectionOptions.class));
    verify(reactiveOplogMongoTemplate, times(1)).findOne(Mockito.any(Query.class), Mockito.any(),
        Mockito.anyString());
    verify(reactiveCheckpointMongoTemplate, times(1)).findOne(Mockito.any(Query.class),
        Mockito.any(), Mockito.anyString());
    verify(reactiveOplogMongoTemplate, times(1)).tail(Mockito.any(Query.class), Mockito.any());
    verify(oplogStream, times(1)).subscribe(Mockito.any(), Mockito.any());
  }

  @Test
  public void TestStateChangeOnlineToOffline() throws InterruptedException {
    setupProperties();
    when(oplogStream.subscribe(Mockito.any(), Mockito.any())).thenReturn(subscription);

    consumerStateModelSpy.onBecomeOnlineFromOffline(message, context);
    Thread.sleep(1000);

    consumerStateModelSpy.onBecomeOfflineFromOnline(message, context);

    verify(subscription, times(1)).dispose();

    OplogReader oplogReader =
        Whitebox.<OplogReader>getInternalState(consumerStateModelSpy, "oplogReader");
    Assert.assertTrue("Should set null for oplogreader instance", oplogReader == null);
  }

  @Test
  public void TestStateChangeOnlineToOfflineNotCloseSubcriptionIfNotStart()
      throws InterruptedException {
    setupProperties();

    consumerStateModelSpy.onBecomeOnlineFromOffline(message, context);
    Thread.sleep(1000);

    consumerStateModelSpy.onBecomeOfflineFromOnline(message, context);

    verify(subscription, times(0)).dispose();

    OplogReader oplogReader =
        Whitebox.<OplogReader>getInternalState(consumerStateModelSpy, "oplogReader");
    Assert.assertTrue("Should set null for oplogreader instance", oplogReader == null);
  }

  @Test
  public void TestResetMethod() throws InterruptedException {
    setupProperties();

    consumerStateModelSpy.onBecomeOnlineFromOffline(message, context);
    Thread.sleep(1000);
    consumerStateModelSpy.reset();

    verify(subscription, times(0)).dispose();
    OplogReader oplogReader =
        Whitebox.<OplogReader>getInternalState(consumerStateModelSpy, "oplogReader");
    Assert.assertTrue("Should set null for oplogreader instance", oplogReader == null);
  }

}
