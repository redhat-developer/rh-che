[![Master Build Status](https://ci.centos.org/buildStatus/icon?subject=master&job=devtools-rh-che-build-che-credentials-master/)](https://ci.centos.org/job/devtools-rh-che-build-che-credentials-master/)
[![Compatibility Build Status](https://ci.centos.org/buildStatus/icon?subject=compatibility&job=devtools-rh-che-build-master/)](https://ci.centos.org/view/Devtools/job/devtools-rh-che-build-master/)

# Eclipse Che on OpenShift 

[![Contribute](https://che.openshift.io/factory/resources/factory-contribute.svg)](https://che.openshift.io/f?url=https://raw.githubusercontent.com/redhat-developer/rh-che/master/devfile.yaml)

## Table Of Contents

* [Job statuses](#job-statuses)
* [What is the Red Hat Che distribution](#what-is-the-red-hat-che-distribution)
* [How to build it](#how-to-build-it)
* [PR-Check details](#pr-check-details)

## Job statuses

#### Periodic functional tests

| Cluster       | Status    |
| ------------- |-------------|
| us-east-2 | [![Build Status](https://ci.centos.org/buildStatus/icon?job=devtools-rh-che-periodic-prod-2/)](https://ci.centos.org/job/devtools-rh-che-periodic-prod-2/) |
| us-east-2a | [![Build Status](https://ci.centos.org/buildStatus/icon?job=devtools-rh-che-periodic-prod-2aProd)](https://ci.centos.org/view/Devtools/job/devtools-rh-che-periodic-prod-2aProd/) |
| us-east-1a | [![Build Status](https://ci.centos.org/buildStatus/icon?job=devtools-rh-che-periodic-prod-1a)](https://ci.centos.org/view/Devtools/job/devtools-rh-che-periodic-prod-1a/) |
| us-east-1b | [![Build Status](https://ci.centos.org/buildStatus/icon?job=devtools-rh-che-periodic-prod-1b)](https://ci.centos.org/view/Devtools/job/devtools-rh-che-periodic-prod-1b/) |
| us-east-2a preview | [![Build Status](https://ci.centos.org/buildStatus/icon?job=devtools-rh-che-periodic-prod-preview-2a/)](https://ci.centos.org/job/devtools-rh-che-periodic-prod-preview-2a/) |
| us-east-2 flaky | [![Build Status](https://ci.centos.org/buildStatus/icon?job=devtools-rh-che-flaky-prod-2/)](https://ci.centos.org/job/devtools-rh-che-flaky-prod-2/) | 

#### Periodic route tests

| Cluster       | Status    |
| ------------- |-------------|
| us-east-2 | [![Build Status](https://ci.centos.org/buildStatus/icon?job=devtools-rh-che-periodic-route-2)](https://ci.centos.org/view/Devtools/job/devtools-rh-che-periodic-route-2) |
| us-east-2a | [![Build Status](https://ci.centos.org/buildStatus/icon?job=devtools-rh-che-periodic-route-2a)](https://ci.centos.org/view/Devtools/job/devtools-rh-che-periodic-route-2a/) |
| us-east-1a | [![Build Status](https://ci.centos.org/buildStatus/icon?job=devtools-rh-che-periodic-route-1a)](https://ci.centos.org/view/Devtools/job/devtools-rh-che-periodic-route-1a/) |
| us-east-1b | [![Build Status](https://ci.centos.org/buildStatus/icon?job=devtools-rh-che-periodic-route-1b)](https://ci.centos.org/view/Devtools/job/devtools-rh-che-periodic-route-1b/) |

#### PR check for rh-che

| Job       | Status    |
| ------------- |-------------|
| PR check itself | [![Build Status](https://ci.centos.org/buildStatus/icon?job=devtools-rh-che-rh-che-prcheck-dev.rdu2c.fabric8.io)](https://ci.centos.org/job/devtools-rh-che-rh-che-prcheck-dev.rdu2c.fabric8.io) |
| cleanup | [![Build Status](https://ci.centos.org/buildStatus/icon?job=devtools-rh-che-prcheck-cleanup)](https://ci.centos.org/job/devtools-rh-che-prcheck-cleanup) |

Job ```cleanup``` cleans projects on dev cluster which are created as part of verifying PR changes.

#### Dependency image build

| Job       | Status    |
| ------------- |-------------|
| build che credentials master | [![Build Status](https://ci.centos.org/buildStatus/icon?job=devtools-rh-che-build-che-credentials-master)](https://ci.centos.org/job/devtools-rh-che-build-che-credentials-master) |
| prcheck build-dep | [![Build Status](https://ci.centos.org/buildStatus/icon?job=devtools-rh-che-prcheck-build-dep)](https://ci.centos.org/job/devtools-rh-che-prcheck-build-dep) |
| functional build-dep | [![Build Status](https://ci.centos.org/buildStatus/icon?job=devtools-rh-che-build-func-dep)](https://ci.centos.org/job/devtools-rh-che-build-func-dep) |


Job ```build che credentials master``` runs after PR check is merged. Job ```tests after rh-che build``` is dependent on it and is executed right after it. Job ```prcheck build-dep```
is dependent on building che credentials master too and is executed right after it finishes (in parallel with testing job). 

#### Compatibility check

[![Build Status](https://ci.centos.org/buildStatus/icon?job=devtools-rh-che-rh-che-compatibility-test-dev.rdu2c.fabric8.io)](https://ci.centos.org/job/devtools-rh-che-rh-che-compatibility-test-dev.rdu2c.fabric8.io)

#### Rollout job

[![Build Status](https://ci.centos.org/buildStatus/icon?job=devtools-rh-che-rollout-test-devtools-dev.ext.devshift.net)](https://ci.centos.org/job/devtools-rh-che-rollout-test-devtools-dev.ext.devshift.net)

## Red Hat Che distribution (Hosted Che)

Hosted Che is [Eclipse Che](https://www.eclipse.org/che/) hosted by Red Hat. A Che server is running on OpenShift Dedicated, and the user base is spread across multiple OpenShift Online clusters where workspaces are created. The detailed information about the Hosted Che can be found in the official [documentation](https://www.eclipse.org/che/docs/che-7/hosted-che/#about-hosted-che_hosted-che).

## Developer Guide

*See*: [the Dev guide](./dev-guide.adoc)

## PR-Check details

### PR-Check workflow

DEP IMAGE: [![Build Status](https://ci.centos.org/view/Devtools/job/devtools-rh-che-prcheck-build-dep/badge/icon)](https://ci.centos.org/view/Devtools/job/devtools-rh-che-prcheck-build-dep/)

[![PR-Check sequence diagram](https://raw.githubusercontent.com/redhat-developer/rh-che/master/documentation/rh-che-prcheck/pr_check_general_squence_diagram.svg?sanitize=true)](https://raw.githubusercontent.com/redhat-developer/rh-che/master/documentation/rh-che-prcheck/pr_check_general_squence_diagram.svg?sanitize=true)

- This diagram shows the general logic of PR-Check workflow from opening a PR to merge
- PR_Check job : https://ci.centos.org/view/Devtools/job/devtools-rh-che-rh-che-prcheck-dev.rdu2c.fabric8.io/buildTimeTrend
- Dependency image build job: https://ci.centos.org/view/Devtools/job/devtools-rh-che-prcheck-build-dep/buildTimeTrend

While deploying rh-che to ```devtools-dev.ext.devshift.net```, the Postgre is deployed too. There is service account doing the deploy. 
A special test suite is used for PR check. It contains basic tests to ensure workspaces are working and the project can be build and run. You can find 
that suite [here](https://github.com/redhat-developer/rh-che/blob/master/functional-tests/src/test/resources/suites/prcheck.xml).

Testing account for that test is on ```us-east-2a``` cluster.

### Clean up job [![Build Status](https://ci.centos.org/view/Devtools/job/devtools-rh-che-prcheck-cleanup/badge/icon)](https://ci.centos.org/view/Devtools/job/devtools-rh-che-prcheck-cleanup/)

For each PR the deployment on dev cluster is created. Therefore there is a job running once a day that checks current open PRs and remove deployments of closed ones.
The job can be found [here](https://ci.centos.org/view/Devtools/job/devtools-rh-che-prcheck-cleanup/).
