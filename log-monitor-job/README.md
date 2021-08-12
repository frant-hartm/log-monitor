## Usage

Deploy pipeline:

```bash
hz-cli submit target/log-monitor-*.jar
```

Send logs:

```bash
# batch
cat json.log | while read line ; do echo $line | curl -X POST --data-binary @- http://localhost:8082 ; done
# stream
tail -f json.log | while read line ; do echo $line | curl -X POST --data-binary @- http://localhost:8082 ; done
```
