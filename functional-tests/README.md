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
These tests require TestNG profile and listener must be set to `com.redhat.che.selenium.core.RhCheSeleniumTestHandler`

To run these tests it is necessary to have some variables set:

VM options

| Variable | Descrition |
|----------|---------|
| che.threads | threads in which tests will be executed |
| che.workspace_pool_size | amount of workspace to be used while testing |
| che.host | host where Che runs (e.g. rhche.openshift.io) |
| che.port | port where Che runs (e.g. 443) |
| che.protocol | protocol (https) |
| grid.mode | false |
| browser | browser for selenium tests (e.g. GOOGLE_CHROME) |
| driver.port | port of driver (e.g. port of chromedriver) |
| driver.version | version of driver |
| excludedGroups | group of tests to be excluded |

Environment variables

| Variable | Descrition |
|----------|---------|
| CHE_INFRASTRUCTURE | infrastructure on which Che runs (e.g. openshift) |
| CHE_TESTUSER_EMAIL | email of testing user |
| CHE_TESTUSER_OFFLINE__TOKEN | refresh token of testing user |
| CHE_MULTIUSER | use multiuser mode |
| CHE_OFFLINE_TO_ACCESS_TOKEN_EXCHANGE_ENDPOINT | endpoint for autentization (e.g. https://auth.openshift.io/api/token/refresh) |
| CHE_TESTUSER_NAME | username of testing user |
| CHE_TESTUSER_PASSWORD | password of testing user |

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
