package com.rcx.events.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Configuration;
import lombok.Data;

@Configuration
@ConfigurationProperties(prefix = "rcx.kafka")
@Data
@RefreshScope
public class RcxKafkaProperties {

  private String bootstrapServers;
  private String topicName;
  private int checkPointFreq = 1;
  private int maxProducerRetries;
  private int logFrequencyWrites = 1;

}
