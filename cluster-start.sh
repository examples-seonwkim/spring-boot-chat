#!/bin/bash

set -e

BASE_PORT=$1
BASE_PEKKO_PORT=$2
INSTANCE_COUNT=$3

if [[ -z "$BASE_PORT" || -z "$BASE_PEKKO_PORT" || -z "$INSTANCE_COUNT" ]]; then
  echo "Usage: $0 <basePort> <basePekkoPort> <instanceCount>"
  exit 1
fi

run_application() {
  local instance=$1
  local port=$((BASE_PORT + instance))
  local pekko_port=$((BASE_PEKKO_PORT + instance))

  echo "Starting instance $instance: port=${port}, pekko_port=${pekko_port}"

  ./gradlew bootRun \
    --args="--server.port=${port} \
            --spring.actor.pekko.remote.artery.canonical.port=${pekko_port}" \
    -PmainClass=${MAIN_CLASS} \
    > "log_${port}.txt" 2>&1 &
}

for ((i=0; i<INSTANCE_COUNT; i++)); do
  run_application $i
done
