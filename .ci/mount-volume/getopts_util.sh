#!/usr/bin/env bash

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
WHITE='\033[1;37m'
NC='\033[0m' # No Color    

function printHelp() {
  echo -e "${YELLOW}run_test.sh ${WHITE}[-u <username>] [-p <passwd>] [-r <url>] ${NC}{-v <volume_name>} {-z <zabbix_prefix} {-t <attempt_timeout>} {-i <iterations>} {-f}"
  echo "Check if PVC exists - if not creates it. Then tries to mount a pod to this PVC for number of iterations set."
  echo -e "${YELLOW}Parameters:${NC}"
  echo -e "${WHITE}   -u  username for openshift account ${GREEN}- mandatory"
  echo -e "${WHITE}   -p  password for openshift account ${GREEN}- mandatory"
  echo -e "${WHITE}   -r  url where openshift can be accessed ${GREEN}- mandatory"
  echo -e "$NC   -v  name of PVC (default value \"claim-che-workspace\""
  echo -e "   -z  prefix for Zabbix"
  echo -e "   -t  timeout for test"
  echo -e "   -i  number of attepts to mount a volume"
  echo -e "   -f  fill PVC with a large number of files"
}

function checkParams() {
  FAILED="false"
  if [[ -z $USERNAME ]]; then
    echo "Username is not set. Please use \"-u\" option and pass a username."
    FAILED="true"
  fi
  if [[ -z $PASSWORD ]]; then
    echo "Password is not set. Please use \"-p\" option and set a password."
    FAILED="true"
  fi
  if [[ -z $URL ]]; then
    echo "URL is not set. Please use \"-r\" option and pass a URL."
    FAILED="true"
  fi
  if [[ -z $VOLUME_NAME ]]; then
    export VOLUME_NAME="default-volume-name"
  fi
  if [[ -z $ZABBIX_PREFIX ]]; then
    echo "Zabbix prefix is not set. Please use \"-z\" option and pass a zabbix prefix."
    FAILED="true"
  fi
  if [[ -z $ATTEMPT_TIMEOUT ]]; then
    echo "Attempt timeout not set - using default value 120."
    export ATTEMPT_TIMEOUT=120
  fi
  if [[ -z $ITERATIONS ]]; then
    echo "Number of iterations not set - using default value 5."
    export ITERATIONS=5
  fi
  if [[ "$FAILED" == "true" ]]; then
    exit 1
  fi		
}
