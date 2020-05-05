#!/usr/bin/env bash
set -e

stopRecording(){
  echo "Killing ffmpeg with PID=$ffmpeg_pid"
  kill -2 "$ffmpeg_pid"
  set +e
  wait "$ffmpeg_pid"
  set -e
  return 0
}

startRecording() {
  ffmpeg_path="./report/ffmpeg_report"
  mkdir -p $ffmpeg_path
  nohup ffmpeg -y -video_size 1920x1080 -framerate 24 -f x11grab -i $DISPLAY.$SCREEN $ffmpeg_path/output.mp4 2> $ffmpeg_path/ffmpeg_err.txt > $ffmpeg_path/ffmpeg_std.txt & 
  ffmpeg_pid=$!
  trap stopRecording 2 15
}

setURLs() {
  if [ -z $ACCOUNT_ENV ]; then
    echo "ACCOUNT_ENV variable is not set, retrieving env from username."

    #verify environment - if production or prod-preview
    #variable preview is used to differ between prod and prod-preview urls
    rm -rf cookie-file loginfile.html
    if [[ "$USERNAME" == *"preview"* ]] || [[ "$USERNAME" == *"saas"* ]]; then
      export USER_INFO_URL="https://api.prod-preview.openshift.io/api/users?filter[username]=$USERNAME"
      export LOGIN_PAGE_URL="https://auth.prod-preview.openshift.io/api/login?redirect=https://che.openshift.io"
    else
      export USER_INFO_URL="https://api.openshift.io/api/users?filter[username]=$USERNAME"
      export LOGIN_PAGE_URL="https://auth.openshift.io/api/login?redirect=https://che.openshift.io"
    fi
  else 
    if [ "$ACCOUNT_ENV" == "prod" ]; then
      export USER_INFO_URL="https://api.openshift.io/api/users?filter[username]=$USERNAME"
      export LOGIN_PAGE_URL="https://auth.openshift.io/api/login?redirect=https://che.openshift.io"
    else 
      export USER_INFO_URL="https://api.prod-preview.openshift.io/api/users?filter[username]=$USERNAME"
      export LOGIN_PAGE_URL="https://auth.prod-preview.openshift.io/api/login?redirect=https://che.openshift.io"
    fi
  fi
}

# ------------------------------------------------------------------------------------------
# ------------------------------- VALIDATE ALL REQUIRED PARAMETERS -------------------------
# ------------------------------------------------------------------------------------------

all_set=true;
if [ -z $USERNAME ]; then
  all_set=false;
  echo "USERNAME is not set";
fi
if [ -z $PASSWORD ]; then
  all_set=false;
  echo "PASSWORD is not set";
fi
if [ -z $URL ]; then
  all_set=false;
  echo "URL is not set";
fi

if ! $all_set; then
  exit 1
else
  echo "All mandatory parameters were set. Running Che 7 Theia E2E tests against $URL with user $USERNAME."
fi

if [ -z $TEST_SUITE ]; then
  TEST_SUITE="test-all"
fi
if [ -z $DEBUG_LEVEL ]; then
  DEBUG_LEVEL="DEBUG"
fi

