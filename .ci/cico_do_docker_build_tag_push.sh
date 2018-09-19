#!/bin/bash
# Copyright (c) 2017 Red Hat, Inc.
# All rights reserved. This program and the accompanying materials
# are made available under the terms of the Eclipse Public License v1.0
# which accompanies this distribution, and is available at
# http://www.eclipse.org/legal/epl-v10.html

currentDir=`pwd`
ABSOLUTE_PATH="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

. ${ABSOLUTE_PATH}/../config

RH_CHE_TAG=$(git rev-parse --short HEAD)

UPSTREAM_TAG=$(sed -n 's/^SCM-Revision: \(.\{7\}\).*/\1/p' ${ABSOLUTE_PATH}/../assembly/assembly-wsmaster-war/target/war/work/org.eclipse.che/assembly-wsmaster-war/META-INF/MANIFEST.MF)

# Now lets build the local docker images

ADDONS=${ABSOLUTE_PATH}/../openshift/minishift-addons

DIR=${ABSOLUTE_PATH}/../dockerfiles/che-fabric8
cd ${DIR}

distPath='assembly/assembly-main/target/eclipse-che-*/eclipse-che-*'
for distribution in `echo ${ABSOLUTE_PATH}/../${distPath}`; do
  case "$distribution" in
    ${ABSOLUTE_PATH}/../assembly/assembly-main/target/eclipse-che-${RH_DIST_SUFFIX}-*${RH_NO_DASHBOARD_SUFFIX}*)
      TAG=${UPSTREAM_TAG}-${RH_TAG_DIST_SUFFIX}-no-dashboard-${RH_CHE_TAG}
      NIGHTLY=nightly-${RH_TAG_DIST_SUFFIX}-no-dashboard
      if [ "$PR_CHECK_BUILD" == "true" ]; then
        TAG=${RH_TAG_DIST_SUFFIX}-no-dashhoard-${RH_PULL_REQUEST_ID}
      fi
      ;;
    ${ABSOLUTE_PATH}/../assembly/assembly-main/target/eclipse-che-${RH_DIST_SUFFIX}*)
      TAG=${UPSTREAM_TAG}-${RH_TAG_DIST_SUFFIX}-${RH_CHE_TAG}
      NIGHTLY=nightly-${RH_TAG_DIST_SUFFIX}
      if [ "$PR_CHECK_BUILD" == "true" ]; then
        TAG=${RH_TAG_DIST_SUFFIX}-${RH_PULL_REQUEST_ID}
      fi
      # File che_image_tag.env will be used by the verification script to
      # retrieve the image tag to promote to production. That's the only
      # mechanism we have found to share the tag amongs the two scripts
      echo 'export CHE_SERVER_DOCKER_IMAGE_TAG='${TAG} >> ~/che_image_tag.env
      ;;
  esac

  # Use of folder
  LOCAL_ASSEMBLY_DIR="${DIR}"/eclipse-che

  if [ -d "${LOCAL_ASSEMBLY_DIR}" ]; then
    rm -r "${LOCAL_ASSEMBLY_DIR}"
  fi

  echo "Copying assembly ${distribution} --> ${LOCAL_ASSEMBLY_DIR}"
  cp -r "${distribution}" "${LOCAL_ASSEMBLY_DIR}"

  if [ "$DeveloperBuild" != "true" ] || [ "$PR_CHECK_BUILD" == "true" ]; then
    if [ -n "${QUAY_USERNAME}" -a -n "${QUAY_PASSWORD}" ]; then
      docker login -u "${QUAY_USERNAME}" -p "${QUAY_PASSWORD}" ${REGISTRY}
    else
      echo "ERROR: Can not push to ${REGISTRY}: credentials are not set. Aborting"
      exit 1
    fi
  fi

  docker build -t ${DOCKER_IMAGE_URL}:${TAG} -f $DIR/${DOCKERFILE} .
  if [ $? -ne 0 ]; then
    echo 'Docker Build Failed'
    exit 2
  fi

  # lets change the tag and push it to the registry
  docker tag ${DOCKER_IMAGE_URL}:${TAG} ${DOCKER_IMAGE_URL}:${NIGHTLY}

  dockerTags="${dockerTags} ${REGISTRY}/${NAMESPACE}/${DOCKER_IMAGE}:${NIGHTLY} ${REGISTRY}/${NAMESPACE}/${DOCKER_IMAGE}:${TAG}"

  if [ "$DeveloperBuild" != "true" ]; then
      docker push ${DOCKER_IMAGE_URL}:${NIGHTLY}
      docker push ${DOCKER_IMAGE_URL}:${TAG}
  fi

  if [ "${NIGHTLY}" != "*-no-dashboard" ] && [ "$PR_CHECK_BUILD" != "true" ]; then
      docker build -t ${KEYCLOAK_DOCKER_IMAGE_URL}:${TAG} $ADDONS/rhche-prerequisites/keycloak-configurator

      if [ $? -ne 0 ]; then
        echo 'Docker Build Failed'
        exit 2
      fi

      # lets change the tag and push it to the registry
      docker tag ${KEYCLOAK_DOCKER_IMAGE_URL}:${TAG} ${REGISTRY}/${NAMESPACE}/${KEYCLOAK_STANDALONE_CONFIGURATOR_IMAGE}:${NIGHTLY}

      dockerTags="${dockerTags} ${REGISTRY}/${NAMESPACE}/${KEYCLOAK_STANDALONE_CONFIGURATOR_IMAGE}:${NIGHTLY} ${REGISTRY}/${NAMESPACE}/${KEYCLOAK_STANDALONE_CONFIGURATOR_IMAGE}:${TAG}"

      if [ "$DeveloperBuild" != "true" ]; then
          docker push ${KEYCLOAK_DOCKER_IMAGE_URL}:${NIGHTLY}
          docker push ${KEYCLOAK_DOCKER_IMAGE_URL}:${TAG}
      fi
  fi
done

if [ "${DOCKER_HOST}" == "" ]; then
  dockerEnv="local Docker environment"
else
  dockerEnv="following Docker environment: ${DOCKER_HOST}"
fi

echo "!"
echo "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!"
echo "!"
echo "! Created / tagged the following eclipse Che images:"
echo "!     ${dockerTags}"
echo "! in the ${dockerEnv}"
echo "!"
echo "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!"
echo "!"

