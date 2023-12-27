package com.rcx.events.kafka;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.List;
import org.apache.kafka.common.Cluster;
import org.apache.kafka.common.PartitionInfo;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
public class KafkaProducerPartitionerTest {

  @Mock
  private Cluster cluster;

  @Mock
  private List<PartitionInfo> partitionInfoList;

  @Mock
  private PartitionInfo PartitionInfo;

  @InjectMocks
  private KafkaProducerPartitioner kafkaProducerPartitioner;

  @Before
  public void setUp() {
    kafkaProducerPartitioner = new KafkaProducerPartitioner();
    when(cluster.availablePartitionsForTopic(Mockito.anyString())).thenReturn(partitionInfoList);
    when(partitionInfoList.size()).thenReturn(5);
  }

  @Test
  public void TestPartition() {
    int selectedpartition = kafkaProducerPartitioner.partition("test-topic",
        "5c46e2367b2bfe0102059ff4", null, null, null, cluster);
    verify(cluster, times(1)).availablePartitionsForTopic(Mockito.anyString());
    verify(partitionInfoList, times(1)).size();
    Assert.assertTrue("Should return parition number as 1", selectedpartition == 1);

    when(partitionInfoList.size()).thenReturn(7);
    selectedpartition = kafkaProducerPartitioner.partition("test-topic", "5c46e2367b2bfe0102059ff4",
        null, null, null, cluster);
    Assert.assertTrue("Should return parition number as 6", selectedpartition == 6);

    when(partitionInfoList.size()).thenReturn(2);
    when(partitionInfoList.get(Mockito.anyInt())).thenReturn(PartitionInfo);
    when(PartitionInfo.partition()).thenReturn(2);

    selectedpartition =
        kafkaProducerPartitioner.partition("test-topic", null, null, null, null, cluster);
    Assert.assertTrue("Should return parition number as 2", selectedpartition == 2);

    when(PartitionInfo.partition()).thenReturn(4);
    selectedpartition =
        kafkaProducerPartitioner.partition("test-topic", null, null, null, null, cluster);
    Assert.assertTrue("Should return parition number as 4", selectedpartition == 4);
  }

}
