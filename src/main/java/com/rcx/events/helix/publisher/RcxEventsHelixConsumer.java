package com.rcx.events.helix.publisher;

import java.util.List;
import javax.annotation.PreDestroy;
import org.apache.helix.HelixManager;
import org.apache.helix.HelixManagerFactory;
import org.apache.helix.InstanceType;
import org.apache.helix.ZNRecord;
import org.apache.helix.manager.zk.ZKHelixAdmin;
import org.apache.helix.manager.zk.ZNRecordSerializer;
import org.apache.helix.manager.zk.ZkClient;
import org.apache.helix.model.InstanceConfig;
import org.apache.helix.participant.StateMachineEngine;
import org.apache.helix.store.zk.ZkHelixPropertyStore;
import org.junit.Assert;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import com.rcx.events.helix.factory.ConsumerStateModelFactory;
import com.rcx.events.mongo.RcxMongoUtils;
import com.rcx.events.properties.RcxEventsProperties;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Order(3)
@Component
@Log4j2
@RequiredArgsConstructor
public class RcxEventsHelixConsumer implements CommandLineRunner {

  private @NonNull RcxEventsProperties rcxEventsProperties;

  @Autowired
  private AutowireCapableBeanFactory beanFactory;

  @Autowired
  private RcxMongoUtils rcxMongoUtils;

  private HelixManager manager = null;
  private ZkClient zkclient = null;

  @PreDestroy
  public void preDestroyRcxEventsHelixConsumer() {
    rcxMongoUtils.updateCheckPointCollns();
  }

  public void connect(String zkAddr, String clusterName, String consumerId) {
    Assert.assertNotNull("zookeeperAddress cannot be null",
        rcxEventsProperties.getZookeeperAddress());
    Assert.assertNotNull("helixClusterName cannot be null",
        rcxEventsProperties.getHelixClusterName());

    try {
      manager = HelixManagerFactory.getZKHelixManager(clusterName, consumerId,
          InstanceType.PARTICIPANT, zkAddr);
      manager.connect();
      StateMachineEngine stateMach = manager.getStateMachineEngine();
      ZkHelixPropertyStore<ZNRecord> store = manager.getHelixPropertyStore();
      ConsumerStateModelFactory modelFactory = new ConsumerStateModelFactory(consumerId, store);
      beanFactory.autowireBean(modelFactory);
      stateMach.registerStateModelFactory(rcxEventsProperties.getStateModel(), modelFactory);
    } catch (Exception e) {
      log.error("Exception on connecting consumer {} to helix manager {} {}", consumerId,
          clusterName, e);
    }
  }

  public void disconnect() {
    if (manager != null) {
      log.warn("Disconnecting the helix manager");
      manager.disconnect();
    }
    if (zkclient != null) {
      log.warn("Closing the zk client connection");
      zkclient.close();
    }
  }

  public void startConsumer() {

    final String hostName = System.getenv("HOSTNAME");

    final String clusterName = rcxEventsProperties.getHelixClusterName();
    final String zkAddr = rcxEventsProperties.getZookeeperAddress();
    final String consumerId = "consumer." + hostName;

    log.info("Starting the HelixConsumer {} for cluster {} ", consumerId, clusterName);
    // add node to cluster if not already added
    zkclient = new ZkClient(zkAddr, ZkClient.DEFAULT_SESSION_TIMEOUT,
        ZkClient.DEFAULT_CONNECTION_TIMEOUT, new ZNRecordSerializer());
    ZKHelixAdmin admin = new ZKHelixAdmin(zkclient);

    List<String> nodes = admin.getInstancesInCluster(clusterName);
    log.info("Existing Instances in the cluster {} are {}", clusterName, nodes.toString());
    if (!nodes.contains(consumerId)) {
      InstanceConfig config = new InstanceConfig(consumerId);
      config.setInstanceEnabled(true);
      admin.addInstance(clusterName, config);
    }
    connect(zkAddr, clusterName, consumerId);
  }

  @Override
  public void run(String... args) throws Exception {
    startConsumer();
  }
}
