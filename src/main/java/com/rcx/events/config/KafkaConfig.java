package com.rcx.events.config;

import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import com.rcx.events.kafka.KafkaProducerPartitioner;
import com.rcx.events.kafka.serializer.OplogDocumentSerializer;
import com.rcx.events.mongo.model.OplogDocument;
import com.rcx.events.properties.RcxKafkaProperties;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class KafkaConfig {

  @Autowired
  private RcxKafkaProperties kafkaProperties;

  private @NonNull final KafkaProperties springKafkaProperties;

  @Bean
  public Map<String, Object> producerConfigs() {
    Map<String, Object> props = new HashMap<>();

    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.getBootstrapServers());

    props.putAll(springKafkaProperties.getSsl().buildProperties());
    props.putAll(springKafkaProperties.getProperties());

    props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, OplogDocumentSerializer.class);

    props.put(ProducerConfig.PARTITIONER_CLASS_CONFIG, KafkaProducerPartitioner.class);

    props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy");
    props.put(ProducerConfig.ACKS_CONFIG, "all");

    // Only retry after 2 seconds.
    props.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, "1000");

    props.put(ProducerConfig.MAX_REQUEST_SIZE_CONFIG, 16 * 1024 * 1024);

    props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 1);
    props.put(ProducerConfig.RETRIES_CONFIG, 100);

    return props;
  }

  @Bean
  public ProducerFactory<String, OplogDocument> producerFactory() {
    return new DefaultKafkaProducerFactory<>(producerConfigs());
  }

  @Bean
  public KafkaTemplate<String, OplogDocument> kafkaTemplate() {
    return new KafkaTemplate<>(producerFactory());
  }

}
