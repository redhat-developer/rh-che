#### Periodic jobs:

| Cluster       | Status    |
| ------------- |-------------|
| us-east-2 | [![Build Status](https://ci.centos.org/buildStatus/icon?job=devtools-rh-che-periodic-prod-2/)](https://ci.centos.org/job/devtools-rh-che-periodic-prod-2/) |
| us-east-2a | [![Build Status](https://ci.centos.org/buildStatus/icon?job=devtools-rh-che-periodic-prod-2aProd)](https://ci.centos.org/view/Devtools/job/devtools-rh-che-periodic-prod-2aProd/) |
| us-east-1a | [![Build Status](https://ci.centos.org/buildStatus/icon?job=devtools-rh-che-periodic-prod-1a)](https://ci.centos.org/view/Devtools/job/devtools-rh-che-periodic-prod-1a/) |
| us-east-1b | [![Build Status](https://ci.centos.org/buildStatus/icon?job=devtools-rh-che-periodic-prod-1b)](https://ci.centos.org/view/Devtools/job/devtools-rh-che-periodic-prod-1b/) |
| us-east-2a preview | [![Build Status](https://ci.centos.org/buildStatus/icon?job=devtools-rh-che-periodic-prod-preview-2a/)](https://ci.centos.org/job/devtools-rh-che-periodic-prod-preview-2a/) |

#### PR check for rh-che:

| Job       | Status    |
| ------------- |-------------|
| PR check itself | [![Build Status](https://ci.centos.org/buildStatus/icon?job=devtools-rh-che-rh-che-prcheck-dev.rdu2c.fabric8.io)](https://ci.centos.org/job/devtools-rh-che-rh-che-prcheck-dev.rdu2c.fabric8.io) |
| cleanup | [![Build Status](https://ci.centos.org/buildStatus/icon?job=devtools-rh-che-prcheck-cleanup)](https://ci.centos.org/job/devtools-rh-che-prcheck-cleanup) |

Job ```cleanup``` cleans projects on dev cluster which are created as part of verifying PR changes.

#### Dependency image build:

| Job       | Status    |
| ------------- |-------------|
| build che credentials master | [![Build Status](https://ci.centos.org/buildStatus/icon?job=devtools-rh-che-build-che-credentials-master)](https://ci.centos.org/job/devtools-rh-che-build-che-credentials-master) |
| tests after rh-che build | [![Build Status](https://ci.centos.org/buildStatus/icon?job=devtools-che-functional-tests-after-rh-che-build-prod-preview.openshift.io)](https://ci.centos.org/job/devtools-che-functional-tests-after-rh-che-build-prod-preview.openshift.io) |
| prcheck build-dep | [![Build Status](https://ci.centos.org/buildStatus/icon?job=devtools-rh-che-prcheck-build-dep)](https://ci.centos.org/job/devtools-rh-che-prcheck-build-dep) |


Job ```build che credentials master``` runs after PR check is merged. Job ```tests after rh-che build``` is dependent on it and is executed right after it. Job ```prcheck build-dep```
is dependent on building che credentials master too and is executed right after it finishes (in parallel with testing job). 

#### Compatibility check:

[![Build Status](https://ci.centos.org/buildStatus/icon?job=devtools-rh-che-rh-che-compatibility-test-dev.rdu2c.fabric8.io)](https://ci.centos.org/job/devtools-rh-che-rh-che-compatibility-test-dev.rdu2c.fabric8.io)

#### Rollout job:

[![Build Status](https://ci.centos.org/buildStatus/icon?job=devtools-rh-che-rollout-test-devtools-dev.ext.devshift.net)](https://ci.centos.org/job/devtools-rh-che-rollout-test-devtools-dev.ext.devshift.net)

# Eclipse Che on OpenShift 

## Table Of Contents

* [What is the Red Hat Che distribution](#what-is-the-red-hat-che-distribution)
* [How to build it](#how-to-build-it)
* [PR-Check details](#pr-check-details)

## What is the Red Hat Che distribution

The Red Hat distribution of Eclipse Che is a Red Hat specific packaging of Che assemblies
that adds some Red Hat specific plugins / behaviors up to the standard upstream Che
distribution. The Red Hat distribution powers [openshift.io](https://openshift.io) developers workspaces.

Red Hat modifications against the upstream Che include:

* [fabric8-analytics Language Server](https://github.com/fabric8-analytics/fabric8-analytics-lsp-server)
* a different set of Che stacks than upstream. They can contain specific Red Hat configurations such as providing 'oc' on command-line
* telemetry to follow usage of different parts

## Interaction of Red Hat Che with other openshift.io services

![Interaction of Red Hat Che with other openshift.io services](https://user-images.githubusercontent.com/1461122/48473793-8213aa80-e7f9-11e8-8bc1-75549c771438.png)

1. Opening `Codebases UI` in browser
2. `fabric8-ui` sends request to `fabric8-wit`
3. `fabric8-wit` sends request to `che-starter` in order to initiate workspace creation 

## How to build it

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