cd rh-che
length=${#USERNAME}

# ------------------------------------------------------------------------------------------
# ------------------------------- GET TOKEN FOR A USER -------------------------------------
# ------------------------------------------------------------------------------------------

echo "Trying to find token for $(echo $USERNAME | cut -c1-3) $(echo $USERNAME | cut -c4-$length)"

setURLs

response=$(curl -s -g -X GET --header 'Accept: application/json' ${USER_INFO_URL})
data=$(echo "$response" | jq .data)
if [ "$data" == "[]" ]; then
  echo -e "${RED}Can not find active token for user $USERNAME. Please check settings. ${NC}"
  exit 1
fi

#get html of developers login page
curl -sX GET -L -c cookie-file -b cookie-file ${LOGIN_PAGE_URL} > loginfile.html

#get url for login from form
url=$(grep "form id" loginfile.html | grep -o 'http.*.tab_id=.[^\"]*')
dataUrl="username=$USERNAME&password=$PASSWORD&login=Log+in"
url=${url//\&amp;/\&}

#send login and follow redirects
set +e
url=$(curl -w '%{redirect_url}' -s -X POST -c cookie-file -b cookie-file -d "$dataUrl" "$url")
found=$(echo "$url" | grep "token_json")

while true
do
  url=$(curl -c cookie-file -b cookie-file -s -o /dev/null -w '%{redirect_url}' "$url")
  if [[ ${#url} == 0 ]]; then
    #all redirects were done but token was not found
    break
  fi
  found=$(echo "$url" | grep "token_json")
  if [[ ${#found} -gt 0 ]]; then
    #some redirects were done and token was found as a part of url
    break
  fi
done
set -e

#extract active token
token=$(echo "$url" | grep -o "ey.[^%]*" | head -1)
if [[ ${#token} -gt 0 ]]; then
  export E2E_SAAS_TESTS_USER_TOKEN=${token}
  echo "Token set successfully."
else
  echo -e "${RED}Failed to obtain token for $USERNAME! Probably user password is incorrect. ${NC}"
  exit 1
fi

# ------------------------------------------------------------------------------------------
# -------------------- SET ALL NEEDED ENVIRONMENT VARIABLES --------------------------------
# ------------------------------------------------------------------------------------------

export TS_SELENIUM_USERNAME=$USERNAME
export TS_SELENIUM_PASSWORD=$PASSWORD
export TS_SELENIUM_BASE_URL=$URL
export TS_SELENIUM_MULTIUSER=true
export TS_SELENIUM_LOG_LEVEL=$DEBUG_LEVEL

echo "Running Xvfb"

export DISPLAY=:20
export SCREEN="0"

/usr/bin/Xvfb $DISPLAY -screen $SCREEN 1920x1080x16 +extension RANDR > /dev/null 2>&1 &
x11vnc -display $DISPLAY -N -forever > /dev/null 2>&1 &

# Launch selenium server
/usr/bin/supervisord --configuration /etc/supervisord.conf & \
export TS_SELENIUM_REMOTE_DRIVER_URL=http://localhost:4444/wd/hub

# Check selenium server launching
expectedStatus=200
currentTry=1
maximumAttempts=5

while [ $(curl -s -o /dev/null -w "%{http_code}" --fail http://localhost:4444/wd/hub/status) != $expectedStatus ];
do
  if (( currentTry > maximumAttempts ));
  then
    status=$(curl -s -o /dev/null -w "%{http_code}" --fail http://localhost:4444/wd/hub/status)
    echo "Exceeded the maximum number of checking attempts,"
    echo "selenium server status is '$status' and it is different from '$expectedStatus'";
    exit 1;
  fi;

  echo "Wait selenium server availability ..."

  curentTry=$((curentTry + 1))
  sleep 1
done


# ------------------------------------------------------------------------------------------
#----------------------------------- RUN TESTS ---------------------------------------------
# ------------------------------------------------------------------------------------------

echo
echo "**************************************************************"
echo "********* Environment prepared, running Theia tests **********"
echo "**************************************************************"
echo

hostname=$(hostname -I)
echo "You can watch localy using VNC with IP: ${hostname}:0"

if mount | grep 'local_tests'; then
  echo "The local scripts are mounted. Executing local scripts."
  cd local_tests
  rm -rf node_modules dist
  #When mounting local code, there can be local dependency to e2e set - this would cause script to fail.
  sed -i '/e2e/d' package.json
  npm --silent i
  echo "Local scripts successfully built."
else
  cd e2e-saas
fi

echo "Installing upstream dependency."
npm --silent i ../../e2e/

startRecording

set +e
echo "Running test suite: $TEST_SUITE"
npm run $TEST_SUITE
RESULT=$?
set -e

stopRecording

if [[ $RESULT == 0 ]]; then
  rm -rf $ffmpeg_path
fi
echo "Exiting docker entrypoint with status $RESULT"
exit $RESULT
