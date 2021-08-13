package com.hazelcast.logmonitor;

import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.HazelcastJsonValue;
import com.hazelcast.jet.JetService;
import com.hazelcast.jet.Traversers;
import com.hazelcast.jet.contrib.http.HttpListenerSources;
import com.hazelcast.jet.pipeline.Pipeline;
import com.hazelcast.jet.pipeline.Sinks;
import com.hazelcast.sql.SqlResult;
import com.hazelcast.sql.SqlRow;
import com.hazelcast.sql.SqlService;

import java.util.UUID;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Job exposing HTTP endpoint to capture logs
 */
public class LogListenerJob {

    public static void main(String[] args) {
        HazelcastInstance hz = Hazelcast.bootstrappedInstance();
        JetService jet = hz.getJet();
        SqlService sql = hz.getSql();

        createMappingIfNeeded(sql);

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

    private static void createMappingIfNeeded(SqlService sql) {
        boolean mappingExists = false;
        try (SqlResult mappings = sql.execute("SHOW MAPPINGS")) {
            for (SqlRow row : mappings) {
                Object name = row.getObject("name");
                if ("logs".equals(name)) {
                    System.out.println("Mapping logs already exists.");
                    mappingExists = true;
                }
            }
        }

        if (!mappingExists) {
            SqlResult result = sql.execute("" +
                    "CREATE MAPPING logs (\n" +
                    "  __key VARCHAR,\n" +
                    "  hostName VARCHAR,\n" +
                    "  level VARCHAR,\n" +
                    "  loggerClassName VARCHAR,\n" +
                    "  loggerName VARCHAR,\n" +
                    "  message VARCHAR,\n" +
                    "  ndc VARCHAR,\n" +
                    "  processId INT,\n" +
                    "  processName VARCHAR,\n" +
                    "  sequence INT,\n" +
                    "  threadId INT,\n" +
                    "  threadName VARCHAR,\n" +
                    " \"timestamp\" VARCHAR\n" +
                    ")\n" +
                    "TYPE IMap\n" +
                    "OPTIONS (\n" +
                    "  'keyFormat' = 'varchar',\n" +
                    "  'valueFormat' = 'json'\n" +
                    ")");
            System.out.println("Created mapping result: " + result.updateCount());
        }
    }
}
