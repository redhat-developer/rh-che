#!/bin/bash

# Delete local OpenShift cluster if exists
minishift delete

# Srart new OpenShift cluster
minishift start

# Using minishift docker daemon
eval $(minishift docker-env)

# Default OpenShift credentials
DEFAULT_CHE_OPENSHIFT_USERNAME="openshift-dev"
DEFAULT_CHE_OPENSHIFT_PASSWORD="devel"

# CHE_OPENSHIFT_USERNAME and CHE_OPENSHIFT_PASSWORD variables can be used for custom credentials
CHE_OPENSHIFT_USERNAME=${CHE_OPENSHIFT_USERNAME:-${DEFAULT_CHE_OPENSHIFT_USERNAME}}
CHE_OPENSHIFT_PASSWORD=${CHE_OPENSHIFT_PASSWORD:-${DEFAULT_CHE_OPENSHIFT_PASSWORD}}

# Enable OpenShift router
oc login -u admin -p admin -n default
docker pull openshift/origin-haproxy-router:`oc version | awk '{ print $2; exit }'`
oc adm policy add-scc-to-user hostnetwork -z router
oc adm router --create --service-account=router --expose-metrics --subdomain="openshift.mini"

# Create OpenShift project
oc login -u ${CHE_OPENSHIFT_USERNAME} -p ${CHE_OPENSHIFT_PASSWORD}
oc new-project eclipse-che

# Create a serviceaccount with privileged scc
oc login -u admin -p admin -n eclipse-che
oc create serviceaccount cheserviceaccount
oc adm policy add-scc-to-user privileged -z cheserviceaccount

oc login -u ${CHE_OPENSHIFT_USERNAME} -p ${CHE_OPENSHIFT_PASSWORD}
export CHE_HOSTNAME=che.openshift.mini
export DOCKER0_IP=$(docker run -ti --rm --net=host alpine ip addr show docker0 | grep "inet\b" | awk '{print $2}' | cut -d/ -f1)
export CHE_OPENSHIFT_ENDPOINT=https://$(minishift ip):8443

# Reminder for updating /etc/hosts file with a line that associates minishift IP address and the hostname che.openshift.mini
echo "$(tput setaf 3)NOTE: update /etc/hosts file - $(tput setaf 2) $(minishift ip) che.openshift.mini $(tput sgr0)"
