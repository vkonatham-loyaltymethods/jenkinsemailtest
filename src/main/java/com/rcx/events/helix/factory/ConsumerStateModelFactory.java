package com.rcx.events.helix.factory;

import org.apache.helix.ZNRecord;
import org.apache.helix.participant.statemachine.StateModelFactory;
import org.apache.helix.store.zk.ZkHelixPropertyStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import com.rcx.events.helix.statemodel.ConsumerStateModel;

public class ConsumerStateModelFactory extends StateModelFactory<ConsumerStateModel> {
  private final String consumerId;
  
  @Autowired
  private AutowireCapableBeanFactory beanFactory;  
  private ZkHelixPropertyStore<ZNRecord> store;
  
  public ConsumerStateModelFactory(String consumerId, ZkHelixPropertyStore<ZNRecord> store) {
    this.consumerId = consumerId;
    this.store = store;
  }
  
  @Override
  public ConsumerStateModel createNewStateModel(String resource, String partition) {
    ConsumerStateModel consumerStateModel = new ConsumerStateModel(consumerId, partition, store);
    beanFactory.autowireBean(consumerStateModel);
    return consumerStateModel;
  }
}
