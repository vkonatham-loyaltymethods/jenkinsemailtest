package com.rcx.events.config;

import java.io.FileInputStream;
import java.security.KeyStore;
import java.util.concurrent.TimeUnit;
import javax.annotation.PostConstruct;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.mongodb.ReactiveMongoDatabaseFactory;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.SimpleReactiveMongoDatabaseFactory;
import com.mongodb.Block;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.connection.ClusterSettings;
import com.mongodb.connection.SslSettings;
import com.mongodb.connection.netty.NettyStreamFactoryFactory;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.rcx.events.properties.RcxMongoProperties;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import lombok.extern.log4j.Log4j2;

@Configuration
@Log4j2
public class MongoReactiveConfig {

  @Autowired
  private RcxMongoProperties mongoProperties;

  EventLoopGroup oplogEventLoopGroup = new NioEventLoopGroup();
  EventLoopGroup checkpointEventLoopGroup = new NioEventLoopGroup();
  SSLContext sslContext = null;

  @PostConstruct
  public void initializeSSLContext() throws Exception {
    sslContext = getSSLContext();
  }

  private SSLContext getSSLContext() throws Exception {
    SSLContext sslContext = SSLContext.getInstance("TLS");
    String keyStore = mongoProperties.getKeyStoreLocation();
    String keyStorePassword = mongoProperties.getKeyStorePassword();
    String trustStore = mongoProperties.getTrustStoreLocation();
    String trustStorePassword = mongoProperties.getTrustStorePassword();

    char[] keyStorePasswd = null;
    if (keyStorePassword.length() != 0) {
      keyStorePasswd = keyStorePassword.toCharArray();
    }

    char[] trustStorePasswd = null;
    if (trustStorePassword.length() != 0) {
      trustStorePasswd = trustStorePassword.toCharArray();
    }

    KeyStore ks = null;
    if (keyStore != null) {
      ks = KeyStore.getInstance(mongoProperties.getKeyStoreType());
      try (FileInputStream ksfis = new FileInputStream(keyStore)) {
        ks.load(ksfis, keyStorePasswd);
      }
    }
    KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
    kmf.init(ks, keyStorePasswd);

    KeyStore ts = null;
    if (trustStore != null) {
      ts = KeyStore.getInstance(mongoProperties.getTrustStoreType());
      try (FileInputStream tsfis = new FileInputStream(trustStore)) {
        ts.load(tsfis, trustStorePasswd);
      }
    }
    TrustManagerFactory tmf =
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    tmf.init(ts);

    sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
    return sslContext;
  }

  @Bean
  @Primary
  public MongoClient getOplogClient() {
    ConnectionString connectionString = new ConnectionString(mongoProperties.getOplogDBUrl());
    Block<SslSettings.Builder> sslSettingsBlock = new Block<SslSettings.Builder>() {
      @Override
      public void apply(SslSettings.Builder t) {
        try {
          t.applySettings(SslSettings.builder().enabled(true).context(sslContext).build());
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
    };

    Block<ClusterSettings.Builder> clusterSettingsBlock = new Block<ClusterSettings.Builder>() {
      @Override
      public void apply(ClusterSettings.Builder t) {
        t.serverSelectionTimeout(mongoProperties.getConnectionTimeout(), TimeUnit.MILLISECONDS);
      }
    };

    return MongoClients.create(MongoClientSettings.builder().applyConnectionString(connectionString)
        .applyToClusterSettings(clusterSettingsBlock)
        .streamFactoryFactory(
            NettyStreamFactoryFactory.builder().eventLoopGroup(oplogEventLoopGroup).build())
        .applyToSslSettings(sslSettingsBlock).build());
  }

  @Bean
  @Primary
  public ReactiveMongoDatabaseFactory reactiveOplogDatabaseFactory() {
    ConnectionString connectionString = new ConnectionString(mongoProperties.getOplogDBUrl());
    return new SimpleReactiveMongoDatabaseFactory(getOplogClient(), connectionString.getDatabase());
  }

  @Bean(name = "reactiveOplogMongoTemplate")
  @Primary
  public ReactiveMongoTemplate reactiveOplogMongoTemplate() {
    printDBUrl(mongoProperties.getOplogDBUrl());
    return new ReactiveMongoTemplate(reactiveOplogDatabaseFactory());
  }

  @Bean
  public MongoClient getCheckpointClient() {
    ConnectionString connectionString = new ConnectionString(mongoProperties.getCheckPointDBUrl());
    Block<SslSettings.Builder> sslSettingsBlock = new Block<SslSettings.Builder>() {
      @Override
      public void apply(SslSettings.Builder t) {
        t.applySettings(SslSettings.builder().enabled(true).build());
        try {
          t.applySettings(SslSettings.builder().enabled(true).context(sslContext).build());
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
    };

    Block<ClusterSettings.Builder> clusterSettingsBlock = new Block<ClusterSettings.Builder>() {
      @Override
      public void apply(ClusterSettings.Builder t) {
        t.serverSelectionTimeout(mongoProperties.getConnectionTimeout(), TimeUnit.MILLISECONDS);
      }
    };

    return MongoClients.create(MongoClientSettings.builder().applyConnectionString(connectionString)
        .applyToClusterSettings(clusterSettingsBlock)
        .streamFactoryFactory(
            NettyStreamFactoryFactory.builder().eventLoopGroup(checkpointEventLoopGroup).build())
        .applyToSslSettings(sslSettingsBlock).build());
  }

  @Bean
  public ReactiveMongoDatabaseFactory reactiveCheckpointDatabaseFactory() {
    ConnectionString connectionString = new ConnectionString(mongoProperties.getCheckPointDBUrl());
    return new SimpleReactiveMongoDatabaseFactory(getCheckpointClient(),
        connectionString.getDatabase());
  }

  @Bean(name = "reactiveCheckpointMongoTemplate")
  public ReactiveMongoTemplate reactiveCheckpointMongoTemplate() {
    printDBUrl(mongoProperties.getCheckPointDBUrl());
    return new ReactiveMongoTemplate(reactiveCheckpointDatabaseFactory());
  }

  private void printDBUrl(String dburl) {
    if (dburl == null || dburl.length() <= 0) {
      log.info("DB Connection String {}", dburl);
      return;
    }
    try {
      String url = dburl.replaceAll("\\/\\/([^:]+):(.*)@", "//$1:*****@");
      log.info("Connected to database @ {}", url);
    } catch (Exception ex) {
      log.error("Error while printing DB url {}", ex);
    }
  }

}
