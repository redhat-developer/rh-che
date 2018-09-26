[![Build Status](https://ci.centos.org/buildStatus/icon?job=devtools-rh-che-build-che-credentials-master)](https://ci.centos.org/view/Devtools/job/devtools-rh-che-build-che-credentials-master/)

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

## How to build it

*See*: [the Dev guide](./dev-guide.adoc)

## PR-Check details

### PR-Check workflow

DEP IMAGE: [![Build Status](https://ci.centos.org/view/Devtools/job/devtools-rh-che-prcheck-build-dep-dev.rdu2c.fabric8.io.openshift.io/badge/icon)](https://ci.centos.org/view/Devtools/job/devtools-rh-che-prcheck-build-dep-dev.rdu2c.fabric8.io.openshift.io/)

[![PR-Check sequence diagram](https://raw.githubusercontent.com/redhat-developer/rh-che/master/documentation/rh-che-prcheck/pr_check_general_squence_diagram.svg)](https://raw.githubusercontent.com/redhat-developer/rh-che/master/documentation/rh-che-prcheck/pr_check_general_squence_diagram.plantuml)

- This diagram shows the general logic of PR-Check workflow from opening a PR to merge.
- PR_Check job : https://ci.centos.org/view/Devtools/job/devtools-rh-che-rh-che-prcheck-dev.rdu2c.fabric8.io/buildTimeTrend
- Dependency image build job: https://ci.centos.org/view/Devtools/job/devtools-rh-che-prcheck-build-dep-dev.rdu2c.fabric8.io.openshift.io/buildTimeTrend
