# How to deploy Che on OpenShift
Scripts, patchs and templates to run Eclipse Che on OpenShift

## Deploy Che on che.ci.centos.org

1\. [Optional] Build Che (`openshift-connector` branch) 

This step is optional: the Docker image is already built and available on Docker Hub `rhche/che-server:nightly`.

Instructions to build the image:

```sh
# Get the source code 
git clone https://github.com/eclipse/che.git
cd che
git checkout openshift-connector

# Install pre-requisites (CentOS)
yum -y update
yum -y install centos-release-scl java-1.8.0-openjdk-devel golang git patch
yum -y install rh-maven33 rh-nodejs4

source scl_source enable rh-maven33 rh-nodejs4
 
# Build Che 
export NPM_CONFIG_PREFIX=~/.node_modules
export PATH=$NPM_CONFIG_PREFIX/bin:$PATH
npm install -g bower gulp typings
mvn clean install -Pfast

# Build docker image and push it to a registry
docker build -t rhche/che-server:nightly
docker push rhche/che-server:nightly
```

2\. Configure OpenShift

```sh
# Create OpenShift project
oc login -u openshift-dev che.ci.centos.org
oc new-project eclipse-che

# Create a serviceaccount with privileged scc
oc login -u system:admin -n eclipse-che
oc create serviceaccount cheserviceaccount
oc adm policy add-scc-to-user privileged -z cheserviceaccount
oc adm policy add-cluster-role-to-user cluster-admin system:serviceaccount:eclipse-che:cheserviceaccount
```

3\. Deploy Che

```sh
# Get the script from github
git clone https://github.com/redhat-developer/rh-che
cd rh-che/scripts
# Prepare the environment
oc login -u openshift-dev che.ci.centos.org
export CHE_HOSTNAME=demo.che.ci.centos.org
export CHE_OPENSHIFT_ENDPOINT=https://che.ci.centos.org:8443/
export CHE_IMAGE=rhche/che-server:nightly
export CHE_LOG_LEVEL=INFO
export CHE_OPENSHIFT_USERNAME=<replacewithusername>
export CHE_OPENSHIFT_PASSWORD=<replacewithpassword>
# If a previous version of Che was deployed, delete it
./openche.sh delete
# Install OpenShift Che template and deploy Che 
./openche.sh deploy
```
Once the pod is successfully started Che dashboard should be now available at http://che.ci.centos.org/


## Deployment Che on Minishift or with "oc cluster"

1\. Get [minishift](https://github.com/minishift/minishift#installation), create an OpenShift cluster (`minishift start`), open the web console (`minishift console`) and read the instructions to install the OpenShift CLI (help->Command Line Tools).

2\. Use that command to use minishift docker daemon: `eval $(minishift docker-env)`
Skip this step if you use "oc cluster".

3\. Configure OpenShift

```sh
# Create OpenShift project
oc login -u developer -p developer
oc new-project eclipse-che

# Create a serviceaccount with privileged scc
oc login -u system:admin -n eclipse-che
oc create serviceaccount cheserviceaccount
oc adm policy add-scc-to-user privileged -z cheserviceaccount
oc adm policy add-cluster-role-to-user cluster-admin system:serviceaccount:eclipse-che:cheserviceaccount
```

4\. Update `/etc/hosts` with a line that associates minishift/openshift IP address (`minishift ip`) and the hostname `che.openshift.mini`

5\. Create Persistent Volumes

```sh
oc login -u system:admin
oc create -f os-templates/hostPath/pv0001.yaml
oc create -f os-templates/hostPath/pv0002.yaml
```

or, if you use "oc cluster" and have set nfs, you can use the following:

```sh
oc login -u system:admin
oc create -f os-templates/nfs/pv0001.yaml
oc create -f os-templates/nfs/pv0002.yaml
```

6\. Deploy Che

```sh
# Get the script from github
git clone https://github.com/redhat-developer/rh-che
cd rh-che/scripts
# Prepare the environment
oc login -u developer -p developer

export CHE_HOSTNAME=che.openshift.mini
export CHE_IMAGE=rhche/che-server:nightly
export CHE_OPENSHIFT_ENDPOINT=https://$(minishift ip):8443
docker pull $CHE_IMAGE

When using "oc cluster", replace "$(minishift ip)" with your openshift ip.
You will get that ip address when starting a cluster (oc cluster up).

# If a previous version of Che was deployed, delete it
./openche.sh delete
# Install OpenShift Che templat eand deploy Che 
./openche.sh deploy
```
Once the pod is successfully started Che dashboard should be now available on the minishift console.

>`./build_openshift_connector.sh` script can be used for building `rhche/che-server:nightly` image locally

## Deployment of Che on ADB (deprecated, use minishift instead)

1\. Get the [atomic developer bundle](https://github.com/projectatomic/adb-atomic-developer-bundle#how-do-i-install-and-run-adb)

2\. Start a shell in ADB VM

```sh
vagrant ssh
```

3\. Get docker-latest (v.1.12.1) and disable older docker (v1.10.3)

```sh
# Stop Docker and OpenShift
sudo systemctl stop openshift
sudo systemctl stop docker
sudo systemctl disable docker
# Get docker-latest
sudo yum install -y docker-latest
# Update OpenShift service config
sudo sed -i.orig -e "s/^After=.*/After=docker-latest.service/g" /usr/lib/systemd/system/openshift.service
sudo sed -i.orig -e "s/^Requires=.*/Requires=docker-latest.service/g" /usr/lib/systemd/system/openshift.service
# Change docker-latest config to use docker-current configuration
sudo sed -i.orig -e "s/docker-latest/docker/g" /usr/lib/systemd/system/docker-latest.service
sudo sed -i.orig -e "s/Wants=docker-storage-setup.service/Wants=docker-latest-storage-setup.service/g" /usr/lib/systemd/system/docker-latest.service
# Restart Docker and OpenShift
sudo systemctl start docker-latest
sudo systemctl enable docker-latest
sudo systemctl start openshift
```

4\. Configure OpenShift

```sh
# Create OpenShift project
oc login -u openshift-dev -p devel
oc new-project eclipse-che

# Create a serviceaccount with privileged scc
oc login -u admin -p admin -n eclipse-che
oc create serviceaccount cheserviceaccount
oc adm policy add-scc-to-user privileged -z cheserviceaccount
oc adm policy add-cluster-role-to-user cluster-admin system:serviceaccount:eclipse-che:cheserviceaccount
```

5\. Deploy Che

```sh
# Get the script from github
git clone https://github.com/redhat-developer/rh-che/
cd rh-che
# Prepare the environment
oc login -u openshift-dev -p devel
export CHE_HOSTNAME=che.openshift.adb
export CHE_IMAGE=rhche/che-server:nightly
docker pull $CHE_IMAGE
# If a previous version of Che was deployed, delete it
./openche.sh delete
# Install OpenShift Che templat eand deploy Che 
./openche.sh deploy
```
Once the pod is successfully started Che dashboard should be now available at http://che.openshift.adb/
