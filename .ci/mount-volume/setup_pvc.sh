#!/usr/bin/env bash

if (oc get pvc | grep -q "${VOLUME_NAME}") ; then
  echo "PVC ${VOLUME_NAME} already exists - skipping creating and setup."
  exit 0
else
  echo "PVC ${VOLUME_NAME} does not exists - creating..."
  PVC_TMP=$(cat pvc.yaml)
  PVC_TMP=${PVC_TMP//"replace_with_pvc_name"/"$VOLUME_NAME"}
  if echo "$PVC_TMP" | oc create -f - ; then
    echo "PVC ${VOLUME_NAME} was created successfully."
  else
    echo "Not able to create volume ${VOLUME_NAME}. Finishing tests."
    exit 1
  fi
fi

if [[ -z $FILL_PVC ]]; then
  exit 0
else
  echo -e "\\nFilling PVC with data."
  source ./simple-pod.sh
  POD_NAME="simple-pod-large"

  SIMPLE_POD_CONFIGURATION_JSON=$(jq ".spec.volumes[].persistentVolumeClaim.claimName |= \"$VOLUME_NAME\"" $POD_NAME.json)
  echo "$SIMPLE_POD_CONFIGURATION_JSON" | oc apply -f - #creating pod 
  echo "$SIMPLE_POD_CONFIGURATION_JSON" | oc apply -f - #applying configuration
  if [[ $? != 0 ]]; then 
    echo "The pod could not be created and configured. Finishing tests."
    exit 1
  fi

  echo "Waiting for max $ATTEMPT_TIMEOUT seconds for pod to start."
  waitForPod "Start" "$POD_NAME"

  echo "Cloning project from https://github.com/angular/quickstart.git."
  oc exec $POD_NAME -- git clone https://github.com/angular/quickstart.git /data/quickstart
  if [[ $? != 0 ]]; then 
    echo "Project could not be cloned. Cleaning environment and finishing tests."
    oc delete pod "$POD_NAME"
    oc delete pvc "$VOLUME_NAME"
    exit 1
  fi

  echo "Running command \"npm install\" to download dependencies..."
  oc exec $POD_NAME -- npm --prefix /data/quickstart install /data/quickstart
  if [[ $? != 0 ]]; then 
    echo "The dependencies were not downloaded. Cleaning environment and finishing tests."
    oc delete pod "$POD_NAME"
    oc delete pvc "$VOLUME_NAME"
    exit 1
  fi

  DEPS=$(oc exec $POD_NAME -- find /data/quickstart/node_modules | wc -l)
  if [[ $DEPS -ne 18710 ]]; then
    echo "WARN: Dependencies expected: 18710   Dependencies gotten: $DEPS"
    echo "Continuing tests."
  else	
    echo -e "Expected number of dependencies were downloaded."
  fi

  echo "Removing pod $POD_NAME"
  oc delete pod "$POD_NAME"
  waitForPod "Stop" "$POD_NAME"
fi

exit 0
