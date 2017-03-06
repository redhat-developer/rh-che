#!/bin/sh
#
# This script allow to deploy/delete Che on OpenShift. 
# To run it:
#     ./openche.sh [deploy|delete]
#
# Before running the script OpenShift should be configured properly:
#
# 1. Run OpenShift
# ----------------
# If we don't have a running OpenShift instance we can start it as a container:
# docker run -d --name "origin" \
#         --privileged --pid=host --net=host \
#         -v /:/rootfs:ro -v /var/run:/var/run:rw -v /sys:/sys -v /var/lib/docker:/var/lib/docker:rw \
#         -v /var/lib/origin/openshift.local.volumes:/var/lib/origin/openshift.local.volumes \
#         openshift/origin start
#
# 2. Create an OpenShift project
# ------------------------------
# oc login -u mario
# oc new-project openche
#
# 3. Create a serviceaccount with privileged scc
# -------------------------------------------------
# oc login -u system:admin
# oc create serviceaccount cheserviceaccount
# oc adm policy add-scc-to-user privileged -z cheserviceaccount
#
# 4. Set the env variables (optional)
# ------------------------------------
# export CHE_HOSTNAME=che.openshift.mini
# export CHE_IMAGE=codenvy/che-server:local


set_parameters() {
    echo "Setting parameters"
    DEFAULT_CHE_HOSTNAME=che.openshift.mini
    DEFAULT_CHE_IMAGE=rhche/che-server:nightly
    DEFAULT_CHE_LOG_LEVEL=DEBUG
    DEFAULT_CHE_TEMPLATE="../os-templates/che.json"

    CHE_HOSTNAME=${CHE_HOSTNAME:-${DEFAULT_CHE_HOSTNAME}}
    CHE_IMAGE=${CHE_IMAGE:-${DEFAULT_CHE_IMAGE}}
    CHE_LOG_LEVEL=${CHE_LOG_LEVEL:-${DEFAULT_CHE_LOG_LEVEL}}

    CHE_TEMPLATE=${CHE_TEMPLATE:-${DEFAULT_CHE_TEMPLATE}}

    CHE_APPLICATION_NAME=che-host
}

check_prerequisites() {
    echo "Checking prerequisites"
    # oc must be installed
    command -v oc >/dev/null 2>&1 || { echo >&2 "I require oc but it's not installed.  Aborting."; exit 1; }

    # there should be a service account called cheserviceaccount
    oc get serviceaccounts cheserviceaccount >/dev/null 2>&1 || { echo >&2 "Command 'oc get serviceaccounts cheserviceaccount' failed. A serviceaccount named cheserviceaccount should exist. Aborting."; exit 1; }
    
    # docker must be installed
    command -v docker >/dev/null 2>&1 || { echo >&2 "I require docker but it's not installed.  Aborting."; exit 1; }
    
    # Check if -v /nonexistantfolder:Z works
    # A workaround is to remove --selinux-enabled option in /etc/sysconfig/docker
    docker create --name openchetest -v /tmp/nonexistingfolder:/tmp:Z docker.io/busybox sh >/dev/null 2>&1 || { echo >&2 "Command 'docker create -v /tmp/nonexistingfolder:/tmp:Z busybox sh' failed. Che won't be able to create workspaces in this conditions. To solve this you can either install the latest docker version or deactivate Docker SELinux option. Aborting."; exit 1; }
    docker rm openchetest >/dev/null 2>&1

}

## Intstall eclipse-che template (download the json file from github if not found locally)
install_template() {
    echo "Installing template"
    if [ ! -f ${CHE_TEMPLATE} ]; then
        echo "Template not found locally. Downloading from internet"
        TEMPLATE_URL=https://raw.githubusercontent.com/redhat-developer/rh-che/master/os-templates/che.json
        curl -sSL ${TEMPLATE_URL} > ${CHE_TEMPLATE}
        echo "${TEMPLATE_URL} downladed"
    fi
    oc create -f ${CHE_TEMPLATE} >/dev/null 2>&1 || oc replace -f ${CHE_TEMPLATE} >/dev/null 2>&1
    echo "Template installed"
}

## Install pvc
install_pvc() {
    echo "Installing PVC"
    PVC1=../os-templates/pvc-checonf.yaml
    oc create -f $PVC1 >/dev/null 2>&1 || true
    PVC2=../os-templates/pvc-chedata.yaml
    oc create -f $PVC2 >/dev/null 2>&1 || true
    echo "PVCs installed"
}


## Create a new app based on `eclipse_che` template and deploy it
deploy() {
    echo "Deploying Che"
    echo "calling oc new-app"
    oc new-app --template=eclipse-che --param=APPLICATION_NAME=${CHE_APPLICATION_NAME} \
                                    --param=HOSTNAME_HTTP=${CHE_HOSTNAME} \
                                    --param=CHE_SERVER_DOCKER_IMAGE=${CHE_IMAGE} \
                                    --param=CHE_LOG_LEVEL=${CHE_LOG_LEVEL}
    
    echo "OPENCHE: Waiting 5 seconds for the pod to start"
    sleep 5
    POD_ID=$(oc get pods | grep ${CHE_APPLICATION_NAME} | grep -v "\-deploy" | awk '{print $1}')
    echo "Che pod starting (id $POD_ID)..."
}

## Uninstall everything
delete() {
    echo "Deleting resources"
    # POD_ID=$(oc get pods | grep ${CHE_APPLICATION_NAME} | awk '{print $1}')
    # oc delete pod/${POD_ID}
    # oc delete route/${CHE_APPLICATION_NAME} || true
    # oc delete svc/${CHE_APPLICATION_NAME} || true
    # oc delete dc/${CHE_APPLICATION_NAME} || true
    # oc delete pvc claim-che-conf || true
    # oc delete pvc claim-che-data || true
    oc -n eclipse-che delete dc,route,svc,po,pvc --all || true
}

parse_command_line () {
  if [ $# -eq 0 ]; then
    usage
    exit
  fi

  case $1 in
    deploy|delete)
      ACTION=$1
    ;;
    -h|--help)
      usage
      exit
    ;;
    *)
      # unknown option
      echo "ERROR: You passed an unknown command line option."
      exit
    ;;
  esac
}

usage () {
  USAGE="Usage: ${0} [COMMAND]
     deploy                             Install and deploys che-host Application on OpenShift
     delete                             Delete che-host related objects from OpenShift
"
  printf "%s" "${USAGE}"
}

set -e
set -u

set_parameters
check_prerequisites
parse_command_line "$@"

case ${ACTION} in
  deploy)
    install_pvc
    install_template
    deploy
  ;;
  delete)
    delete
  ;;
esac
