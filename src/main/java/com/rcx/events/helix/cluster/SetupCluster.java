package com.rcx.events.helix.cluster;

import java.util.List;
import org.I0Itec.zkclient.exception.ZkException;
import org.apache.helix.manager.zk.ZKHelixAdmin;
import org.apache.helix.manager.zk.ZNRecordSerializer;
import org.apache.helix.manager.zk.ZkClient;
import org.apache.helix.model.IdealState.RebalanceMode;
import org.apache.helix.model.OnlineOfflineSMD;
import org.junit.Assert;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import com.rcx.events.properties.RcxEventsProperties;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;


@Order(1)
@Component
@Log4j2
@RequiredArgsConstructor
public class SetupCluster implements CommandLineRunner {

  private @NonNull RcxEventsProperties rcxEventsProperties;

  public void setupConsumerCluster() {
    Assert.assertNotNull("zookeeperAddress cannot be null",
        rcxEventsProperties.getZookeeperAddress());
    Assert.assertNotNull("helixClusterName cannot be null",
        rcxEventsProperties.getHelixClusterName());
    Assert.assertNotNull("partitionCount must be greater than 0",
        rcxEventsProperties.getPartitionCount());

    log.info("Starting to setup the RCX events cluster {}",
        rcxEventsProperties.getHelixClusterName());

    final String zkAddr = rcxEventsProperties.getZookeeperAddress();
    final String clusterName = rcxEventsProperties.getHelixClusterName();

    ZkClient zkclient = null;

    try {
      zkclient = new ZkClient(zkAddr, ZkClient.DEFAULT_SESSION_TIMEOUT,
          ZkClient.DEFAULT_CONNECTION_TIMEOUT, new ZNRecordSerializer());

      ZKHelixAdmin admin = new ZKHelixAdmin(zkclient);

      // Cluster will not be add if it already exists with the same name
      admin.addCluster(clusterName, false);

      if (!admin.getStateModelDefs(clusterName).contains(rcxEventsProperties.getStateModel())) {
        admin.addStateModelDef(clusterName, rcxEventsProperties.getStateModel(),
            OnlineOfflineSMD.build());
      }

      // add resource
      String resourceName = rcxEventsProperties.getResourceName();
      List<String> resources = admin.getResourcesInCluster(clusterName);
      if (!resources.contains(resourceName)) {
        admin.addResource(clusterName, resourceName, rcxEventsProperties.getPartitionCount(),
            rcxEventsProperties.getStateModel(), RebalanceMode.FULL_AUTO.toString());
      }
      admin.rebalance(clusterName, resourceName, 1);
    } catch (ZkException zk) {
      log.error("Failed while setting up {} at the following ZK address {}  {}",
          rcxEventsProperties.getHelixClusterName(), rcxEventsProperties.getZookeeperAddress(), zk);
    } catch (Exception e) {
      log.error("Execption occured while setting up the cluster {}", e);
    } finally {
      if (zkclient != null) {
        zkclient.close();
      }
    }
  }

  @Override
  public void run(String... args) throws Exception {
    setupConsumerCluster();
  }

}
