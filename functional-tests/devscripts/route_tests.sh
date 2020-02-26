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
	echo -e "   $WHITE -u $NC username for login to openshift"
	echo -e "   $WHITE -p $NC password for login to openshift"
	echo -e "   $WHITE -f $NC file with route definition in yml format"
	echo -e "${YELLOW}Optional parameters:"
	echo -e "   $WHITE -z $NC send data to zabbix, default set to true"
	echo -e "   $WHITE -s $NC set zabbix server, default zabbix.devshift.net"
	echo -e "   $WHITE -t $NC set zabbix host, default set by cluster"
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
		
	if $SEND_TO_ZABBIX; then
		rpm -ivh https://repo.zabbix.com/zabbix/3.0/rhel/7/x86_64/zabbix-release-3.0-1.el7.noarch.rpm
		yum install -y --nogpgcheck zabbix-sender
	fi
}

# Setting default ZABBIX values
SEND_TO_ZABBIX=true
ZABBIX_SERVER="zabbix.devshift.net"

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
           ;;
        s) ZABBIX_SERVER=$OPTARG
           ;;
        t) ZABBIX_HOST=$OPTARG
           ;;
        z) SEND_TO_ZABBIX=$OPTARG
           ;;
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

ZABBIX_TIMESTAMP=$(date +%s) # time when test starts
start_time=$(date +%s)
wait_for_route
end_time=$(date +%s)
exposure_time=$(($end_time - $start_time))

if ! $hard_failed; then
	printf "Time taken for route to be available: %.3f seconds. \n" $exposure_time
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

echo "--------------- EVENTS -----------------"
oc get events

if $SEND_TO_ZABBIX; then
    echo "--------------- Send time to Zabbix -----------------"
    ZABBIX_HOST=""
    case "$OC_CLUSTER_URL" in
        *"2a"*) ZABBIX_HOST="qa-starter-us-east-2a"
                ;;
        *"1a"*) ZABBIX_HOST="qa-starter-us-east-1a"
	            ;;
        *"1b"*) ZABBIX_HOST="qa-starter-us-east-1b"
                ;;
        *"2."*) ZABBIX_HOST="qa-starter-us-east-2"
	           ;;
        *) echo "WARNING - DATA NOT SENT: can not send data to zabbix: can not extract ZABBIX_HOST from OC_CLUSTER_URL. OC_CLUSTER_URL: $OC_CLUSTER_URL"
	       ;;
    esac
    if [ ! -z $ZABBIX_HOST ]; then
        touch report.txt
        if $hard_failed; then
            echo "$ZABBIX_HOST hard_fail $ZABBIX_TIMESTAMP 1" > report.txt
            echo "$ZABBIX_HOST flapping_fail $ZABBIX_TIMESTAMP 0" >> report.txt
            echo "$ZABBIX_HOST soft_fail $ZABBIX_TIMESTAMP 0" >> report.txt
        else
            echo "$ZABBIX_HOST route_exposure_time $ZABBIX_TIMESTAMP $exposure_time" > report.txt
	        echo "$ZABBIX_HOST hard_fail $ZABBIX_TIMESTAMP 0" >> report.txt
            if $soft_failed; then
                echo "$ZABBIX_HOST soft_fail $ZABBIX_TIMESTAMP 1" >> report.txt
            else
                echo "$ZABBIX_HOST soft_fail $ZABBIX_TIMESTAMP 0" >> report.txt
            fi
            if $flapping_found; then
                echo "$ZABBIX_HOST flapping_fail $ZABBIX_TIMESTAMP 1" >> report.txt
            else 
                echo "$ZABBIX_HOST flapping_fail $ZABBIX_TIMESTAMP 0" >> report.txt
            fi
        fi
        zabbix_sender -vv -T -i report.txt -z $ZABBIX_SERVER
    fi
fi

echo ---------- Remove route ----------------------
echo "oc delete -f $FILE"
oc delete -f $FILE

if $hard_failed; then
	echo
	echo "---------------- HARD FAIL ---------------------"
	echo "Route was not available. Waiting for $hard_timeout seconds."
else
    if $soft_failed; then
        echo
        echo "------------------- SOFT FAIL ---------------------"
        echo "It took more than $soft_timeout seconds for the route to start."
        echo "Route was available after $exposure_time seconds."
    fi
    if $flapping_found; then
        echo
        echo "------------------- FLAPPING FAIL ---------------------------"
        echo "The route flapping was present after the route was available."
        echo "For more information see section \"Test route flapping\" above."
    fi
fi

if $hard_failed || $soft_failed || $flapping_found; then
    exit 1
fi
