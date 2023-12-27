/**
 * 
 */
package com.rcx.events.properties;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Configuration;
import lombok.Data;

/**
 * @author karthik
 *
 */
@Configuration
@ConfigurationProperties(prefix = "rcx.events.publisher")
@Data
@RefreshScope
public class RcxEventsProperties {

  private String zookeeperAddress;
  private String helixClusterName;
  private Integer partitionCount;
  private String stateModel;
  private String resourceName;
  private long stateModelOnlineWaittime;
  private int numPartitionsPerInstance;

  private List<MessageOperation> messageSkipMap;
  private List<MessageOperation> messageRedirectMap;
  private String consumesEvents = "all";

}
