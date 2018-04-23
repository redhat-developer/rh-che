[![Build Status](https://ci.centos.org/buildStatus/icon?job=devtools-rh-che-build-che-credentials-rh-che6)](https://ci.centos.org/view/Devtools/job/devtools-rh-che-build-che-credentials-rh-che6/)

# Eclipse Che on OpenShift 

## Table Of Content

* [What is the Red Hat Che distribution](#what-is-the-redhat-che-distribution)
* [How to build it](#how-to-build-it)
* [How to deploy or roll-update on OpenShift](#how-to-deploy-or-roll-update-che-on-openshift-container-platform-minishift-or-osio)
* [Further details](#further-details)

## What is the Red Hat Che distribution

The Red Hat distribution of Eclipse Che is a Red Hat specific packaging of Che assemblies
that adds some Red Hat specific plugins / behaviors up to the standard upstream Che
distribution. The Red Hat distribution powers [openshift.io](https://openshift.io) developers workspaces.

Red Hat modifications against the upstream Che include:

* The ability to disable the Dashboard (and remove the *Go to Dashboard* button from the Che IDE)
* [fabric8-analytics Language Server](https://github.com/fabric8-analytics/fabric8-analytics-lsp-server)

## How to build it

### Build prerequisites

* Set some environment variables:

  * `DOCKER_HUB_NAMESPACE` can be overridden to point
    to your own Docker Hub account

    ```bash
    export DOCKER_HUB_NAMESPACE=myDockerNamspace
    ```

    The docker namespace used by default is `docker.io/rhchestage`

### Build Red Hat distribution

```bash
dev-scripts/build_fabric8.sh [PARAMETERS]
```

This script:
- runs the RH maven build with the options passed as arguments,
- generates the docker images for the upstream and the RH
distribution and tag them appropriately.

### Build scripts parameters

The optional build scripts parameters are the [parameters of the underlying Maven build](#maven-build-parameters).

### Docker scripts to create and tag images from the RH build artifacts

##### In the local docker environment

```bash
dev-scripts/local_docker_create_images_and_tag.sh
```
__Note:__ This step is already included in the build scripts.
However, if you plan to deploy or rollupdate to Minishift, you should also create/tag docker images
in the Minishift docker environment, as detailed in next section.

##### In the Minishift docker environment

```bash
dev-scripts/minishift_docker_create_images_and_tag.sh
```

## How to deploy or roll-update Che on OpenShift Container Platform, Minishift or OSIO

### Deployment prerequisites

* [minishift](https://github.com/minishift/minishift#installation) (v1.0.0 or greater)
* `oc` the OpenShift command line client tool. The binary is included in minishift distribution and can be found in folder `.minishift/cache/oc/<oc version>/`. You can add this folder to your `$PATH` using the following command `eval $(minishift oc-env)`.

### Deploy to OpenShift Container Platform

```bash
# Configure OpenShift cluster details:
export OPENSHIFT_ENDPOINT=<OCP_ENDPOINT_URL> # e.g. https://opnshmdnsy3t7twsh.centralus.cloudapp.azure.com:8443
export OPENSHIFT_TOKEN=<OCP_TOKEN>
export OPENSHIFT_NAMESPACE_URL=<CHE_HOSTNAME> # e.g. che-eclipse-che.52.173.199.80.xip.io

# Deploy Che
SCRIPT_URL=https://raw.githubusercontent.com/redhat-developer/rh-che/master/dev-scripts/openshift_deploy.sh
export OPENSHIFT_FLAVOR=ocp && curl -fsSL ${SCRIPT_URL} -o get-che.sh && bash get-che.sh
```

And if you have cloned [redhat-developer/rh-che](https://github.com/redhat-developer/rh-che) you can deploy Che on OSIO executing:

```bash
export OPENSHIFT_FLAVOR=ocp && ./dev-scripts/openshift_deploy.sh
```

### Deploy to Minishift

The easiest way to deploy Che on minishift is to use the minishift addon:

* Clone this repository

    ```bash
    git clone https://github.com/redhat-developer/rh-che
    cd rh-che
    ```

* Install rhche-prerequisites minishift addon

    ```bash
    minishift addons install openshift/minishift-addons/rhche-prerequisites
    minishift addons apply rhche-prerequisites
    ```

* Install rhche minishift addon

    ```bash
    minishift addons install openshift/minishift-addons/rhche
    minishift addons apply rhche
    ```

To remove the addon:

```bash
minishift addons remove rhche && minishift addons uninstall rhche
minishift addons remove rhche-prerequisites && minishift addons uninstall rhche-prerequisites
```

### Deploy to openshift.io

```bash
SCRIPT_URL=https://raw.githubusercontent.com/redhat-developer/rh-che/master/dev-scripts/openshift_deploy.sh
export OPENSHIFT_FLAVOR=osio && curl -fsSL ${SCRIPT_URL} -o get-che.sh && bash get-che.sh
```

And if you have cloned [redhat-developer/rh-che](https://github.com/redhat-developer/rh-che) you can deploy Che on OSIO executing:

```bash
export OPENSHIFT_FLAVOR=osio && ./dev-scripts/openshift_deploy.sh
```

### Deployment Options

You can set different deployment options using environment variables:

* `OPENSHIFT_FLAVOR`: possible values are `ocp`, `minishift` and `osio` (default is `minishift`)
* `OPENSHIFT_ENDPOINT`: url of the OpenShift API (default is unset for ocp, `https://$(minishift ip):8443/` for minishift, `https://api.starter-us-east-2.openshift.com` for osio)
* `OPENSHIFT_TOKEN` (default is unset)
* `CHE_OPENSHIFT_PROJECT`: the OpenShift namespace where Che will be deployed (default is `eclipse-che` for ocp and minishift and `${OPENSHIFT_ID}-che` for osio)
* `CHE_IMAGE_REPO`: `che-server` Docker image repository that will be used for deployment (default is `docker.io/rhchestage/che-server`)
* `CHE_IMAGE_TAG`: `che-server` Docker image tag that will be used for deployment (default is `nightly-fabric8`)
* `CHE_LOG_LEVEL`: Log level of che-server (default is `DEBUG`)
* `CHE_DEBUGGING_ENABLED`: If set to `true` the script will create the OpenShift service to debug che-server (default is `true`)
* `CHE_KEYCLOAK_DISABLED`: If this is set to true Keycloack authentication will be disabled (default is `true` for ocp and minishift, `false` for osio)
* `OPENSHIFT_NAMESPACE_URL`: The Che application hostname (default is unset for ocp, `${CHE_OPENSHIFT_PROJECT}.$(minishift ip).nip.io` for minishift, `${CHE_OPENSHIFT_PROJECT}.8a09.starter-us-east-2.openshiftapps.com` for osio)

#### ocp and minishift only options

* `OPENSHIFT_USERNAME`: username to login on the OpenShift cluster. Ignored if `OPENSHIFT_TOKEN` is set (default is `developer`)
* `OPENSHIFT_PASSWORD`: password to login on the OpenShift cluster. Ignored if `OPENSHIFT_TOKEN` is set (default is `developer`)

__Warning__: If you are deploying the RH distribution build, ensure that you created / tagged the Che docker images 
*in the Minishift docker environment* (see [previous section](#in-the-minishift-docker-environment)).
If you want to build and deploy the RH distribution to Minishift in one go, you can use the
[*all-in-one scripts*](#all-in-one-scripts-for-minishift).

### Delete all resources and clean up in Minishift

```bash
dev-scripts/openshift_deploy.sh --command cleanup
```

### Roll-update the current Minishift deployment with the up-to-date docker image

```bash
dev-scripts/openshift_deploy.sh --command rollupdate
```

__Warning__: If you are deploying the RH distribution build, ensure that you created / tagged the Che docker images
[*in the Minishift docker environment*](#in-the-minishift-docker-environment).
If you want to build and deploy the RH distribution to Minishift in one go, you can use the
[*all-in-one scripts*](#all-in-one-scripts-for-minishift)

### All-in-one scripts for Minishift

Instead of running a script for each step in the process of building / deploying to Minisift,
the following all-in-one scripts are also available, and can take the [same arguments as
the build scripts](#build-scripts-parameters)

##### For building the RedHat Che distribution

- `dev-scripts/minishift_build_fabric8_and_deploy.sh [PARAMETERS]`:
    - changes the current docker environment to use
the minishift docker daemon,
    - runs `dev-scripts/build_fabric8.sh [PARAMETERS]`
    - runs `dev-scripts/openshift_deploy.sh --command cleanup`
    - runs `dev-scripts/openshift_deploy.sh`

- `dev-scripts/minishift_build_fabric8_and_rollupdate.sh [PARAMETERS]`:
    - changes the current docker environment to use
the minishift docker daemon,
    - runs `dev-scripts/build_fabric8.sh [PARAMETERS]`
    - runs `dev-scripts/openshift_deploy.sh --command rollupdate`

## Further details

### Maven build details

The result of the RedHat Che distribution build will be available at the following location:

    rh-che/assembly/assembly-main/target/eclipse-che-fabric8-1.0.0-SNAPSHOT

Alternatively, if the option to remove the Dashboard has been enabled then the classifier `without-dashboard`
will be used and the result of the RedHat Che distribution build will be available at the following location:

    rh-che/assembly/assembly-main/target/eclipse-che-fabric8-1.0.0-SNAPSHOT-without-dashboard


The build is started by running *maven* in the root of the current git repository,
which is :`rh-che`


#### Maven build parameters

The build relies on the upstream Eclipse Che maven configuration. All the maven options available in Eclipse Che can be used here.
For instance, the profile `fast` will skip all the tests and checks as describe [in the Eclipse Che development documentation](https://github.com/eclipse/che/wiki/Development-Workflow#build-and-run):

    mvn clean install -Pfast


##### Enabling / Disabling the Dashboard

By default the Che Dashboard is part of the Red Hat Che distribution.
Howvever it can removed by with the `-DwithoutDashboard` argument
