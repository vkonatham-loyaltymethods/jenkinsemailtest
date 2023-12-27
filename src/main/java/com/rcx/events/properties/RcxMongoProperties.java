package com.rcx.events.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Configuration;
import lombok.Data;

@Configuration
@ConfigurationProperties(prefix = "rcx.mongo")
@Data
@RefreshScope
public class RcxMongoProperties {

  private String trustStoreLocation;
  private String trustStoreType;
  private String trustStorePassword;
  private String keyStoreLocation;
  private String keyStoreType;
  private String keyStorePassword;
  private boolean sslEnabled;

  private String checkPointDBUrl;
  private String oplogDBUrl;

  private String nsFilter;

  private int connectionTimeout;
  private int logFrequencyReads;

}
