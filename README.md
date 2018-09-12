[![Build Status](https://ci.centos.org/buildStatus/icon?job=devtools-rh-che-build-che-credentials-master)](https://ci.centos.org/view/Devtools/job/devtools-rh-che-build-che-credentials-master/)

# Eclipse Che on OpenShift 

## Table Of Contents

* [What is the Red Hat Che distribution](#what-is-the-redhat-che-distribution)
* [How to build it](#how-to-build-it)
* [How to deploy or roll-update on OpenShift](#how-to-deploy-or-roll-update-che-on-openshift-container-platform-minishift-or-osio)
* [Further details](#further-details)

## What is the Red Hat Che distribution

The Red Hat distribution of Eclipse Che is a Red Hat specific packaging of Che assemblies
that adds some Red Hat specific plugins / behaviors up to the standard upstream Che
distribution. The Red Hat distribution powers [openshift.io](https://openshift.io) developers workspaces.

Red Hat modifications against the upstream Che include:

* The ability to disable the Dashboard (and remove the *Go to Dashboard* button from the Che IDE)
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

## Further details

### Maven build details

The result of the RedHat Che distribution build will be available at the following location:

    rh-che/assembly/assembly-main/target/eclipse-che-fabric8-1.0.0-SNAPSHOT

Alternatively, if the option to remove the Dashboard has been enabled then the classifier `without-dashboard`
will be used and the result of the RedHat Che distribution build will be available at the following location:

    rh-che/assembly/assembly-main/target/eclipse-che-fabric8-1.0.0-SNAPSHOT-without-dashboard


The build is started by running *maven* in the root of the current git repository,
which is :`rh-che`


#### Maven build parameters

The build relies on the upstream Eclipse Che maven configuration. All the maven options available in Eclipse Che can be used here.
For instance, the profile `fast` will skip all the tests and checks as describe [in the Eclipse Che development documentation](https://github.com/eclipse/che/wiki/Development-Workflow#build-and-run):

    mvn clean install -Pfast


##### Enabling / Disabling the Dashboard

By default the Che Dashboard is part of the Red Hat Che distribution.
Howvever it can removed by with the `-DwithoutDashboard` argument

### Running selenium tests

Rh-Che `functional-tests` are based on selenium framework with Guice injection.
These tests require TestNG profile and listener must be set to `com.redhat.che.selenium.core.RhCheSeleniumTestHandler`

###### VM options
    -Dche.threads=1 -Dche.workspace_pool_size=1 -Dche.host="<RH-Che DEPLOYMENT PATH>" -Dche.port=443
    -Dche.protocol=https -Dgrid.mode=false -Dbrowser=GOOGLE_CHROME -Ddriver.port=9515
    -Ddriver.version="2.35" -DexcludedGroups=github

###### Env variables
    CHE_INFRASTRUCTURE=openshift;CHE_TESTUSER_EMAIL=<email>;CHE_TESTUSER_OFFLINE__TOKEN=<refresh_token>;
    CHE_MULTIUSER=true;CHE_OFFLINE_TO_ACCESS_TOKEN_EXCHANGE_ENDPOINT=https://auth.<OSD URL>/api/token/refresh;
    CHE_TESTUSER_NAME=<username>;CHE_TESTUSER_PASSWORD=<password>