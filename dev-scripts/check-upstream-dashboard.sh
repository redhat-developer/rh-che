#!/bin/bash

set -e

RED="\e[31m"
GREEN="\e[32m"
RESET="\e[39m"

function generate_diff() {
  mvn -q clean dependency:unpack-dependencies@extract-upstream-sources antrun:run@diff-source-trees -pl ':fabric8-ide-dashboard-war'
}

function check_for_changes() {
  CUR=$(grep -v -e "^Only in" -e "\+\+\+" -e "---" tmp/current.diff)
  NEW=$(grep -v -e "^Only in" -e "\+\+\+" -e "---" tmp/new.diff)
  diff <(echo "$CUR") <(echo "$NEW") > /dev/null
}

function print_changed_files() {
  if command -v interdiff > /dev/null; then
    echo -e "$RED"
    interdiff tmp/current.diff tmp/new.diff | grep diff | cut -f 4 -d ' '
    echo -e "$RESET"
  else
    echo "Printing changed files depends on program 'interdiff'"
  fi
}

function cleanup() {
  echo "Executing cleanup"
  git checkout -- .
  rm -rf ./tmp
}

if ! git diff-index --quiet HEAD --; then
  echo "Git repository has uncommitted changes. Aborting"
  exit 1
fi

trap cleanup EXIT

NEW_VER=$1
if [ -z "$NEW_VER" ]; then
  echo "Usage: check_dashboard.sh \$NEW_VERSION"
  exit 1
fi

echo "Testing update to version ${NEW_VER}"
mkdir -p tmp

# Get current diff against upstream source tree
echo "Getting dashboard source diff for current Che parent version"
generate_diff
cp assembly/fabric8-ide-dashboard-war/target/src_tree.diff tmp/current.diff

# Update project parent to new version
echo "Updating parent version in pom.xml to ${NEW_VER}"
NEW_POM=$(xq --arg VER "$NEW_VER" '.project.parent.version = $VER' pom.xml -x)
echo "${NEW_POM}" > pom.xml

# Get diff for updated parent version
echo "Getting dashboard source diff for Che parent version ${NEW_VER}"
generate_diff
cp assembly/fabric8-ide-dashboard-war/target/src_tree.diff tmp/new.diff

if ! check_for_changes; then
  echo -e "${RED}Upstream changes detected. Changed files:${RESET}"
  print_changed_files
else
  echo -e "${GREEN}No upstream changes detected.${RESET}"
fi

