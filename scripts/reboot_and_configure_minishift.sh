#!/bin/bash

# Delete local OpenShift cluster if exists
minishift delete

# Srart new OpenShift cluster
minishift start

# Using minishift docker daemon
eval $(minishift docker-env)

# Default OpenShift credentials
DEFAULT_CHE_OPENSHIFT_USERNAME="developer"
DEFAULT_CHE_OPENSHIFT_PASSWORD="developer"

# CHE_OPENSHIFT_USERNAME and CHE_OPENSHIFT_PASSWORD variables can be used for custom credentials
CHE_OPENSHIFT_USERNAME=${CHE_OPENSHIFT_USERNAME:-${DEFAULT_CHE_OPENSHIFT_USERNAME}}
CHE_OPENSHIFT_PASSWORD=${CHE_OPENSHIFT_PASSWORD:-${DEFAULT_CHE_OPENSHIFT_PASSWORD}}

# Create OpenShift project
oc login -u ${CHE_OPENSHIFT_USERNAME} -p ${CHE_OPENSHIFT_PASSWORD}
oc new-project eclipse-che

# Create a serviceaccount with privileged scc
oc login -u system:admin
oc create serviceaccount cheserviceaccount
oc adm policy add-scc-to-user privileged -z cheserviceaccount
oc adm policy add-cluster-role-to-user cluster-admin system:serviceaccount:eclipse-che:cheserviceaccount

oc login -u ${CHE_OPENSHIFT_USERNAME} -p ${CHE_OPENSHIFT_PASSWORD}
export CHE_HOSTNAME=che.openshift.mini
export CHE_OPENSHIFT_ENDPOINT=https://$(minishift ip):8443

# Reminder for updating /etc/hosts file with a line that associates minishift IP address and the hostname che.openshift.mini
echo "$(tput setaf 3)NOTE: update /etc/hosts file - $(tput setaf 2) $(minishift ip) che.openshift.mini $(tput sgr0)"
