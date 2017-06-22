#!/bin/bash
set -e

# TODO Check minishift is running

commandDir=$(dirname "$0")
source ${commandDir}/env-for-minishift

oc login ${OPENSHIFT_ENDPOINT} -u ${OPENSHIFT_USERNAME} -p ${OPENSHIFT_PASSWORD} -n ${CHE_OPENSHIFT_PROJECT} > /dev/null
echo "# Deleting route..."
oc delete route -n ${CHE_OPENSHIFT_PROJECT} --all
echo "# Deleting service..."
oc delete svc -n ${CHE_OPENSHIFT_PROJECT} --all
echo "# Deleting pod, dc and rc..."
oc delete dc,rc,pod,rs -n ${CHE_OPENSHIFT_PROJECT} --all

echo "# Deleting serviceaccount..."
oc get serviceaccount che &> /dev/null && oc delete serviceaccount che

echo "# Deleting rolebinding..."
oc get rolebinding che &> /dev/null && oc delete rolebinding che

oc login ${OPENSHIFT_ENDPOINT} -u system:admin -n ${CHE_OPENSHIFT_PROJECT} > /dev/null
echo "# Deleting PVCs..."
oc delete pvc --all -n ${CHE_OPENSHIFT_PROJECT} 
echo "# Deleting PVs..."
oc delete pv --all -n ${CHE_OPENSHIFT_PROJECT}

oc login ${OPENSHIFT_ENDPOINT} -u ${OPENSHIFT_USERNAME} -p ${OPENSHIFT_PASSWORD} -n ${CHE_OPENSHIFT_PROJECT} > /dev/null
echo "# Deleting configmaps..."
oc get configmap che &> /dev/null && oc delete configmap che
