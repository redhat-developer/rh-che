# Eclipse Che on OpenShift 

## Table Of Content

* [What is the RedHat Che distribution](#what-is-the-redhat-che-distribution)
* [How to build it](#how-to-build-it)
* [How to deploy or roll-update it in Minishift](#how-to-deploy-or-roll-update-it-in-minishift)
* [Further details](#further-details)

## What is the RedHat Che distribution

The RedHat distribution of Eclipse Che is a RedHat-specific packaging of Che assemblies
that adds some RedHat specific plugins / behaviors up to the standard upstream Che
distribution. It is currently based on the `openshift-connector-wip` branch of the upstream
Che repository. The RedHat distribution powers [openshift.io](https://openshift.io) developers workspaces.

RedHat modifications against the upstream Che include:
- The ability to disable the Dashboard (and remove the *Go to Dashboard* button from the Che IDE)
- Keycloak integration
- [fabric8-analytics Language Server](https://github.com/fabric8-analytics/fabric8-analytics-lsp-server) 

## How to build it

### Build prerequisites

* Install Che development prerequisites
* Clone the upstream Che dependencies git repository and checkout the `openshift-connector-wip` branch:

```bash
git clone https://github.com/eclipse/che-dependencies
cd che-dependencies
git checkout openshift-connector-wip
```
* You should have built the upstream Che dependencies at least once. In the `che-dependencies` git repository,
run the following command:

```bash
mvn install  
```

* Clone the upstream Che git repository and checkout the `openshift-connector-wip` branch:

```bash
git clone https://github.com/eclipse/che
cd che
git checkout openshift-connector-wip
```
* You should have built the upstream Che at least once. In the che git repository,
run the following command:

```bash
mvn -Pfast install  
```

* Set some environment variables:
    * `UPSTREAM_CHE_PATH` points to the local directory where the upstream Che repo was checked out
    and is *mandatory*
    
    ```bash
    export UPSTREAM_CHE_PATH="/Users/mariolet/Github/che"
    ```
    
    * `DOCKER_HUB_NAMESPACE` can be overridden to point
    to your own Docker Hub account
    
    ```bash
    export DOCKER_HUB_NAMESPACE=myDockerNamspace
    ```

    The docker namespace used by default is `docker.io/rhchestage`
    
### Build RedHat distribution

```bash
dev-scripts/build_fabric8.sh [PARAMETERS]
```

This script:
- runs the RH maven build with the options passed as arguments,
- generates the docker images for the upstream and the RH
distribution and tag them appropriately.

### Build scripts parameters

The optional build scripts parameters are the [parameters of the underlying Maven build](#maven-build-parameters).

Most useful parameters are:
- `-Dfast` to skip tests and various verifications
- `-DmultiThread` to enable multi-threaded maven builds
- `-Dche-fork`, `-Dche-branch`, `-Dche-dependencies-fork`,
`-Dche-dependencies-branch` to change the forks/branches used for the
upstream Che repositories.
- `-Dpl` and `-Damd` to build only some modules of the RedHat Che distribution
- `clean` as the optional last argument of the build to run `clean` before the `install` 

### Update Upstream Che Openshift Connector

To rebuild only the Openshift connector related plugins in the upstream Che,
and update the RedHat distribution accordingly, you can use one the following scripts: 

```bash
dev-scripts/update_openshift_connector.sh [PARAMETERS]
```

This script:
- runs the upstream Che maven build on the openshift connector related modules
- updates the RH distribution by running RH maven build with the options passed
as arguments,
- generates the docker images for the upstream and the RH distribution 
and tag them appropriately.

The available parameters are the same as for the `build_fabric8.sh` script.

### Examples

- To clean/rebuild *only* the Bayesian language server, without any validation
check or test, use the following command:

```bash
dev-scripts/build_fabric8_and_deploy.sh -Dfast -Dpl="plugins/che-plugin-bayesian-lang-server" -Damd clean
```

- If your upstream Che repository is on the "myFixBranch" git branch,
to rebuild the upstream openshift connector integration, and have the
RedHat distribution updated accordingly, use the following command:

```bash
dev-scripts/update_openshift_connector.sh -Dfast -Dche.branch="myFixBranch" clean
```

### Docker scripts to create and tag images from the RH build artifacts 

##### In the local docker environment

```bash
dev-scripts/docker_create_images_and_tag.sh
```
__Note:__ This step is already included in the build scripts.
However, if you plan to deploy or rollupdate to Minishift, you should also create / tag docker images
in the Minishift docker environment, as detailed in next section. 

##### In the Minishift docker environment

```bash
dev-scripts/docker_create_images_and_tag_in_minishift.sh
```

## How to deploy or roll-update it in Minishift
    
### Deployment prerequisites

* Get [minishift](https://github.com/minishift/minishift#installation) (we have tested with v1.0.0beta4)
  * Minishift v1.0.0beta4 includes the `oc` binary, and this should be added to your `$PATH`. The binary is located
    in the `.minishift/cache/oc/<oc version>/` directory (e.g. on Linux, it is in `~/.minishift/cache/oc/v1.4.1/`).
* Get [gofabric8](https://github.com/fabric8io/gofabric8#getting-started) (we have tested with v0.4.121)
* Clone fabric8-online git repository:

`git clone https://github.com/fabric8io/fabric8-online.git`

* The scripts assume that the `fabric8-online` repository is cloned in `${HOME}/github/fabric8-online/`.
If you cloned it in another location, you need to override the `FABRIC8_ONLINE_PATH` variable
with the full path of the cloned `fabric8-online` git repository :

```bash
export FABRIC8_ONLINE_PATH="<path where you cloned it>/fabric8-online/"
```
__Important note:__ the `FABRIC8_ONLINE_PATH` should always end with a `/`.

* For all the scripts that deploy or rollout to Minishift, you should have minishift running.
This can be done with the following command:

```bash
minishift start
```

#### Deploy to Minishift 

```bash
dev-scripts/minishift_deploy.sh
```
By default, the docker image of the deployed to Minishift is the last built `nightly-fabric8` image.

However, you can override this by providing the `CHE_IMAGE_TAG` variable:

```bash
bash -c "export CHE_IMAGE_TAG=nightly ; dev-scripts/minishift_deploy.sh"
```

You can even override the name of the docker image with the `CHE_IMAGE_REPO` variable:

```bash
bash -c "export CHE_IMAGE_REPO=rhche/che-server ; export CHE_IMAGE_TAG=nightly ; dev-scripts/minishift_deploy.sh"
```

By default, Che is deployed on minishift with the Keycloak integration *disabled*.

However, to explicitly enable the Keycloak integration, you can override the `CHE_KEYCLOAK_DISABLED` variable:

```bash
bash -c "export CHE_KEYCLOAK_DISABLED=false ; dev-scripts/minishift_deploy.sh"
```

__Warning__: If you are deploying the RH distribution build, ensure that you created / tagged the Che docker images 
*in the Minishift docker environment* (see [previous section](#in-the-minishift-docker-environment)).
If you want to build and deploy the RH distribution to Minishift in one go, you can use the
[*all-in-one scripts*](#all-in-one-scripts-for-minishift)

#### Delete all resources and clean up in Minishift

```bash
dev-scripts/minishift_clean.sh
```

#### Roll-update the current Minishift deployment with the up-to-date docker image

```bash
dev-scripts/minishift_rollupdate.sh
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
    - runs `dev-scripts/minishift_clean.sh`
    - runs `dev-scripts/minishift_deploy.sh`
    
- `dev-scripts/minishift_build_fabric8_and_rollupdate.sh [PARAMETERS]`:
    - changes the current docker environment to use 
the minishift docker daemon,
    - runs `dev-scripts/build_fabric8.sh [PARAMETERS]`
    - runs `dev-scripts/minishift_rollupdate.sh`

##### For updating the Upstream Che Openshift Connector
 
- `dev-scripts/minishift_update_openshift_connector_and_deploy.sh [PARAMETERS]`:
    - changes the current docker environment to use
the minishift docker daemon,
    - runs `dev-scripts/update_openshift_connector.sh [PARAMETERS]`
    - runs `dev-scripts/minishift_clean.sh`
    - runs `dev-scripts/minishift_deploy.sh`
    
- `dev-scripts/minishift_update_openshift_connector_and_rollupdate.sh [PARAMETERS]`:
    - changes the current docker environment to use
the minishift docker daemon,
    - runs `dev-scripts/update_openshift_connector.sh [PARAMETERS]`
    - runs `dev-scripts/minishift_rollupdate.sh`

## Further details

### Maven build details

The RedHat Che distribution maven build does the following:
- Checks out the upstream GitHub `che-dependencies` and `che` repositories into folder
`target/export`, based on a given fork (`eclipse` by default) and branch
(by default the `openshift-connector-wip` branch for both `che-dependencies`
and `openshift-connector-wip` repositories),
- Builds the upstream repositories first as a pre-step,
- Then builds the RedHat distribution maven sub-project based on this upstream build.

However, by passing a given property, it is also possible to bypass the checkout and build
of the upstream projects if the upstream che project is present locally on the developer's
machine and has already been fully built by maven. In this case, this local Che repository
is reused, which make the RedHat Che distribution build much faster.

The version of the RedHat Che distribution assembly and dashboard artifacts is derived from
the version of the upstream Che project. For example, if upstream version is:
`5.16.0-SNAPSHOT`,
then the version of the generated RedHat Che distribution will be:
`5.16.0-fabric8-SNAPSHOT`
and the result of the RedHat Che distribution build will be available at the following location:
    
    rh-che/target/builds/fabric8/fabric8-che/assembly/assembly-main/target/eclipse-che-5.16.0-fabric8-SNAPSHOT

Alternatively, if the option to remove the Dashboard has been enabled then the version of the
generated RedHat Che distribution will be:
`5.16.0-fabric8-without-dashboard-SNAPSHOT` .
and the result of the RedHat Che distribution build will be available at the following location:
    
    rh-che/target/builds/fabric8-without-dashboard/fabric8-che/assembly/assembly-main/target/eclipse-che-5.16.0-fabric8-without-dashboard-SNAPSHOT


The build is started by running *maven* in the root of the current git repository,
which is :`rh-che`


#### The 2 use-cases of the maven build

##### CI-oriented maven build (Upstream che + RedHat Che)

This checks out and builds the upstream Che before building the RedHat distribution.

    mvn clean install

To reuse a previously checked-out and built upstream Che, it is possible to explicitely remove
the `checkout-base-profile` profile by adding the `-P '!checkout-base-che'` parameters:
    
    mvn -P '!checkout-base-che' clean install 

##### Developer-oriented maven build

You can also use a local upstream che repository you already have on your machine.
In this case oyu *have to* define the location of this local reporitory with the
`localCheRepository` property:

    mvn -DlocalCheRepository=<root of your local upstream Che Git repo> clean install

#### Maven build parameters

##### Change the forks / branches used for the upstream Che repositories

If you want to use a different fork / branch for the upstream `che` or `che-dependencies`
repositories, you can specify this with the following maven properties:
- `che-fork`
- `che-branch`
- `che-dependencies-fork`
- `che-dependencies-branch`

For example if you want to use the `fix` branch on the `yourname/che` GitHub repository
that contains your fork of Che, you can use the following options:

    -Dche-fork=yourname -Dche-branch=fix


##### Enabling / Disabling the Dashboard

By default the Che Dashboard is part of the RedHat Che distribution.
Howvever it can removed by with the `-DwithoutDashboard` argument

##### Enabling / Disabling the checks and tests

By default the build conyains various validation checks and tests, which make it slow.
You can disable all these checks as well as skip tests with the `-Dfast` argument


##### Enabling / Disabling multi-thread builds

By default the build is single-threaded, to ensure robustness. However, multi-thread builds
(1 thread per core) can be enabled with the `-DmultiThread` argument

##### Build only some modules of the RedHat Che Distribution

The advanced Maven Reactor options described [here](http://books.sonatype.com/mvnref-book/reference/_using_advanced_reactor_options.html)
are also available in the RedHat Che distribution build using the following properties:

- List of reactor projects to build:

`-Dprojects=<comma separated list of modules>` or `-Dpl=<comma separated list of modules>`

- Reactor project to resume the build from:
    
`-Dresume-from=<module to resume from>` of `-Drf=<module to resume from>`

- Also make the projects *required by* the projects specified in the `projects` option:

`-Dalso-make` or `-Dam`

- Also make the projects *that depend on* the projects specified in the `projects` option:

`-Dalso-make-dependents` or `-Damd`

So for example if you only want to rebuild the Keycloak server plugin and have the main assembly
up-to-date, just use the following options:

```
-Dpl=plugins/keycloak-plugin-server -Damd
```

