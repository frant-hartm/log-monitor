# Log monitor Hazelcast example

### Usage

```
mvn clean package
mvn -f desktop-notification/pom.xml exec:java

docker-compose up --force-recreate

docker exec -it log-monitor_hz_1 hz-cli sql
select message from logs;
```

Zeppelin Notebook Log_errors: http://172.128.0.5:8080/