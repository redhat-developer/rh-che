#!/bin/bash

# this script downloads runs the build
# to create the che binaries

. config 

mvnche() {
  which scl 2>/dev/null
  if [ $? -eq 0 ]
  then
    if [ `scl -l 2> /dev/null | grep rh-maven33` != "" ]
    then
      # gulp-cli is needed to build the dashboard
      scl enable rh-nodejs4 "npm install --global gulp-cli"
      scl enable rh-maven33 rh-nodejs4 "mvn $*"
    else
      mvn $*
    fi
  else
    mvn $*
  fi

}

mkdir $NPM_CONFIG_PREFIX 2>/dev/null
mvnche -B $* clean install
if [ $? -ne 0 ]; then
  echo "Error building che/rh-che with dashboard"
  exit 1;
fi

if [ "$DeveloperBuild" != "true" ]
  then
    mvnche -B -DwithoutDashboard -pl=:fabric8-ide-assembly-ide-war,:fabric8-ide-assembly-main $* install
    if [ $? -ne 0 ]; then
      echo "Error building che/rh-che without dashboard"
      exit 1;
    fi
fi
