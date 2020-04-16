#!/usr/bin/env bash
set -e

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

#verify environment - if production or prod-preview
#variable preview is used to differ between prod and prod-preview urls
rm -rf cookie-file loginfile.html
if [[ "$USERNAME" == *"preview"* ]] || [[ "$USERNAME" == *"saas"* ]]; then
  preview="prod-preview."
else
  preview=""
fi

response=$(curl -s -g -X GET --header 'Accept: application/json' "https://api.${preview}openshift.io/api/users?filter[username]=$USERNAME")
data=$(echo "$response" | jq .data)
if [ "$data" == "[]" ]; then
  echo -e "${RED}Can not find active token for user $USERNAME. Please check settings. ${NC}"
  exit 1
fi

#get html of developers login page
curl -sX GET -L -c cookie-file -b cookie-file "https://auth.${preview}openshift.io/api/login?redirect=https://che.openshift.io" > loginfile.html

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
  #save each token into file tokens.txt in format: token;username;["","prod-preview"]
  export E2E_SAAS_TESTS_USER_TOKEN=${token}
  echo "Token set successfully."
else
  echo -e "${RED}Failed to obtain token for $USERNAME! Probably user password is incorrect. Continue with other users. ${NC}"
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

echo "Running Xvfb"
/usr/bin/Xvfb :1 -screen 0 1920x1080x24 +extension RANDR > /dev/null 2>&1 &

x11vnc -display :1.0 > /dev/null 2>&1 &
export DISPLAY=:1.0

# ------------------------------------------------------------------------------------------
#--------------------------------- APPLY PATCHES -------------------------------------------
# ------------------------------------------------------------------------------------------

echo "Applying patches..."
# currently in /tmp/rh-che folder

# rebuild upstream patched code
cd ../e2e && npm i && tsc && cd ../rh-che

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

echo "Running test suite: $TEST_SUITE"
npm run $TEST_SUITE
