#!/bin/bash
# stop the script when any command fails
set -e

function printHelp {
	YELLOW="\\033[93;1m"
	WHITE="\\033[0;1m"
	GREEN="\\033[32;1m"
	NC="\\033[0m" # No Color
	
	echo -e "${WHITE}Script for testing route time exposure and route flapping. "
	echo -e "${GREEN}Mandatory parameters:"
	echo -e "	$WHITE -u $NC username for login to openshift"
	echo -e "	$WHITE -p $NC password for login to openshift"
	echo -e "   $WHITE -f $NC file with route definition in yml format"
}

function checkPrereq {
	if [[ -z $USERNAME || -z $PASSWORD ]]; then
		echo "Username and password must be set."
		exit 1
	fi
	if [[ -z $FILE ]]; then
		echo "Specify file with route definition."
		exit 1
	fi

	# Install dependecies if needed
	CURRENT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
	if ! command -v jq; then
		if ! type installJQ; then
			source $CURRENT_DIR/../../.ci/functional_tests_utils.sh
		fi
		installJQ
	fi
	if ! command -v oc; then
		if ! type installOC; then
			source $CURRENT_DIR/../../.ci/functional_tests_utils.sh
		fi
		installOC
	fi
}

# Parse commandline flags
while getopts ':f:hp:u:' option; do
    case "$option" in
    	h) printHelp
    	   exit 1
    	   ;;
        u) USERNAME=$OPTARG
           ;;
        p) PASSWORD=$OPTARG
           ;;
        f) FILE=$OPTARG
    esac
done

checkPrereq

echo ---------- Get cluster for user --------------------

if [[ $USERNAME = *"preview"* ]]; then
  API_SERVER_URL="https://api.prod-preview.openshift.io"
else
  API_SERVER_URL="https://api.openshift.io"
fi

OC_CLUSTER_URL=$(curl -s -X GET --header 'Accept: application/json' "$API_SERVER_URL/api/users?filter\\[username\\]=$USERNAME" | jq '.data[0].attributes.cluster')
OC_CLUSTER_URL="$(echo "${OC_CLUSTER_URL//\"/}")"

echo "Using cluster $OC_CLUSTER_URL"

echo ---------- Login -----------------------------------
oc login -u $USERNAME -p $PASSWORD "$OC_CLUSTER_URL"

echo ---------- Change to $USERNAME project ---------------
echo "oc project ${USERNAME}-che"
oc project $USERNAME-che

echo ---------- List all routes ----------------------
echo "oc get route"
oc get route

echo ---------- Create route ----------------------
echo "oc apply -f $FILE"
oc apply -f $FILE

echo ---------- Get route host ----------------------
echo "oc get route -o jsonpath='{.items[0].spec.host}'"
ROUTE_URL=`oc get route -o jsonpath='{.items[0].spec.host}'`
echo "Route URL is " $ROUTE_URL

echo ---------- Wait for route to be available ----------------------
# do stop the script when any command fails
set +e
hard_failed=true
soft_failed=true
function wait_for_route {
    i=1
    hard_timeout=150 #in seconds
    soft_timeout=30  #in seconds
    current=$(date +%s)
    hard_endtime=$((current + hard_timeout))
    soft_endtime=$((current + soft_timeout))
    while [ $(date +%s) -lt $hard_endtime ]; do
        RESPONSE_CODE=`curl -sL -w "%{http_code}" -I $ROUTE_URL -o /dev/null`
        if [ ! $RESPONSE_CODE -eq 200 ]; then
            sleep 1
        else
            if [ $(date +%s) -lt $soft_endtime ]; then
                soft_failed=false
            fi
            hard_failed=false
            break
        fi
    done
}
time wait_for_route

if ! $hard_failed; then
	echo ---------- Test route flapping ----------------------
	i=0
	max=20
	flapping_found=false
	while [ $i -le $max ]; do
		RESP=`curl -sL -w "%{http_code}" -I $ROUTE_URL -o /dev/null`
		if [ $RESP != 200 ]; then
			flapping_found=true
		fi
		echo $RESP
		((i++))
		sleep 1
	done
fi

echo ---------- Remove route ----------------------
echo "oc delete -f $FILE"
oc delete -f $FILE

if $hard_failed; then
	echo
	echo "---------------- HARD FAIL ---------------------"
	echo "Route was not available. Waiting for $hard_timeout seconds."
	exit 1
fi
if $soft_failed; then
	echo
	echo "------------------- SOFT FAIL ---------------------"
	echo "It took more than $soft_timeout seconds for the route to start."
	exit 1
fi
if $flapping_found; then
	echo
	echo "------------------- FLAPPING FAIL ---------------------------"
	echo "The route flapping was present after the route was available."
	exit 1
fi

