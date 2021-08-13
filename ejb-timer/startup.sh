#!/bin/bash

$JBOSS_HOME/bin/jboss-cli.sh --file=/mnt/json-file-logger.cli

mkdir $JBOSS_HOME/standalone/log
touch $JBOSS_HOME/standalone/log/server.json
(
  tail -f $JBOSS_HOME/standalone/log/server.json | while read line; do
    echo $line | curl -X POST --data-binary @- http://172.128.0.2:8082
  done
) >/dev/null 2>&1 & 

$JBOSS_HOME/bin/standalone.sh -b 0.0.0.0