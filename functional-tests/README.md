| Cluster       | Status    |
| ------------- |-------------|
| us-east-2 | [![Build Status](https://ci.centos.org/buildStatus/icon?job=devtools-che-functional-tests-periodical-openshift.io-2)](https://ci.centos.org/job/devtools-che-functional-tests-periodical-openshift.io-2) |
| us-east-2a | [![Build Status](https://ci.centos.org/buildStatus/icon?job=devtools-che-functional-tests-periodical-prod-preview.openshift.io-2a)](https://ci.centos.org/view/Devtools/job/devtools-che-functional-tests-periodical-prod-preview.openshift.io-2a/) |
| free-stg | [![Build Status](https://ci.centos.org/buildStatus/icon?job=devtools-che-functional-tests-periodical-prod-preview.openshift.io-free-stg)](https://ci.centos.org/view/Devtools/job/devtools-che-functional-tests-periodical-prod-preview.openshift.io-free-stg/) |

# Functional tests for Eclipse Che on Openshift

## Table Of Content

* [What are these tests meant for](#what-are-these-tests-meant-for)
* [How to run it](#how-to-run-it)
* [Further details](#further-details)
* [When are tests running](#when-are-tests-running)

## What are these tests meant for

Rh-Che `functional-tests` are based on selenium framework with Guice injection.

These functional tests serve to verify that Eclipse Che on Openshift is working well. Tests include integration specific cases and some upstream tests to
verify Che itself.

There are some jobs running periodically on clusters us-east-2, us-east-2a and free-stg as you can see in the begining of this readme.

## How to run it
Prerequisities:
* Working account on Openshift.io.
* Running che-starter. Can be run as docker container like this:
```
docker run -p 10000:10000 -e "GITHUB_TOKEN_URL=https://auth.openshift.io/api/token?for=https://github.com" -e "OPENSHIFT_TOKEN_URL=https://sso.openshift.io/auth/realms/fabric8/broker/openshift-v3/token" -e "CHE_SERVER_URL=https://che.openshift.io" quay.io/openshiftio/almighty-che-starter:latest
```

### Run tests via maven
By default, tests will execute against production environment (che.openshift.io).
```
mvn clean verify -Pfunctional-tests \
    -Dche.testuser.name=<OSIO_USERNAME> \
    -Dche.testuser.email=<OSIO_EMAIL> \
    -Dche.testuser.offline_token=<OSIO_OFFLINE_TOKEN> \
    -Dche.testuser.password=<OSIO_PASSWORD>
```
If you need to run tests against another environment (for example prod-preview), few more variables have to be set (and che-starter has to be started with different parameters). For example, running tests against `prod-preview` will look like this:
```
docker run -p 10000:10000 \
    -e "GITHUB_TOKEN_URL=https://auth.prod-preview.openshift.io/api/token?for=https://github.com" \
    -e "OPENSHIFT_TOKEN_URL=https://sso.prod-preview.openshift.io/auth/realms/fabric8/broker/openshift-v3/token" \
    -e "CHE_SERVER_URL=https://che.prod-preview.openshift.io" \
    quay.io/openshiftio/almighty-che-starter:latest

mvn clean verify -Pfunctional-tests \
    -Dche.testuser.name=<OSIO_PROD_PREVIEW_USERNAME> \
    -Dche.testuser.email=<OSIO_PROD_PREVIEW__EMAIL> \
    -Dche.testuser.offline_token=<OSIO_PROD_PREVIEW__OFFLINE_TOKEN> \
    -Dche.testuser.password=<OSIO_PROD_PREVIEW__PASSWORD> \
    -Dche.host=che.prod-preview.openshift.io \
    -Dche.offline.to.access.token.exchange.endpoint=https://auth.prod-preview.openshift.io/api/token/refresh
```

### Run tests from docker container

By default the docker image is using latest master sources that are embedded inside the docker image along with required dependencies.  
It is possible to also mount local folder with alternative sources of rh-che/functional-tests  

This method spins up `che-starter` container and runs `chromedriver` inside the dependency image.  
No additional steps required.  

```
docker run --name functional-tests-dep --privileged \
           -v /var/run/docker.sock:/var/run/docker.sock \
           -e "RHCHE_ACC_USERNAME=<username>" \
           -e "RHCHE_ACC_PASSWORD=<password>" \
           -e "RHCHE_ACC_EMAIL=<email>" \
           -e "RHCHE_ACC_TOKEN=<offline_token>" \
           quay.io/openshiftio/rhchestage-rh-che-functional-tests-dep
```

###### Optional mount for alternate sources
If the source folder is mounted, the tests automatically run from the alternate sources.  
This change however means, that the dependencies will not be available in the offline mode.  
* `-v <local_functional-tests_full_path>:/root/che/`
  
###### Optional variables for changing target
* ```'allowEmpty=true'
  -e "RHCHE_OFFLINE_ACCESS_EXCHANGE=https://auth.<target>/api/token/refresh"
  -e "RHCHE_GITHUB_EXCHANGE=https://auth.<target>/api/token?for=https://github.com"
  -e "RHCHE_OPENSHIFT_TOKEN_URL=https://sso.<target>/auth/realms/fabric8/broker"
  -e "RHCHE_HOST_PROTOCOL=<http/https>"
  -e "RHCHE_HOST_URL=che.openshift.io"
  ```
  
###### Optional variables for screenshots and logs directory
If the logs folder is mounted, the container will automatically collect logs into the specified folder.  
* ```'allowEmpty=true'
  -v <local_screenshofts_directory>:/root/logs # che-starter logs
  -e "RHCHE_SCREENSHOTS_DIR=/root/logs/screenshots # example path for the locally mounted logs folder"
  ```

### Full list of variables

These tests require TestNG profile and listener must be set to `com.redhat.che.selenium.core.RhCheSeleniumTestHandler`.

VM options (Mandatory parameters in __bold__)

| Variable | Description | Default value|
|----------|---------| --- |
| __che.testuser.email__ | Openshift.io e-mail |  |
| __che.testuser.name__ | Openshift.io username |  |
| __che.testuser.offline_token__ | Openshift.io offline token |  |
| __che.testuser.password__ | Openshift.io password |  |
| che.threads | Thread count| `1` |
| che.workspace_pool_size | Amount of workspace to be used while testing | `1` |
| che.host | Host where Che runs | `che.openshift.io` |
| che.port | Port where Che runs | `443` |
| che.protocol | Protocol | `https` |
| grid.mode | Used, when running with selenium-grid | `false` |
| browser | browser for selenium tests | `GOOGLE_CHROME` |
| driver.port | port of driver | `9515` |
| excludedGroups | Group of tests to be excluded |  |
| cheStarterUrl | URL of running che starter | `http://localhost:10000` |

## Further details

To be adoptable to upstream Che changes as much as possible, functional tests have dependency on artifacts from upstream tests. These two projects include
page fragments and all tests from upstream. Projects are taken directly from upstream repo [che-selenium-core](https://github.com/eclipse/che/tree/master/selenium/che-selenium-core) and [che-selenium-test](https://github.com/eclipse/che/tree/master/selenium/che-selenium-test).

## When are tests running

There are periodical jobs running everyday at 2am and 2pm (server time). Logs are saved as artifacts and sent to zabbix. You can find link to them at the
begining of this README.

### Related tests not included in this repo

There are two more tests for Che on Openshift, but their coverage is very small and serves mostly as a monitoring. They run on private jenkins and report
results to zabbix. These tests are located in [che-functional-tests repo](https://github.com/redhat-developer/che-functional-tests).

**Performance tests** are testing if user can correctly create/start/stop/remove workspaces. You can find more detail information in [performance tests repo](https://github.com/redhat-developer/che-functional-tests/tree/master/che-start-workspace).

**Test for mounting volume** verify if it is possible to mount volume in non-che namespace in openshift.io. Tests are creating/starting/stopping/removing pods. More detail information can be found in [tests repo](https://github.com/redhat-developer/che-functional-tests/tree/master/mount-volume).
