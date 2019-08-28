#!/usr/bin/env bash

med=0
max=0
min=0
avg=0
count=0

function processItem {
  time_taken=$1
  if [[ ${count} == 0 ]]; then
    max=${time_taken}
    min=${time_taken}
    avg=${time_taken}
    count=1
  else
    avg=${avg}+${time_taken}
    if [[ ${max} -lt ${time_taken} ]]; then
      max=${time_taken}
    fi
    if [[ ${min} -gt ${time_taken} ]]; then
      min=${time_taken}
    fi
    count=$((count+1))
  fi
}

function getMetrics {
  file=$1
  while IFS=, read -r time_taken
  do
    processItem "$time_taken"
  done < "$file"
  med=$(sed "$((count/2+1))q;d" tmp.csv)
}

function addMetricsToLogFile {
  PREFIX_CURRENT=$1
  text="$HOST $PREFIX_CURRENT"
  echo "$text-median $ZABBIX_TIMESTAMP $med" >> "$logfile"
  echo "$text-max $ZABBIX_TIMESTAMP $max" >> "$logfile"
  echo "$text-min $ZABBIX_TIMESTAMP $min" >> "$logfile"
  echo "$text-avg $ZABBIX_TIMESTAMP $((avg/count))" >> "$logfile"
  med=0
  max=0
  min=0
  avg=0
  count=0
}

function generateLogs {
  METRICS=$1
  sort -n "$METRICS".csv > tmp.csv
  getMetrics "$METRICS".csv
  if [ $ITERATIONS == 1 ]; then
    PREFIX_CURRENT="${PREFIX}$(echo ${METRICS,,})-actual"
    echo "actual: $med for ${PREFIX_CURRENT}"
    text="$HOST $PREFIX_CURRENT"
    echo "$text $ZABBIX_TIMESTAMP $max" >> "$logfile"
    med=0
    max=0
    min=0
    avg=0
    count=0
  else
    echo "median: $med   minimum: $min   maximum: $max   average:$((avg/count))"
    PREFIX_CURRENT="${PREFIX}$(echo ${METRICS,,})"
    addMetricsToLogFile "$PREFIX_CURRENT"
  fi
}

ZABBIX_TIMESTAMP=`date +%s`
URL=$1
ZABBIX_PREFIX=$2
ITERATIONS=$3
logfile=zabbix.log

if [ -f "$logfile" ]; then
  rm "$logfile"
fi

PREFIX=${ZABBIX_PREFIX:-"mount_volume-"}
if [[ $URL = *"2a"* ]]; then
  HOST=qa-starter-us-east-2a
else
  HOST=qa-starter-us-east-2
fi;

generateLogs Start
generateLogs Stop

echo "  Uploading report to zabbix...";
zabbix_sender -vv -i zabbix.log -T -z "$ZABBIX_SERVER" -p "$ZABBIX_PORT";
