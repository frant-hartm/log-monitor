package com.hazelcast.logmonitor.test;

import com.hazelcast.config.Config;
import com.hazelcast.config.EvictionPolicy;
import com.hazelcast.config.JoinConfig;
import com.hazelcast.config.MapConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.spi.properties.ClusterProperty;

public class Member {

    public static void main(String[] args) {
        Config config = new Config().setProperty(ClusterProperty.SOCKET_BIND_ANY.getName(), "false");
        config.getJetConfig().setEnabled(true).setResourceUploadEnabled(true);
        JoinConfig joinConfig = config.getNetworkConfig().getJoin();
        joinConfig.getAutoDetectionConfig().setEnabled(false);
        joinConfig.getMulticastConfig().setEnabled(false);
        joinConfig.getTcpIpConfig().setEnabled(true).addMember("127.0.0.1");
        MapConfig mapConfig = new MapConfig("logs");
        mapConfig.getEventJournalConfig().setEnabled(true);
        mapConfig.getEvictionConfig().setEvictionPolicy(EvictionPolicy.LFU);
//        mapConfig.setTimeToLiveSeconds()
        config.addMapConfig(mapConfig);
        Hazelcast.newHazelcastInstance(config);
    }
}
