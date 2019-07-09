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

cd rh-che
length=${#USERNAME}

# ------------------------------------------------------------------------------------------
# ------------------------------- GET TOKEN FOR A USER -------------------------------------
# ------------------------------------------------------------------------------------------

echo "Trying to find token for $(echo $USERNAME | cut -c1-3) $(echo $USERNAME | cut -c4-$length)"  
    
#verify environment - if production or prod-preview
#variable preview is used differ between prod and prod-preview urls
if [[ "$USERNAME" == *"preview"* ]]; then
	response=$(curl -s -g -X GET --header 'Accept: application/json' "https://api.prod-preview.openshift.io/api/users?filter[username]=$USERNAME")
	echo $response
	data=$(echo "$response" | jq .data)
	if [ "$data" == "[]" ]; then
		echo -e "${RED}User $USERNAME is not provisoned on prod-preview cluster. Please check settings. ${NC}"
	    exit 1
    fi        
    preview="prod-preview."
else
	response=$(curl -s -g --header 'Accept: application/json' -X GET "https://api.openshift.io/api/users?filter[username]=$USERNAME")
	data=$(echo "$response" | jq .data)
	if [ "$data" == "[]" ]; then
		echo -e "${RED}User $USERNAME is not provisioned on production cluster. Please check settings. ${NC}" 
        exit 1
    fi        
	preview=""
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

#substract active token
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

echo "Starting chromedriver"
chromedriver &

echo "Running Xvfb"
/usr/bin/Xvfb :1 -screen 0 1920x1080x24 +extension RANDR > /dev/null 2>&1 &

x11vnc -display :1.0 > /dev/null 2>&1 &
export DISPLAY=:1.0


# ------------------------------------------------------------------------------------------
#-------------- -------------------- RUN TESTS ---------------------------------------------
# ------------------------------------------------------------------------------------------

echo
echo "**************************************************************"
echo "********* Environment prepared, running Theia tests **********"
echo "**************************************************************"
echo

hostname=$(hostname -I)
echo "You can wath localy using VNC with IP: ${hostname}:0"

if mount | grep 'local_tests'; then
	echo "The local scripts are mounted. Executing local scripts."
	cd local_tests
	pwd
	echo "ls:"
	ls
	rm -rf node_modules dist
	npm --silent i
	echo "Local scripts successfully built."
	ls
else
	cd e2e-saas
fi

echo "Installing upstream dependency."
npm i ../../e2e/

echo "Running tests."
npm run test
