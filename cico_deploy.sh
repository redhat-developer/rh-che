#!/bin/bash
set -u
set +e

# Source build variables
cat jenkins-env | grep -e ^CHE_ > inherit-env
. inherit-env
. config 

# Install oc client
yum install -y centos-release-openshift-origin
yum install -y origin-clients

if [ `echo $CHE_CLUSTERS | wc -c` -le 2 ]; then
  echo 'No clusters specified'
  exit 3
fi

set +x
for CLUSTER in $CHE_CLUSTERS; do 
  echo "Deploying to Cluster " $CLUSTER
  CHE_OPENSHIFT_ENDPOINT=$(eval echo \${CHE_${CLUSTER}_ENDPOINT})
  CHE_OPENSHIFT_USERNAME=$(eval echo \${CHE_${CLUSTER}_USERNAME})
  CHE_OPENSHIFT_PASSWORD=$(eval echo \${CHE_${CLUSTER}_PASSWORD})
  CHE_OPENSHIFT_PROJECT=$(eval echo \${CHE_${CLUSTER}_PROJECT})
  CHE_OPENSHIFT_HOSTNAME=$(eval echo \${CHE_${CLUSTER}_HOSTNAME})
  CHE_APPLICATION_NAME=che

  # Login
  oc login "${CHE_OPENSHIFT_ENDPOINT}" -u "${CHE_OPENSHIFT_USERNAME}" -p "${CHE_OPENSHIFT_PASSWORD}" --insecure-skip-tls-verify
  # Ensure we're in the che project
  oc project ${CHE_OPENSHIFT_PROJECT}

  # Check if deploymentConfig is already present
  OUT=$(oc -n ${CHE_OPENSHIFT_PROJECT} get dc ${CHE_APPLICATION_NAME} 2> /dev/null || true)
  if [[ $OUT != "" ]]; then
    # Cleanup the project
    oc -n ${CHE_OPENSHIFT_PROJECT} delete dc,route,svc,po,pvc --all || true
    sleep 30
  fi

  # Deploy che from the template
  # oc adm policy add-scc-to-group anyuid system:authenticated or at least
  # oc adm policy add-scc-to-group nonrot system:authenticated
  cat che.yaml | \
      sed "s/    hostname-http:.*/    hostname-http: ${CHE_OPENSHIFT_HOSTNAME}/" | \
      sed "s/          image: rhche/          image: ${DOCKER_HUB_NAMESPACE}/" | \
      sed "s/nightly-fabric8-no-dashboard/${RH_CHE_DOCKER_IMAGE_VERSION}/" | \
      sed "s/<route-host>/${CHE_OPENSHIFT_HOSTNAME}/" | \
            oc apply -f -
  if [ $? -eq 0 ]; then 
    echo 'Deploy succeeded'
  else 
    echo 'Deploy Failed!'
    exit 1
  fi

  # Create route
  oc expose service che-host --hostname=${CHE_OPENSHIFT_HOSTNAME}

done
