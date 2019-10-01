# Workspace startup tests

The purpose of these tests is to periodically collect data about cluster health regarding the workspace startup and stopping times.
Collected metrics are then reported to a zabbix server.
These tests can run in two modes:

* PVC mode where a large volume with a practival project is attached to the pod
* Ephemeral mode where an empty volume is attached instead

## Jenkins job dependencies

* `zabbix_sender`
* `curl`

## Order of operation

* get active token using `git_active_tokens.sh` from users.properties file
* run locust tests with the active tokens, in `--no-web` profile, `-c` count of locusts is based on the lines in the tokens file and `-r` spawnrate is always one, `--only-summary` is used to reduce the output and output is exported with `--csv=<path>` option
  * set global locust variables like `token;username;environment` array, user count, cycles count, open an HTTP session to the target host
  * get openshift cluster token and delete existing workspaces
  * prepare soft and hard failure commands for `zabbix_sender`
  * create workspace
  * prepare timer and start workspace
  * save data, reset timer and stop workspace
  * save data and delete workspace
  * if the cycles count has been met, raise a `StopLocust` exception - only way to manually stop a locust thread execution without affecting other threads
* if job exits with success, process the results and output them in a zabbix format
* use `zabbix_sender` to send the metrics in batch

## Components

* `osioperf.py` - main Python script
* `get_active_tokens.sh` - helper bash script to extract active tokens for users
* `che7_ephemeral.json` & `che7_workspace.json` - workspace stack based json definiton for PVC and Ephemeral workspaces used in tests

## Get_active_tokens.sh

### Environment parameters

* `USERS_PROPERTIES_FILE` - contains a semicolon separated username, passowrd and user environment

### Dependencies

* `curl`

### Usage

`./get_active_tokens.sh <output_file_location>`

## Osioperf.py

This is the main job script that takes care of the worker distribution based on the amount of users in the `tokens file` received as a result of calling `get_active_tokens.sh`.
This script also automatically assigns all the necessary variables based on the user environment.

### Environment parameters

* `CHE_STACK_FILE` - path to a workspace json that should be used for workspace creation
* `ZABBIX_SERVER` - URL to a zabbix server that is used to store the test results
* `ZABBIX_PORT` - port of the zabbix service running on the server
* `ZABBIX_EPHEMERAL` - True/False switch that changes which metrics are used for reporting. If true, it's reporting Ephemeral values, else it's reporting PVC values
* `CYCLES_COUNT` - describes how many times is the test to be run per-user. default = 1
* `START_HARD_FAILURE_TIMEOUT` - time in seconds when the workspace startup reports an error and fails
* `START_SOFT_FAILURE_TIMEOUT` - time in seconds when the workspace startup reports a soft failure - intended time exceeded
* `STOP_HARD_FAILURE_TIMEOUT` - time in seconds when the workspace stopping reports an error and fails
* `STOP_SOFT_FAILURE_TIMEOUT` - time in seconds when the workspace stopping reports a soft failure - intended time exceeded
* `USER_TOKENS` - new-line separated user authentication details containing active token, username and environment separated by `'`

### Dependencies

* `Python 3.7`
* `locustio`
* `locust`
* `zabbix_sender`

### Usage

```
locust -f osioperf.py --no-web -c <number_of_active_tokens> \
       -r 1 --only-summary --csv=<export_file_path>
```
