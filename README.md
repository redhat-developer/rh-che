# Eclipse Che on OpenShift

## How to build openshift-connector branch

### Build prerequisites

* Install Che development prerequisites
* Clone che git repository and checkout openshift-connector branch:

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

### Build Che

```bash
scripts/build.sh
```

## How to run Che on OpenShift

### Runtime prerequisites

* Get [minishift](https://github.com/minishift/minishift#installation) (we have tested with v1.0.0beta4)
* Get [gofabric8](https://github.com/fabric8io/gofabric8#getting-started) (we have tested with v0.4.121)
* Clone fabric8-online git repository:

`git clone https://github.com/fabric8io/fabric8-online.git`

* Set some environment variables (or run `. ./scripts/setenv-for-deploy.sh`)

```bash
export OPENSHIFT_USERNAME="developer"
export OPENSHIFT_PASSWORD="developer"
export CHE_OPENSHIFT_PROJECT="eclipse-che"
export CHE_HOSTNAME="che.$(minishift ip).nip.io"
export CHE_LOG_LEVEL="INFO"
export CHE_DEBUGGING_ENABLED="false"
export FABRIC8_ONLINE_PATH="/home/user/github/fabric8-online/"
```

### Deploy Che and all needed resources (configmaps, service account, pvc, pv, services, routes)

```bash
scripts/create-all.sh
```

### Che rolling update (helpful if you need to deploy a new build of Che)

```bash
oc rollout latest che -n eclipse-che
```

### Delete all resources and clean up

```bash
scripts/delete-all.sh
```