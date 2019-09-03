#!/usr/bin/env bash
# First parameter either "Start" or "Stop"
function podStarted {
  POD_NAME=$1
  SIMPLE_POD_JSON=$(oc get pod "$POD_NAME" -o json)
  RETURN_CODE=$?

  if [[ $RETURN_CODE -ne 0 ]]; then
    echo "Could not obtain information about pod. Trying again."
    return 1
  fi

  POD_STATUS=$(echo "$SIMPLE_POD_JSON" | jq --raw-output '.status.phase')

  echo "Wanted: Running     Actual: ${POD_STATUS}"
  if [[ $POD_STATUS == "Running" ]]; then
    echo "Pod is running."
    return 0
  else
    return 1
  fi
}

function podStopped {
  POD_NAME=$1
  if oc get pod "$POD_NAME" -o json > /dev/null 2>&1; then
    # pod found, not deleted yet.
    return 1
  else
    return 0
  fi
}

function waitForPodToBeRunning {
  echo "Waiting for pod to start"
  start=$(($(date +%s%N)/1000000))
  TIMEOUT_IN_MILLISEC=$(( $ATTEMPT_TIMEOUT * 1000))
  while [[ ${CURRENT_TRY_TIME} -le ${TIMEOUT_IN_MILLISEC} ]]; do
    end=$(($(date +%s%N)/1000000))
    CURRENT_TRY_TIME=$(expr $end - $start)
    if podStarted "$POD_NAME"; then
      echo $CURRENT_TRY_TIME >> Start.csv
      return
    else
      sleep 1
    fi
  done
  echo "Waiting for pod to change its state timed out. Exiting."
  exit 1
}

function waitForPodToStop {
  echo "Waiting for pod to be removed"
  start=$(($(date +%s%N)/1000000))
  TIMEOUT_IN_MILLISEC=$(( $ATTEMPT_TIMEOUT * 1000))
  while [[ ${CURRENT_TRY_TIME} -le ${TIMEOUT_IN_MILLISEC} ]]; do
    end=$(($(date +%s%N)/1000000))
    CURRENT_TRY_TIME=$(expr $end - $start)
    if podStopped "$POD_NAME"; then
      echo $CURRENT_TRY_TIME >> Stop.csv
      return
    else
      sleep 1
    fi
  done
  echo "Waiting for pod to change its state timed out. Exiting."
  exit 1
}

function simplePodRunTest {
  COUNTER=1
  echo "Number of iterations: ${ITERATIONS}"

  SIMPLE_POD_CONFIGURATION_JSON=$(jq ".spec.volumes[].persistentVolumeClaim.claimName |= \"$VOLUME_NAME\"" simple-pod.json)

  while [[ ${COUNTER} -le ${ITERATIONS} ]]; do
    echo "ITERATION #${COUNTER}"
    echo "$SIMPLE_POD_CONFIGURATION_JSON" | oc apply -f -
    echo "$SIMPLE_POD_CONFIGURATION_JSON" | oc apply -f -
    waitForPodToBeRunning

    oc delete pod simple-pod
    waitForPodToStop

    COUNTER=$((COUNTER+1))
  done

  echo "Tests were done."
}
