package com.rcx.events.kafka;

import static org.mockito.Mockito.doAnswer;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.bson.BsonTimestamp;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.util.concurrent.ListenableFutureCallback;
import com.rcx.events.mongo.model.CheckPointDocument;
import com.rcx.events.mongo.model.OplogDocument;
import com.rcx.events.properties.RcxEventsProperties;
import com.rcx.events.properties.RcxKafkaProperties;
import reactor.core.publisher.Mono;

@RunWith(SpringRunner.class)
public class KafkaPublisherTest {

  @Mock
  private KafkaTemplate<String, OplogDocument> kafkaTemplate;

  @Mock
  private RcxKafkaProperties kafkaProperties;

  @Mock
  private RcxEventsProperties eventProperties;

  @Mock
  @Qualifier("reactiveCheckpointMongoTemplate")
  private ReactiveMongoTemplate reactiveCheckpointMongoTemplate;

  @Mock
  Mono<CheckPointDocument> checkPointMono;

  @Mock
  private ListenableFutureCallback<SendResult<String, OplogDocument>> listenableFutureCallback;

  @Mock
  private RecordMetadata recordMetadata;

  @Mock
  private SendResult<String, OplogDocument> sendResult;

  @Mock
  private ListenableFuture<SendResult<String, OplogDocument>> future;

  @InjectMocks
  private KafkaPublisher kafkaPublisherSpy =
      new KafkaPublisher("default", "test_check_point_1", "1", "oplog_1");

  private String _id = "5c46e2367b2bfe0102059ff4";

  private OplogDocument getOplogDocument() {
    OplogDocument odoc = new OplogDocument();
    odoc.setOp("i");
    BsonTimestamp bts = new BsonTimestamp((int) (new Date().getTime() / 1000), 0);
    odoc.setTs(bts);

    Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("_id", new ObjectId(_id));
    map.put("name", "test user");
    odoc.setO(map);

    return odoc;
  }

  @Before
  public void setUp() {

    Mockito.when(kafkaProperties.getTopicName()).thenReturn("test_topic_name");
    Mockito.when(kafkaProperties.getLogFrequencyWrites()).thenReturn(1);
    Mockito.when(kafkaProperties.getCheckPointFreq()).thenReturn(1);
    Mockito.when(kafkaProperties.getMaxProducerRetries()).thenReturn(0);
    Mockito.when(kafkaTemplate.send(Mockito.anyString(), Mockito.anyString(),
        Mockito.any(OplogDocument.class))).thenReturn(future);

    ProducerRecord<String, OplogDocument> prodRecrd =
        new ProducerRecord<String, OplogDocument>(_id, getOplogDocument());

    Mockito.when(sendResult.getProducerRecord()).thenReturn(prodRecrd);

    Mockito.when(sendResult.getRecordMetadata()).thenReturn(recordMetadata);

    Mockito.when(reactiveCheckpointMongoTemplate.save(Mockito.any(CheckPointDocument.class),
        Mockito.anyString())).thenReturn(checkPointMono);
    Mockito.when(checkPointMono.block()).thenReturn(null);

  }

  @Test
  public void checkSendOnSuccess() {
    doAnswer(invocationOnMock -> {
      ListenableFutureCallback listenableFutureCallback = invocationOnMock.getArgument(0);
      listenableFutureCallback.onSuccess(sendResult);
      return null;
    }).when(future).addCallback(Mockito.any(ListenableFutureCallback.class));

    kafkaPublisherSpy.send(getOplogDocument(), _id);

    Mockito.verify(sendResult, Mockito.times(1)).getProducerRecord();
    Mockito.verify(future, Mockito.times(1))
        .addCallback(Mockito.any(ListenableFutureCallback.class));
    Mockito.verify(kafkaTemplate, Mockito.times(1)).send(Mockito.anyString(), Mockito.anyString(),
        Mockito.any(OplogDocument.class));
    Mockito.verify(reactiveCheckpointMongoTemplate, Mockito.times(1))
        .save(Mockito.any(CheckPointDocument.class), Mockito.anyString());
  }

  @Test
  public void notFailedWhileSavingCheckPoint() {
    Mockito.doThrow(new RuntimeException("custom throw exception")).when(checkPointMono).block();
    doAnswer(invocationOnMock -> {
      ListenableFutureCallback listenableFutureCallback = invocationOnMock.getArgument(0);
      listenableFutureCallback.onSuccess(sendResult);
      return null;
    }).when(future).addCallback(Mockito.any(ListenableFutureCallback.class));

    kafkaPublisherSpy.send(getOplogDocument(), _id);

    Mockito.verify(sendResult, Mockito.times(1)).getProducerRecord();
    Mockito.verify(future, Mockito.times(1))
        .addCallback(Mockito.any(ListenableFutureCallback.class));
    Mockito.verify(kafkaTemplate, Mockito.times(1)).send(Mockito.anyString(), Mockito.anyString(),
        Mockito.any(OplogDocument.class));
    Mockito.verify(reactiveCheckpointMongoTemplate, Mockito.times(1))
        .save(Mockito.any(CheckPointDocument.class), Mockito.anyString());
  }

  @Test
  public void checkSendOnFailure() {
    doAnswer(invocationOnMock -> {
      ListenableFutureCallback listenableFutureCallback = invocationOnMock.getArgument(0);
      listenableFutureCallback
          .onFailure(new RuntimeException("test custom exception checkSendOnFailure"));
      return null;
    }).when(future).addCallback(Mockito.any(ListenableFutureCallback.class));

    kafkaPublisherSpy.send(getOplogDocument(), _id);

    Mockito.verify(sendResult, Mockito.times(0)).getProducerRecord();
    Mockito.verify(future, Mockito.times(1))
        .addCallback(Mockito.any(ListenableFutureCallback.class));
    Mockito.verify(kafkaTemplate, Mockito.times(1)).send(Mockito.anyString(), Mockito.anyString(),
        Mockito.any(OplogDocument.class));
    Mockito.verify(reactiveCheckpointMongoTemplate, Mockito.times(0))
        .save(Mockito.any(Document.class), Mockito.anyString());
  }

  @Test
  public void checkRetryForSendOnFailure() {
    Mockito.when(kafkaProperties.getMaxProducerRetries()).thenReturn(1);

    doAnswer(invocationOnMock -> {
      ListenableFutureCallback listenableFutureCallback = invocationOnMock.getArgument(0);
      listenableFutureCallback
          .onFailure(new RuntimeException("test custom exception checkRetryForSendOnFailure"));
      return null;
    }).when(future).addCallback(Mockito.any(ListenableFutureCallback.class));

    kafkaPublisherSpy.send(getOplogDocument(), _id);

    Mockito.verify(sendResult, Mockito.times(0)).getProducerRecord();
    Mockito.verify(future, Mockito.times(2))
        .addCallback(Mockito.any(ListenableFutureCallback.class));
    Mockito.verify(kafkaTemplate, Mockito.times(2)).send(Mockito.anyString(), Mockito.anyString(),
        Mockito.any(OplogDocument.class));
    Mockito.verify(reactiveCheckpointMongoTemplate, Mockito.times(0))
        .save(Mockito.any(Document.class), Mockito.anyString());
  }

}
