package com.rcx.events.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Configuration;
import lombok.Data;

@Configuration
@ConfigurationProperties(prefix = "spring.security.user")
@Data
@RefreshScope
public class SpringSecurityProperties {

  private String name;
  private String password;
  private String roles;

}
