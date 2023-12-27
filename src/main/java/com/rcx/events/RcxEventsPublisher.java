/**
 * 
 */
package com.rcx.events;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.cloud.config.server.EnableConfigServer;

/**
 * @author karthik
 *
 */

@SpringBootApplication
@EnableConfigServer
@EnableAutoConfiguration(exclude = {MongoAutoConfiguration.class, MongoDataAutoConfiguration.class})
public class RcxEventsPublisher {

  public static void main(String[] args) {
    SpringApplication.run(RcxEventsPublisher.class, args);
  }
}
