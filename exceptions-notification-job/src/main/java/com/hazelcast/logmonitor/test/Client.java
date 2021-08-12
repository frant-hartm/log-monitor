package com.hazelcast.logmonitor.test;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.HazelcastJsonValue;
import com.hazelcast.internal.json.JsonObject;
import com.hazelcast.map.IMap;

public class Client {

    public static void main(String[] args) {
        HazelcastInstance hz = HazelcastClient.newHazelcastClient();
        try {
            IMap<String, HazelcastJsonValue> logMap = hz.getMap("logs");
            int i=0;
            while (true) {
                i++;
                try {
                    TimeUnit.SECONDS.sleep(5);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                long time = System.currentTimeMillis();
                JsonObject json = new JsonObject().add("level", i % 3 == 0 ? "ERROR" : "WARNING").add("message",
                        "Test " + time);
                System.out.println("> " + json.toString());
                logMap.put(UUID.randomUUID().toString(), new HazelcastJsonValue(json.toString()));
            }
        } finally {
            hz.shutdown();
        }
    }
}
