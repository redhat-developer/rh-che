# Eclipse Che on OpenShift

## How to build the RedHat Che distribution

#### What is the RedHat Che distribution

The RedHat distribution of Eclipse Che is a RedHat-specific packaging of the Che assemblies
that allows adding some RedHat specific plugins / behaviors upton the standard upstream Che
distribution. It is currently based on the `openshift-connector` branch of the upstream
Che repository.

RedHat modifications against the upstream Che include:
- the ability to disable the Dashboard (and remove the *Go to Dashboard* button from the Che IDE)
- The Keycloak integration
- The Bayesian integration (soon)

#### How the RedHat Che distribution build works

The RedHat Che distribution build is a *maven* build. By default, it *automatically* takes care 
of *everything* as part of the root maven build, which means that it:
- checks out the upstream GitHub `che-dependencies` and `che` repositories into the
`target/export`, based on a given fork (`eclipse` by default) and branch
(`openshift-connector` by default),
- builds the upstream repositories first as a pre-step,
- then builds the RedHat distribution maven sub-project based on this upstream build.

However, by passing a given property, it is also possible to bypass the checkout and build
of the upstream projects if the upstream che project is present locally on the developer's
machine and has already been fully built by maven. In this case, this local Che repository
is reused, which make the RedHat Che distribution build much faster.

The version of the RedHat Che distribution assembly and dashboard artifacts is derived from
the version of the upstream Che project. For example, if version of the used upstream Che
branch is:
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


#### How to start the  RedHat Che distribution build

The build is started by running *maven* in the root of the current git repository,
which is :`rh-che`

##### CI-oriented build (Upstream che + RedHat Che)

This checks out and builds the upstream Che before building the RedHat distribution.

    mvn clean install

To reuse a previously checked-out and built upstream Che, it is possible to use the 
`!checkout-base-che` profile:
    
    mvn -P '!checkout-base-che' clean install 

##### Developer-oriented build

You can also use a local upstream che repository you already have on your machine.
In this case oyu *have to* define the location of this local reporitory with the
`localCheRepository` property:

    mvn -DlocalCheRepository=<root of your local upstream Che Git repo> clean install

*This is the recomended way if you work on both the upstream che and the RedHat distribution.*
    
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

    -Pfast

##### Enabling / Disabling multi-thread builds

By default the build is single-threaded, to ensure robustness and avoid *any* concurrency issue
that might occur by one of the involved maven plugins.

However, it is possible to enable multi-thread builds (1 thread per core) by enabling the
`multiThread` profile with the following option:

    -PmultiThread

##### Build only some modules of the RedHat Che Distribution

The advanced Maven Reactor options described [here](http://books.sonatype.com/mvnref-book/reference/_using_advanced_reactor_options.html)
are also available in the RedHat Che distribution build using the following properties:

- List of reactor projects to build:
    
    -Dprojects=<comma separated list of modules>

or 
    
    -Dpl=<comma separated list of modules>
    
- Reactor project to resume the build from:
    
    -Dresume-from=<module to resume from>

or 
    
    -Drf=<module to resume from>
    
- Also make the projects *required by* the projects specified in the `projects` option:

    -Dalso-make

or 
    
    -Dam
    
- Also make the projects *that depend on* the projects specified in the `projects` option:

    -Dalso-make-dependents

or 
    
    -Damd
    

So for example if you only want to rebuild the Keycloak server plugin and have the main assembly
up-to-date, just use the following options:

    -Dpl=plugins/keycloak-plugin-server -Damd
    
    
## How to build the upstream openshift-connector branch for development purposes

### Build prerequisites

* Install Che development prerequisites
* Clone the official che git repository and checkout the openshift-connector branch:

```bash
git clone https://github.com/eclipse/che
cd che
git checkout openshift-connector
```

* Set some environment variables (or run `. ./scripts/setenv-for-build.sh`)

```bash
export CHE_IMAGE_REPO=rhche/che-server
export CHE_IMAGE_TAG=nightly
export GITHUB_REPO="/Users/mariolet/Github/che"
eval $(minishift docker-env)
```

* CHE_IMAGE_REPO and CHE_IMAGE_TAG are the Docker image name and tag which will be built.
* GITHUB_REPO points to the local directory where the official Che repo was checked out

### Build Che

In the rh-che repository, build Che by executing the following script:

```bash
scripts/build.sh
```

## How to run Che on OpenShift

### Runtime prerequisites

* Get [minishift](https://github.com/minishift/minishift#installation) (we have tested with v1.0.0beta4)
  * Minishift v1.0.0beta4 includes the `oc` binary, and this should be added to your `$PATH`. The binary is located
    in the `.minishift/cache/oc/<oc version>/` directory (e.g. on Linux, it is in `~/.minishift/cache/oc/v1.4.1/`).
* Get [gofabric8](https://github.com/fabric8io/gofabric8#getting-started) (we have tested with v0.4.121)
* Clone fabric8-online git repository:

`git clone https://github.com/fabric8io/fabric8-online.git`

* Set some environment variables (or run `. ./scripts/setenv-for-deploy.sh`)

```bash
export OPENSHIFT_USERNAME="developer"
export OPENSHIFT_PASSWORD="developer"
export CHE_OPENSHIFT_PROJECT="eclipse-che"
export OPENSHIFT_NAMESPACE_URL="${CHE_OPENSHIFT_PROJECT}.$(minishift ip).nip.io"
export CHE_LOG_LEVEL="INFO"
export CHE_DEBUGGING_ENABLED="false"
export FABRIC8_ONLINE_PATH="/home/user/github/fabric8-online/"
```

* OPENSHIFT_USERNAME and OPENSHIFT_PASSWORD are used to configure which Openshift account will be used
* CHE_OPENSHIFT_PROJECT is the name of the project in Openshift in which Che will be deployed
* OPENSHIFT_NAMESPACE_URL is the base URL used for all Che routes. Once deployed Che dashboard will be available at http://che-${OPENSHIFT_NAMESPACE_URL}.
* CHE_LOG_LEVEL is the logging level (DEBUG, INFO, WARN, ERROR etc)
* CHE_DEBUGGING_ENABLED set this to "true" to allow remote Java debugging of the Che server
* FABRIC8_ONLINE_PATH is the full path to the fabric8-online binary

### Deploy Che and all needed resources (configmaps, service account, pvc, pv, services, routes)

```bash
scripts/create-all.sh
```

### Che rolling update (helpful if you need to deploy a new build of Che)

If you wish to redeploy Che after rebuilding (using the build.sh script described above), execute the following command:

```bash
oc rollout latest che -n eclipse-che
```

### Delete all resources and clean up

```bash
scripts/delete-all.sh
```
