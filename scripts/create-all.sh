#!/bin/bash
set -e

# minishift is running
echo "# Checking if minishift is running..."
minishift status | grep -q "Running" ||(echo "Minishift is not running. Aborting"; exit 1)

if [ -z ${OPENSHIFT_USERNAME+x} ]; then echo "Env var OPENSHIFT_USERNAME is unset. Aborting"; exit 1; fi
if [ -z ${OPENSHIFT_PASSWORD+x} ]; then echo "Env var OPENSHIFT_PASSWORD is unset. Aborting"; exit 1; fi
if [ -z ${CHE_HOSTNAME+x} ]; then echo "Env var CHE_HOSTNAME is unset. Aborting"; exit 1; fi
if [ -z ${CHE_LOG_LEVEL+x} ]; then echo "Env var CHE_LOG_LEVEL is unset. Aborting"; exit 1; fi
if [ -z ${CHE_DEBUGGING_ENABLED+x} ]; then echo "Env var CHE_DEBUGGING_ENABLED is unset. Aborting"; exit 1; fi
if [ -z ${FABRIC8_ONLINE_PATH+x} ]; then echo "Env var FABRIC8_ONLINE_PATH is unset. Aborting"; exit 1; fi
if [ -z ${CHE_OPENSHIFT_PROJECT+x} ]; then echo "Env var CHE_OPENSHIFT_PROJECT is unset. Aborting"; exit 1; fi

# if [ ! -d "${FABRIC8_ONLINE_PATH}"apps/che/src/main/fabric8 ]; then echo "Folder ${FABRIC8_ONLINE_PATH}apps/che/src/main/fabric8 does not exists. Aborting"; exit 1; fi 

echo "# oc login..."
oc login -u ${OPENSHIFT_USERNAME} -p ${OPENSHIFT_PASSWORD} > /dev/null

# Check if the project exists othewise create it
if ! oc get project ${CHE_OPENSHIFT_PROJECT} &> /dev/null; then
  echo "# Creating project"
  oc new-project ${CHE_OPENSHIFT_PROJECT}
fi

oc project ${CHE_OPENSHIFT_PROJECT}

# Deploy configmaps
echo "# Create configmaps..."
if oc get configmap che &> /dev/null ; then oc delete configmap che &> /dev/null; fi
oc create configmap che \
      --from-literal=hostname-http=${CHE_HOSTNAME} \
      --from-literal=workspace-storage="/home/user/che/workspaces" \
      --from-literal=workspace-storage-create-folders="false" \
      --from-literal=local-conf-dir="/etc/conf" \
      --from-literal=openshift-serviceaccountname="che" \
      --from-literal=che-server-evaluation-strategy="single-port" \
      --from-literal=log-level=${CHE_LOG_LEVEL} \
      --from-literal=docker-connector="openshift" \
      --from-literal=port="8080" \
      --from-literal=remote-debugging-enabled=${CHE_DEBUGGING_ENABLED} 

# Deploy PVs (gofabric8 way)
#   gofabric8 should be installed:
#   https://github.com/fabric8io/gofabric8#getting-started
echo "# Create pvc..."
# TODO check if the pvc exist
oc apply -f ${FABRIC8_ONLINE_PATH}apps/che/src/main/fabric8/config-pvc.yml
oc apply -f ${FABRIC8_ONLINE_PATH}apps/che/src/main/fabric8/data-pvc.yml
oc apply -f ${FABRIC8_ONLINE_PATH}apps/che/src/main/fabric8/workspace-pvc.yml
oc login -u system:admin -n ${CHE_OPENSHIFT_PROJECT}  > /dev/null
# TODO check if pv exists (or if pvc has been bounded)
gofabric8 volumes
oc login -u ${OPENSHIFT_USERNAME} -p ${OPENSHIFT_PASSWORD} -n ${CHE_OPENSHIFT_PROJECT}  > /dev/null

