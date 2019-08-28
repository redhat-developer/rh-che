#!/usr/bin/env bash

RED='\033[0;31m'
GREEN='\033[0;32m'
NC='\033[0m' # No Color

tokens_file=$1
IFS=$'\n;' # make newlines the only separator

while read -r line || [[ -n $line ]]
do
  username=$(echo "$line" | cut -d";" -f 1)
  password=$(echo "$line" | cut -d";" -f 2)
  environ=$(echo "$line" | cut -d";" -f 3)
  echo "Trying to find token for $username"

  #verify environment - if production or prod-preview
  #variable preview is used differ between prod and prod-preview urls
  if [ "$environ" == "prod" ]; then
    response=$(curl -s --header 'Accept: application/json' -X GET "https://api.openshift.io/api/users?filter[username]=$username")
    data=$(echo "$response" | jq .data)
    if [ "$data" == "[]" ]; then
      >&2 echo -e "${RED}User $username is not provisoned on $environ cluster. Please check settings. Skipping user.${NC}"
      continue
    fi
    preview=""
  else
    response=$(curl -s --header 'Accept: application/json' -X GET "https://api.prod-preview.openshift.io/api/users?filter[username]=$username")
    data=$(echo "$response" | jq .data)
    if [ "$data" == "[]" ]; then
      >&2 echo -e "${RED}User $username is not provisioned on $environ cluster. Please check settings. Skipping user.${NC}" 
      continue
    fi
    preview="prod-preview."
  fi

  #get html of developers login page
  loginpage=$(curl -sX GET -L -c cookie-file -b cookie-file "https://auth.${preview}openshift.io/api/login?redirect=https://che.openshift.io")

  #get url for login from form
  url=$(echo "$loginpage" | grep "form id" - | grep -o 'http.*.tab_id=.[^\"]*')
  dataUrl="username=$username&password=$password&login=Log+in"
  url=${url//\&amp;/\&}

  #send login and follow redirects
  url=$(curl -w '%{redirect_url}' -s -X POST -c cookie-file -b cookie-file -d "$dataUrl" "$url")
  found=$(echo "$url" | grep "token_json")

  while true
  do
    url=$(curl -c cookie-file -b cookie-file -s -o /dev/null -w '%{redirect_url}' "$url")
    if [[ ${#url} == 0 ]]; then
      #all redirects were done but token was not found
      >&2 echo "No token found after following all redirects"
      break
    fi
    found=$(echo "$url" | grep "token_json")
    if [[ ${#found} -gt 0 ]]; then
      #some redirects were done and token was found as a part of url
      break
    fi
  done

  #substract active token
  token=$(echo "$url" | grep -o "ey.[^%]*" | head -1)
  if [[ ${#token} -gt 0 ]]; then
  #save each token into file tokens.txt in format: token;username;["","prod-preview"]
    echo "$token;$username;$environ" >> "$tokens_file"
    echo -e "${GREEN}Token for user $username was found successfully.${NC}"
  else
    >&2 echo -e "${RED}Failed to obtain token for $username! Probably user password is incorrect. Continue with other users. ${NC}"
  fi
  token=""
  rm cookie-file
done < "${USERS_PROPERTIES_FILE}"
