package com.hazelcast.logmonitor;

import static com.hazelcast.jet.Util.mapEventNewValue;
import static com.hazelcast.jet.Util.mapPutEvents;
import static com.hazelcast.jet.pipeline.JournalInitialPosition.START_FROM_OLDEST;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;

import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.HazelcastJsonValue;
import com.hazelcast.internal.json.Json;
import com.hazelcast.internal.json.JsonObject;
import com.hazelcast.jet.JetService;
import com.hazelcast.jet.aggregate.AggregateOperations;
import com.hazelcast.jet.config.JobConfig;
import com.hazelcast.jet.datamodel.KeyedWindowResult;
import com.hazelcast.jet.pipeline.Pipeline;
import com.hazelcast.jet.pipeline.Sink;
import com.hazelcast.jet.pipeline.SinkBuilder;
import com.hazelcast.jet.pipeline.Sources;
import com.hazelcast.jet.pipeline.StreamSource;
import com.hazelcast.jet.pipeline.WindowDefinition;

public class SinkErrorsToHttp {

    public static void main(String[] args) {
        StreamSource<HazelcastJsonValue> src = Sources.<HazelcastJsonValue, String, HazelcastJsonValue>mapJournal(
                "logs",
                START_FROM_OLDEST,
                mapEventNewValue(),
                mapPutEvents()
        );

        Sink<String> httpSink = SinkBuilder
                .sinkBuilder("httpSink", ctx -> ctx.hazelcastInstance().<String, String>getMap("_config.httpSink"))
                .<String>receiveFn((configMap, item) -> {
                    try {
                        String url = configMap.getOrDefault("url", "http://172.128.0.1:8085/warning4");
                        System.out.println(">>> Firing event to URL: " + url);
                        HttpRequest request = HttpRequest.newBuilder()
                                                         .uri(URI.create(url))
                                                         .timeout(Duration.ofSeconds(3))
                                                         .POST(BodyPublishers.ofString(item))
                                                         .build();
                        
                        HttpClient.newHttpClient().send(request, BodyHandlers.discarding());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }).build();

        Pipeline pipeline = Pipeline.create();

        pipeline.readFrom(src)
                .withIngestionTimestamps()
                .map(jstr -> {
                    String msg = jstr.toString();
                    System.out.println(">>> Message size: " + msg.length());
                    try {
                        JsonObject jsObj = Json.parse(msg).asObject();
                        return jsObj;
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    return null;
                })
                .filter(jo -> jo != null && !jo.isNull())
                .filter(jo -> {
                    String lvl = jo.get("level").asString();
                    System.out.println(">>> Filter level " + lvl);
                    boolean stacktraceIsNull = jo.get("stackTrace")!=null && jo.get("stacktrace")!=null;
                    System.out.println(">>> stacktrace null? " + stacktraceIsNull);
                    return ("ERROR".equals(lvl) || "SEVERE".equals(lvl)) && ! stacktraceIsNull;
                })
                .map(j -> {
                    String msg = (j.get("stackTrace")==null?j.get("stacktrace"):j.get("stackTrace")).asString();
                    System.out.println(">>> " + msg.substring(0, 30));
                    return msg;
                })

                // basically SELECT message,SUM(*) as count FROM ... WHERE count > 3 GROUP BY stacktrace
                .window(WindowDefinition.sliding(60_000, 5_000))// x second window, moving every y seconds
                .groupingKey(stacktrace -> stacktrace)// group by stacktrace
                .aggregate(AggregateOperations.counting())// count
                .filter(pair -> pair.getValue() > 3)
                .map(KeyedWindowResult::toString)// change to something nicer
                .writeTo(httpSink);

        HazelcastInstance hz = Hazelcast.bootstrappedInstance();
        JetService jet = hz.getJet();
        JobConfig config = new JobConfig();
        config.setName("logs-processor");
        // config.setProcessingGuarantee(ProcessingGuarantee.EXACTLY_ONCE);
        jet.newJobIfAbsent(pipeline, config);
    }
}
