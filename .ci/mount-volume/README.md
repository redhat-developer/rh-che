# Tests for mounting volume
These tests try to mount volume for pod on openshift in namespace named \<username\>. Tests are executed via Jenkins every second hour.
Results are collected in zabbix.
Both Jenkins and zabbix are in private network.


## Prerequisites
- Running OpenShift

## Test execution
There is file run_test.sh which is executing tests. Tests are run against openshift so you need to pass variables specifying login details.

`./run_test.sh -u <username> -p <user_password> -r <url of openshift> -z <zabbix host> -v {volume_name} -t {attept_timeout} -i {iterations} -f`

This script expects four environment variables set:

| flag | variable | description | default value | mandatory |
| ---- | -------- | ------------- | ------------- | ------ | 
| -u | USERNAME | username to login with into openshift | | true |
| -p | PASSWORD | password for user to login into openshift | | true |
| -r | URL | url of running openshift | | true |
| -z | ZABBIX_SERVER | server of zabbix where results are stored | | true |
| -i | ITERATIONS | number of mounting and removing a pod | 5 | false |
| -t | ATTEMPT_TIMEOUT | maximum time in seconds for pod to change status (start/stop) | 120 | false |
| -v | VOLUME_NAME | name of the volume claim for PVC | default-volume-name | false |
| -f | FILL_PVC | if set, angular project is cloned and installed into PVC | false | false | 


After test run, the file zabbix.log is generated. This file includes metrics which are sent to zabbix. This file is also saved as an artifact of the jenkins job.

## Scenarios

At the begining of test, the oc and jq are downloaded and PATH is set. Then the PVC is checked and if not present, new PVC is created as specified by parameters given to the script.

If PVC is present or created successfully, test itself runs in loop for ITERATIONS times.

Each loop tries to create pod, wait until it is started, stop it and waits until it is deleted. The time for each operation is measured and send via
zabbix_sender to zabbix.