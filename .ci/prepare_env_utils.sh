#!/usr/bin/env bash

# Copyright (c) 2017 Red Hat, Inc.
# All rights reserved. This program and the accompanying materials
# are made available under the terms of the Eclipse Public License v1.0
# which accompanies this distribution, and is available at
# http://www.eclipse.org/legal/epl-v10.html

function installDependencies() {
# Getting core repos ready
	yum install --assumeyes epel-release
	yum update --assumeyes
	yum install --assumeyes python-pip
	
	# Test and show version
	pip -V
	
	# Getting dependencies ready
	yum install --assumeyes \
	            docker \
	            git \
	            patch \
	            pcp \
	            bzip2 \
	            golang \
	            make \
	            jq \
	            java-1.8.0-openjdk \
	            java-1.8.0-openjdk-devel \
	            centos-release-scl
	
	yum install --assumeyes \
	            rh-maven33 \
	            rh-nodejs8
	
	systemctl start docker
	pip install yq	
}

function checkAllCreds() {
	set -x
	CREDS_NOT_SET="false"
	curl -s "https://mirror.openshift.com/pub/openshift-v3/clients/${OC_VERSION}/linux/oc.tar.gz" | tar xvz -C /usr/local/bin

	if [[ -z "${QUAY_USERNAME}" && -z "${QUAY_PASSWORD}" ]]; then
	  echo "Docker registry credentials not set"
	  CREDS_NOT_SET="true"
	fi
	
	if [[ -z "${RH_CHE_AUTOMATION_DEV_CLUSTER_SA_TOKEN}" ]]; then
	  echo "RDU2C credentials not set"
	  CREDS_NOT_SET="true"
	fi
	
	if [[ -z "${RH_CHE_AUTOMATION_CHE_PREVIEW_USERNAME}" ]] ||
	   [[ -z "${RH_CHE_AUTOMATION_CHE_PREVIEW_PASSWORD}" ]]; then
	  echo "Prod-preview credentials not set."
	  CREDS_NOT_SET="true"
	fi
	
	if [[ "${CREDS_NOT_SET}" = "true" ]]; then
	  echo "Failed to parse jenkins secure store credentials"
	  exit 2
	else
	  echo "Credentials set successfully."
	fi
	set +x
}





