package com.rcx.events.helix.cluster;

import java.util.List;
import org.apache.helix.HelixManager;
import org.apache.helix.HelixManagerFactory;
import org.apache.helix.InstanceType;
import org.apache.helix.manager.zk.ZKHelixAdmin;
import org.apache.helix.manager.zk.ZNRecordSerializer;
import org.apache.helix.manager.zk.ZkClient;
import org.apache.helix.model.InstanceConfig;
import org.junit.Assert;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import com.rcx.events.properties.RcxEventsProperties;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Order(2)
@Component("clusterManager")
@Log4j2
@RequiredArgsConstructor
public class RcxEventsClusterManager implements CommandLineRunner {

  private @NonNull RcxEventsProperties rcxEventsProperties;

  public void startConsumerCluster() {
    Assert.assertNotNull("zookeeperAddress cannot be null",
        rcxEventsProperties.getZookeeperAddress());
    Assert.assertNotNull("helixClusterName cannot be null",
        rcxEventsProperties.getHelixClusterName());
    Assert.assertNotNull("partitionCount must be greater than 0",
        rcxEventsProperties.getPartitionCount());

    HelixManager manager = null;
    ZkClient zkclient = null;

    final String clusterName = rcxEventsProperties.getHelixClusterName();
    final String zkAddr = rcxEventsProperties.getZookeeperAddress();

    String hostName = System.getenv("HOSTNAME");
    final String controllerName = "controller." + hostName;
    log.info("Starting the RCXEventsClusterManager {} for cluster {} with Partitions {}",
        controllerName, clusterName, rcxEventsProperties.getPartitionCount());

    try {
      // add node to cluster if not already added
      zkclient = new ZkClient(zkAddr, ZkClient.DEFAULT_SESSION_TIMEOUT,
          ZkClient.DEFAULT_CONNECTION_TIMEOUT, new ZNRecordSerializer());
      ZKHelixAdmin admin = new ZKHelixAdmin(zkclient);

      List<String> nodes = admin.getInstancesInCluster(clusterName);
      log.info("RCX Events Cluster Manager ::  Existing Instances in the cluster {} are {}",
          clusterName, nodes.toString());
      if (!nodes.contains(controllerName)) {
        InstanceConfig config = new InstanceConfig(controllerName);
        config.setInstanceEnabled(true);
        log.info("Adding Contoller Instance {} to the cluster {} ", controllerName, clusterName);
        admin.addInstance(clusterName, config);
      }

      manager = HelixManagerFactory.getZKHelixManager(clusterName, controllerName,
          InstanceType.CONTROLLER, zkAddr);

      manager.connect();

    } catch (Exception e) {
      log.error("HelixController {} failed to connect to cluster {} with Partitions {}",
          controllerName, clusterName, rcxEventsProperties.getPartitionCount(), e);
      if (manager != null) {
        manager.disconnect();
      }
      if (zkclient != null) {
        zkclient.close();
      }
    }
  }

  @Override
  public void run(String... args) throws Exception {
    startConsumerCluster();
  }

}