# Deploy PVs (template way)
# oc apply -f os-templates/hostPath/pvconf.yaml
# oc apply -f os-templates/hostPath/pvdata.yaml
# oc apply -f os-templates/hostPath/pvworkspace.yaml
# oc login -u ${OPENSHIFT_USERNAME} -p ${OPENSHIFT_PASSWORD} -n ${CHE_OPENSHIFT_PROJECT}  > /dev/null
# oc apply -f ${FABRIC8_ONLINE_PATH}apps/che/src/main/fabric8/config-pvc.yml
# oc apply -f ${FABRIC8_ONLINE_PATH}apps/che/src/main/fabric8/data-pvc.yml
# oc apply -f ${FABRIC8_ONLINE_PATH}apps/che/src/main/fabric8/workspace-pvc.yml

# Create and configure service account
# TODO check if service account already exists
# echo "# Create service account..."
# echo "apiVersion: \"v1\"
# kind: \"ServiceAccount\"
# $(cat ${FABRIC8_ONLINE_PATH}apps/che/src/main/fabric8/sa.yml)
# " | oc apply -f -

# echo "apiVersion: \"v1\"
# kind: \"RoleBinding\"
# $(cat ${FABRIC8_ONLINE_PATH}apps/che/src/main/fabric8/rb.yml)
# " | oc apply -f -

oc login -u ${OPENSHIFT_USERNAME} -p ${OPENSHIFT_PASSWORD} -n ${CHE_OPENSHIFT_PROJECT} > /dev/null
export CHE_OPENSHIFT_SERVICE_ACCOUNT=che
oc create serviceaccount ${CHE_OPENSHIFT_SERVICE_ACCOUNT}
oc login -u system:admin -n ${CHE_OPENSHIFT_PROJECT} > /dev/null
#oadm policy add-role-to-user -n ${CHE_OPENSHIFT_PROJECT} edit system:serviceaccount:${CHE_OPENSHIFT_PROJECT}:${CHE_OPENSHIFT_SERVICE_ACCOUNT}
oc adm policy add-cluster-role-to-user cluster-admin system:serviceaccount:${CHE_OPENSHIFT_PROJECT}:${CHE_OPENSHIFT_SERVICE_ACCOUNT}
# oc adm policy add-scc-to-user anyuid ${CHE_OPENSHIFT_SERVICE_ACCOUNT}
oc adm policy add-scc-to-group anyuid system:authenticated
oc login -u ${OPENSHIFT_USERNAME} -p ${OPENSHIFT_PASSWORD} -n ${CHE_OPENSHIFT_PROJECT} > /dev/null

#Deploy Che server
echo "# Deploy che..."
echo "apiVersion: \"v1\"
kind: \"DeploymentConfig\"
metadata:
  name: \"che\"
  labels:
      project: che
      provider: fabric8
$(cat ${FABRIC8_ONLINE_PATH}apps/che/src/main/fabric8/deployment.yml)
    metadata:
      labels:
        project: che
        provider: fabric8
  selector:
    project: che
    provider: fabric8
" | oc apply -f -

#Create Che service
echo "# Create service..."
sed "s/\${project.artifactId}/che/g" \
   ${FABRIC8_ONLINE_PATH}apps/che/src/main/fabric8/svc.yml | oc apply -f -

#Create Che route
echo "# Create route..."
oc expose service che-host --hostname=${CHE_HOSTNAME}

echo "Che has been successfully deployed on $(oc get route che-host -o jsonpath='{.spec.host}')"

if [ "${CHE_DEBUGGING_ENABLED}" == "true" ]; then
  echo "# Create debug route..."
  oc expose dc che --name=che-host-debug --target-port=http-debug --port=8000 --type=NodePort
  NodePort=$(oc get service che-host-debug -o jsonpath='{.spec.ports[0].nodePort}')
  echo "And remote debugging is possible attaching to URL $(minishift ip):${NodePort}"
fi

# A simpler solution would be:
# export CHE_TEMPLATE_VERSION=1.0.54
# curl -sSL http://central.maven.org/maven2/io/fabric8/online/apps/che/${CHE_TEMPLATE_VERSION}/che-${CHE_TEMPLATE_VERSION}-openshift.yml | \
#       sed "s/    hostname-http:.*/    hostname-http: ${CHE_HOSTNAME}/" | \
#       sed "s/    log-level:.*/    log-level: ${CHE_LOG_LEVEL}/" | \
#       sed "s/    remote-debugging-enabled:.*/    hostname-http: ${CHE_DEBUGGING_ENABLED}/" | \
#       oc apply -f -

