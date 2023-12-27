package com.rcx.events.kafka;

import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;
import org.apache.kafka.clients.producer.Partitioner;
import org.apache.kafka.common.Cluster;
import org.apache.kafka.common.PartitionInfo;
import com.newrelic.api.agent.Trace;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class KafkaProducerPartitioner implements Partitioner {

  @Override
  public void configure(Map<String, ?> configs) {}

  private int partitionNumber = 0;

  @Override
  @Trace
  public int partition(String topic, Object objKey, byte[] keyBytes, Object value,
      byte[] valueBytes, Cluster cluster) {

    final List<PartitionInfo> partitionInfoList = cluster.availablePartitionsForTopic(topic);
    final int partitionCount = partitionInfoList.size();
    final String key = ((String) objKey);
    int partn;
    if (key != null) {
      CRC32 crc = new CRC32();
      crc.update(key.getBytes());
      partn = (int) (crc.getValue() % partitionCount);
      log.debug("Message {} goes to kafka partition={}", key, partn);
      return partn;
    }

    partitionNumber++;
    if (partitionNumber >= partitionCount) {
      partitionNumber = 0;
    }
    partn = partitionInfoList.get(partitionNumber).partition();
    log.debug("Message {} goes to kafka partition={}", key, partn);
    return partn;
  }

  @Override
  public void close() {}

}
