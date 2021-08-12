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
import java.util.UUID;

import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.HazelcastJsonValue;
import com.hazelcast.internal.json.Json;
import com.hazelcast.internal.json.JsonObject;
import com.hazelcast.internal.json.JsonValue;
import com.hazelcast.jet.JetService;
import com.hazelcast.jet.config.JobConfig;
import com.hazelcast.jet.pipeline.Pipeline;
import com.hazelcast.jet.pipeline.Sink;
import com.hazelcast.jet.pipeline.SinkBuilder;
import com.hazelcast.jet.pipeline.Sources;
import com.hazelcast.jet.pipeline.StreamSource;

public class SinkErrorsToHttp {

    public static void main(String[] args) {
        StreamSource<HazelcastJsonValue> src = Sources.<HazelcastJsonValue, String, HazelcastJsonValue> mapJournal("logs",
                START_FROM_OLDEST, mapEventNewValue(), mapPutEvents());
        Sink<String> httpSink = SinkBuilder
                .sinkBuilder("httpSink", ctx -> ctx.hazelcastInstance().<String, String> getMap("_config.httpSink"))
                .<String> receiveFn((configMap, item) -> {
                    String url = configMap.getOrDefault("url", "http://127.0.0.1:8080/warning4");
                    HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(3))
                            .POST(BodyPublishers.ofString(item)).build();
                    HttpClient.newHttpClient().send(request, BodyHandlers.discarding());
                }).build();
        Pipeline pipeline = Pipeline.create();
        pipeline.readFrom(src).withoutTimestamps().map(jstr -> Json.parse(jstr.toString()))
                .map(jv -> (jv instanceof JsonObject) ? (JsonObject) jv : (JsonObject) null).filter(jo -> {
                    if (jo == null) {
                        return false;
                    }
                    String strLevel = jo.get("level").asString();
                    return strLevel == null || "ERROR".equalsIgnoreCase(strLevel);
                }).map(j -> j.get("message").asString()).writeTo(httpSink);

        HazelcastInstance hz = Hazelcast.bootstrappedInstance();
        JetService jet = hz.getJet();
        JobConfig config = new JobConfig();
        config.setName("logs-processor");
        // config.setProcessingGuarantee(ProcessingGuarantee.EXACTLY_ONCE);
        jet.newJobIfAbsent(pipeline, config).join();
    }
}
