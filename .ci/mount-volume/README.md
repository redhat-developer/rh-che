# Mount volume tests

The main goal behind these tests is to collect data about how long it takes for the gluster-fs to mount volumes on our pods. These tests are testing both empty volumes and volumes with significant amount of data inside of them and report the data back to zabbix.
Order of operation is as follows:

* parse all the options and environment variables sourcing `getopts_util.sh`
* log in to the cluster and ensure that the user is on the correct project
* verify that the PVC necessary for this test exists, if not then create it and fill it with data running `setup_pvc.sh`
* source `simple-pod.sh` and execute tests
  * process the pod deployment json using `jq` and modify the volume claim name
  * create the pod using `oc apply` and wait for it to be running
  * save the startup time (until it reports ready using `oc`) and delete the pod
  * save the delete time (until it disappears from the cluster) and reset the loop (run for number of iterations set from an env variable)
* if the test passes successfully, report the data to zabbix calling `zabbix.sh`
* clean up the cluster

## Components

* `run_tests.sh` - main script responsible for running the mount volume test
* `getopts_util.sh` - helper script containing functions for argument processing and help
* `setup_pvc.sh` - simple script that verifies that the required volumes are present, if not it can create them and fill them with data
* `simple-pod.sh` - script that takes care of creating, running, stopping and deleting the simple-pods and collecting the data
* `zabbix.sh` - script that processes mount volume job results and pushes data to zabbix
* `pvc.yaml` - k8s compatible persistent volume claim definition
* `simple-pod.json` & `simple-pod-large.json` - k8s compatible pod definition files

## Run_tests.sh

This is the main entrypoint script, taking care of the parameter parsing, setting up the environment and running the tests.

### Environment parameters

### Dependencies

* `origin-clients`
* `jq`

### Usage

`./run_tests.sh [-options]`

#### Options

* `h` - print help and exit
* `u` - Openshift username
* `p` - Openshift user password
* `r` - Openshift URL
* `v` - PVC name to be mounted
* `z` - zabbix prefix for `zabbix.sh` script
* `i` - number of iterations for the pod creation and deletion
* `f` - [bool] Fill PVC with data if it doesn't exist

## getopts_util.sh

This file contains two methods:

* printHelp - function that echoes help for the script into stdout
* checkParams - function that validates that all the necessary environment variables have been set

### Environment parameters

* `USERNAME` - openshift cluster username
* `PASSWORD` - openshift cluster password
* `URL` - openshift cluster URL
* `VOLUME_NAME` - name of the PVC to be mounted to the `simple-pod`
* `ZABBIX_PREFIX` - prefix for the name of a zabbix metric
* `ATTEMPT_TIMEOUT` - Hard timeout in seconds for one cycle (start and stop max timeout, default=120)
* `ITERATIONS` - describes how many times should the job attempt to mount the volume, the higher the amount of iterations, the more stable result, as the values are being averaged from all the consecutive runs (default=5)

### Dependencies

### Usage

This script is only supposed to be used by sourcing the file, as it only contains bash functions

## setup_pvc.sh

This script takes care of verifying that the PVC described by `VOLUME_NAME` environment variable exists. If the `FILL_PVC` environment variable is not set, it does nothing, otherwise the script utilizes the `simple-pod.sh` to create a temporary pod to manipulate with the PVC and fill it with data, namely <https://github.com/angular/quickstart.git> project and builds it.

### Dependencies

* `origin-clients`
* `jq`

### Environment parameters

* `VOLUME_NAME` - contains the name of the PVC to be used
* `FILL_PVC` - if this variable is set, the script will check whether the volume exists and will create it if it's missing
* `ATTEMPT_TIMEOUT` - timeout in seconds before the script considers the pod startup a failure

### Usage

`./setup_pvc.sh`

## simple-pod.sh

This file contains five methods:

* podStarted - method that takes one argument: `POD_NAME` as the first parameter. This method checks whether the pod exists and it's status is equal to `Running`
* podStopped - method that takes one argument: `POD_NAME` as the first parameter. This method checks whether the pod is present on the cluster and returns 0 if the pod is missing
* waitForPodToBeRunning - function that doesn't take any arguments, but has `POD_NAME` and `ATTEMPT_TIMEOUT` as required environment variables. This function calculates the time it takes for the pod to start and returns 0 if it started within the timeout.
* waitForPodToStop - function that doesn't take any arguments, but has `POD_NAME` and `ATTEMPT_TIMEOUT` as required environment variables. This function calculates the time it takes for the pod to stop and returns 0 if it stopped within the timeout.
* simplePodRunTest - main function that handles the test logic. This function takes `ITERATIONS` and `VOLUME_NAME` as environment vatiables. This function runs the cycle of starting and stopping the pods.

### Dependencies

### Environment parameters

* `POD_NAME` - name of the simple-pod to start
* `ATTEMPT_TIMEOUT` - timeout in seconds for the workspace to start
* `ITERATIONS` - number of cycles to run for the simple-pod PVC mounts
* `VOLUME_NAME` - name of the PVC to mount to the simple-pod

### Usage

This script is only supposed to be used by sourcing the file, as it only contains bash functions

## zabbix.sh

This file is responsible for processing the job results, exporting them as zabbix compatible xml and using `zabbix_sender` to push the data into our zabbix.
It contains four methods:

* processItem - This method takes one argument `time_taken`. If there are multiple values submitted by this method, it also counts the max, min and avg values
* getMetrics - This method takes one argument `file` which contains rows of time values. It iteratively calls `processItem` on all of the lines of the file
* addMetricsToLogFile - This method takes care of exporting the min, max, avg and median values into the zabbix_formated log file
* generateLogs - This method takes one argument `METRICS` which contains a path to a `.csv` file containing the outputs of the tests. It also parses the data and calls `addMetricsToLogFile`

### Dependencies

* `zabbix_sender`

### Environment parameters

* `ZABBIX_PREFIX` - prefix of a zabbix metric (default:mount_volume-)

### Usage

`./zabbix.sh <openshift_URL> <zabbix_prefix> <iterations>`
