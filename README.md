| Last build        | Description           |
| ------------- |:-------------:|
| [![Build Status](https://ci.centos.org/buildStatus/icon?job=devtools-rh-che-periodic-prod-2)](https://ci.centos.org/view/Devtools/job/devtools-rh-che-periodic-prod-2/) | periodic tests against production cluster us-east-2 |
| [![Build Status](https://ci.centos.org/buildStatus/icon?job=devtools-rh-che-periodic-prod-preview-2a)](https://ci.centos.org/view/Devtools/job/devtools-rh-che-periodic-prod-preview-2a/) | periodic tests against prod-preview cluster us-east-2a |
| [![Build Status](https://ci.centos.org/buildStatus/icon?job=devtools-rh-che-build-che-credentials-master)](https://ci.centos.org/view/Devtools/job/devtools-rh-che-build-che-credentials-master/) | master build |
| [![Build Status](https://ci.codenvycorp.com/buildStatus/icon?job=rh-che-ci-master)](https://ci.codenvycorp.com/job/rh-che-ci-master/) | build compatibility with upstream |
| [![Build Status](https://ci.centos.org/buildStatus/icon?job=devtools-rh-che-rh-che-compatibility-test-dev.rdu2c.fabric8.io)](https://ci.centos.org/view/Devtools/job/devtools-rh-che-rh-che-compatibility-test-dev.rdu2c.fabric8.io/) | integration tests against latest upstream SNAPSHOT |


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

[![PR-Check sequence diagram](https://raw.githubusercontent.com/redhat-developer/rh-che/master/documentation/rh-che-prcheck/pr_check_general_squence_diagram.svg)](https://raw.githubusercontent.com/redhat-developer/rh-che/master/documentation/rh-che-prcheck/pr_check_general_squence_diagram.plantuml)

- This diagram shows the general logic of PR-Check workflow from opening a PR to merge
- PR_Check job : https://ci.centos.org/view/Devtools/job/devtools-rh-che-rh-che-prcheck-dev.rdu2c.fabric8.io/buildTimeTrend
- Dependency image build job: https://ci.centos.org/view/Devtools/job/devtools-rh-che-prcheck-build-dep/buildTimeTrend
