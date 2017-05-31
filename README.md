# Eclipse Che on OpenShift 

## Table Of Content

* [What is the RedHat Che distribution](#what-is-the-redhat-che-distribution)
* [RedHat Che distribution Maven build](#redhat-che-distribution-maven-build)
* [Developer-friendly scripts to build and deploy or rollout to minishift](#developer-friendly-scripts-to-build-and-deploy-or-rollout-to-minishift)

## TL;DR

There is a good chance that you want to build rh-che with the dashboard but without keycloak support (because it's easier to test) and as fast as possible :bowtie::

```
git clone https://github.com/redhat-developer/rh-che
cd rh-che
dev-scripts/build_fabric8.sh
    -Pfast            `# skip tests and other verifications` \
    -PmultiThread     `# enable maven multi threading` \
    clean
```

If you have already cloned [eclipse/che](https://github.com/eclipse/che) and built [openshift-connector branch](https://github.com/eclipse/che/tree/openshift-connector)
you will need to set the `UPSTREAM_CHE_PATH` variable before :

```
UPSTREAM_CHE_PATH=/path/to/upstream/che
dev-scripts/build_fabric8.sh
    -Pfast                                     `# skip tests and other verifications` \
    -PmultiThread                              `# enable maven multi threading` \
    clean
```

## What is the RedHat Che distribution

The RedHat distribution of Eclipse Che is a RedHat-specific packaging of Che assemblies
that adds some RedHat specific plugins / behaviors up to the standard upstream Che
distribution. It is currently based on the `openshift-connector` branch of the upstream
Che repository. The RedHat distribution powers [openshift.io](https://openshift.io) developers workspaces.

RedHat modifications against the upstream Che include:
- The ability to disable the Dashboard (and remove the *Go to Dashboard* button from the Che IDE)
- Keycloak integration
- [fabric8-analytics Language Server](https://github.com/fabric8-analytics/fabric8-analytics-lsp-server) 


## RedHat Che distribution Maven build

The RedHat Che distribution maven build does the following:
- Checks out the upstream GitHub `che-dependencies` and `che` repositories into folder
`target/export`, based on a given fork (`eclipse` by default) and branch
(`openshift-connector` by default),
- Builds the upstream repositories first as a pre-step,
- Then builds the RedHat distribution maven sub-project based on this upstream build.

However, by passing a given property, it is also possible to bypass the checkout and build
of the upstream projects if the upstream che project is present locally on the developer's
machine and has already been fully built by maven. In this case, this local Che repository
is reused, which make the RedHat Che distribution build much faster.

The version of the RedHat Che distribution assembly and dashboard artifacts is derived from
the version of the upstream Che project. For example, if upstream version is:
`5.6.0-openshift-connector-SNAPSHOT`,
then the version of the generated RedHat Che distribution will be:
`5.6.0-openshift-connector-fabric8-SNAPSHOT`
and the result of the RedHat Che distribution build will be available at the following location:
    
    rh-che/target/builds/fabric8/fabric8-che/assembly/assembly-main/target/eclipse-che-5.6.0-openshift-connector-fabric8-SNAPSHOT

Alternatively, if the option to remove the Dashboard has been enabled then the version of the
generated RedHat Che distribution will be:
`5.6.0-openshift-connector-fabric8-without-dashboard-SNAPSHOT` .
and the result of the RedHat Che distribution build will be available at the following location:
    
    rh-che/target/builds/fabric8-without-dashboard/fabric8-che/assembly/assembly-main/target/eclipse-che-5.6.0-openshift-connector-fabric8-without-dashboard-SNAPSHOT


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

__Notice:__

This is the *recommended way to run the maven build* if you work on both the upstream che and the RedHat distribution.
However *using the [developer-friendly scripts](#developer-friendly-scripts-to-build-and-deploy-or-rollout-to-minishift) should be
*even more convenient* since they also take care of the docker image creation and minishift deployment.
   
    
## Build options and parameters

These options can be used with both:
- the [RedHat Che distribution Maven build](#redhat-che-distribution-maven-build) itself,
- the [developer-friendly scripts](#developer-friendly-scripts-to-build-and-deploy-or-rollout-to-minishift)

##### Change the forks / branches used for the upstream Che repositories

If you want to use a different fork / branch for the `che` or `che-dependencies` repositories,
you can specify this with the following properties:
- `che-fork`
- `che-branch`
- `che-dependencies-fork`
- `che-dependencies-branch`

For example if you want to use the `fix` branch on the `yourname/che` GitHub repository
that contains your fork of Che, you can use the following options:

    -Dche-fork=yourname -Dche-branch=fix


##### Enabling / Disabling the Dashboard

By default the Dashboard is part of the RedHat Che distribution.
Howvever it can removed by adding the following option to the maven command:

    -DwithoutDashboard

##### Enabling / Disabling the Keycloak integration

By default the Keycloak integration is part of the RedHat Che distribution.
Howvever it can removed by adding the following option to the maven command:

    -DwithoutKeycloak

##### Enabling / Disabling the checks and tests

By default the build enables various checks (dependency validation, license check, 
pom sorting, etc ...), and runs the units / integration tests.

However this can be long in development phase, so you can disable these checks as well as
skip tests by enabling the `fast` profile with the following option:

    -Dfast 

##### Enabling / Disabling multi-thread builds

By default the build is single-threaded, to ensure robustness and avoid *any* concurrency issue
that might occur by one of the involved maven plugins.

However, it is possible to enable multi-thread builds (1 thread per core) by enabling the
`multiThread` profile with the following option:

    -DmultiThread 

##### Build only some modules of the RedHat Che Distribution

The advanced Maven Reactor options described [here](http://books.sonatype.com/mvnref-book/reference/_using_advanced_reactor_options.html)
are also available in the RedHat Che distribution build using the following properties:

- List of reactor projects to build:

```
-Dprojects=<comma separated list of modules>
```

or

```
-Dpl=<comma separated list of modules>
```

- Reactor project to resume the build from:
    
```
-Dresume-from=<module to resume from>
```

or 

```
-Drf=<module to resume from>
```

- Also make the projects *required by* the projects specified in the `projects` option:

```
-Dalso-make
```

or 

```
-Dam
```

- Also make the projects *that depend on* the projects specified in the `projects` option:

```
-Dalso-make-dependents
```

or 

```
-Damd
```


So for example if you only want to rebuild the Keycloak server plugin and have the main assembly
up-to-date, just use the following options:

```
-Dpl=plugins/keycloak-plugin-server -Damd
```    

## Developer-friendly scripts to build and deploy or rollout to minishift

The `dev-scripts` folder contains various scripts for developers that allow easily running the Maven
build but also create / tag the required docker image and, optionally deploy or rollout it in a local 
Minishift installation.

All these `build_xxxx.sh` scripts can take arguments that will be passed to the underlying Maven build.
The available arguments are detailed in the [previous section](#build-options-and-parameters).

Note that the `-DlocalCheRepository` and `-DwithoutKeycloak` arguments are already 
passed by the scripts, so you don't need to add them.

On the other hand the `-Dpl` and `-Damd` are particularly useful to allow rebuilding / redeploying only a part (e.g. a plugin)
of the RH distribution.


### Build prerequisites

* Install Che development prerequisites
* Clone the official che git repository and checkout the openshift-connector branch:

```bash
git clone https://github.com/eclipse/che
cd che
git checkout openshift-connector
```

* Set some environment variables:
    * `UPSTREAM_CHE_PATH` points to the local directory where the official Che repo was checked out
    and is *mandatory* for some scripts
    
    ```bash
    export UPSTREAM_CHE_PATH="/Users/mariolet/Github/che"
    ```
    
    * `DOCKER_HUB_NAMESPACE`, `DOCKER_HUB_USER` and `DOCKER_HUB_PASSWORD` can be overridden to point
    to your own Docker Hub account
    
    ```bash
    export DOCKER_HUB_NAMESPACE=myDockerNamspace
    export DOCKER_HUB_USER=myDockerUserName
    export DOCKER_HUB_PASSWORD=myDockerPassword
    ```

### Openshift / Minishift deployment prerequisites

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

### RedHat distribution build scripts

This assumes that the upstream Che has been previously fully built.

In the `rh-che` repository, the RedHat distribution can be built with one of the following scripts:

- `dev-scripts/build_fabric8.sh`:
    - runs the RH maven build with the options passed as
arguments,
    - generates the docker images for the upstream and the RH
distribution and tag them appropriately.

- `dev-scripts/build_fabric8_and_deploy.sh`:
    - changes the current docker environment to use
the minishift docker daemon,
    - runs `dev-scripts/build_fabric8.sh`
    - deletes all the Che-related resources in minishift
    - creates all the require resources in minishift based on the
generated RH docker image.
    
- `dev-scripts/dev_build_and_rollupdate.sh`:
    - changes the current docker environment to use 
the minishift docker daemon,
    - runs `dev-scripts/build_fabric8.sh`
    - performs a rolling update of the current minishift deployments
with the new docker images 

As an example, if you want to clean/rebuild *only* the Bayesian language server,
without any validation check or test, and redeploy this on Minishift,
you can type the following command:

```bash
dev-scripts/build_fabric8_and_deploy.sh -Dfast -Dpl="plugins/che-plugin-bayesian-lang-server" -Damd clean
```

### Upstream Che Openshift Connector build scripts

To rebuild only the Openshift connector related plugins in the upstream Che,
and update the RedHat distribution accordingly, you can use one of the following scripts: 


- `dev-scripts/build_openshift_connector.sh`:
    - runs the upstream Che maven build on the openshift connector
related modules
    - updates the RH distribution by running RH maven build with
    the options passed as arguments,
    - generates the docker images for the upstream and the RH
distribution and tag them appropriately in the *local docker
environment*.

- `dev-scripts/build_openshift_connector_and_deploy.sh`:
    - changes the current docker environment to use
the minishift docker daemon,
    - runs `dev-scripts/build_fabric8.sh`
    - deletes all the Che-related resources in minishift
    - creates all the require resources in minishift based on the
generated RH docker image.
    
- `dev-scripts/build_openshift_connector_and_rollupdate.sh`:
    - changes the current docker environment to use
the minishift docker daemon,
    - runs `dev-scripts/build_fabric8.sh`
    - performs a rolling update of the current minishift deployments
    with the new docker images 


As an example, if your upstream Che repository is on the "myFixBranch" git branch,
and you want to rebuild the upstream openshift connector integration,
and redeploy the resulting RH distribution to minishift,
you can type the following command:

```bash
dev-scripts/build_openshift_connector_and_deploy.sh -Dfast -Dche.branch="myFixBranch" clean
```

### Additional scripts

#### Delete all resources and clean up in Minishift

```bash
dev-scripts/setenv-for-deploy.sh
dev-scripts/delete-all.sh
```

#### Create and tag Docker images from the RH maven build 

```bash
dev-scripts/create_docker_images_and_tag.sh
```
