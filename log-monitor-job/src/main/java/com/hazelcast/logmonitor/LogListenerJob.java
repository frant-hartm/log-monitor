package com.hazelcast.logmonitor;

import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.HazelcastJsonValue;
import com.hazelcast.jet.JetService;
import com.hazelcast.jet.Traversers;
import com.hazelcast.jet.contrib.http.HttpListenerSources;
import com.hazelcast.jet.pipeline.Pipeline;
import com.hazelcast.jet.pipeline.Sinks;

import java.util.UUID;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Job exposing HTTP endpoint to capture logs
 */
public class LogListenerJob {

    public static void main(String[] args) {
        HazelcastInstance hz = Hazelcast.bootstrappedInstance();
        JetService jet = hz.getJet();

        Pipeline p = Pipeline.create();

        var source = HttpListenerSources.httpListener(
                8082,
                bytes -> new String(bytes, UTF_8)
        );

        var items = p.readFrom(source)
                     .withoutTimestamps()
                     .flatMap(item -> Traversers.traverseItems(item.split("\n")))
                     .filter(item -> !item.isBlank())
                     .map(HazelcastJsonValue::new);

        items.writeTo(Sinks.logger());
        items.writeTo(Sinks.map("logs", v -> UUID.randomUUID().toString(), v -> v));

        jet.newJob(p);
    }
}
