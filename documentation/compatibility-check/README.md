# Compatibility test

Current status: [![Build Status](https://ci.centos.org/buildStatus/icon?job=devtools-rh-che-rh-che-compatibility-test-dev.rdu2c.fabric8.io)](https://ci.centos.org/view/Devtools/job/devtools-rh-che-rh-che-compatibility-test-dev.rdu2c.fabric8.io/) 

This test checks compatibility of latest upstream Che SNAPSHOT and current version of Rh-che. There is a job running once a day: 
https://ci.centos.org/view/Devtools/job/devtools-rh-che-rh-che-compatibility-test-dev.rdu2c.fabric8.io/.

## How does it work

The test consists of two phases - one for maintaining branch and PR which are supposed to track changes needed for compatibility fix. Second phase is related with test itself - it creates 
new docker image, pushes it and runs test.

### Maintaining phase

At the beginning the latest upstream SNAPSHOT version is checked. 

#### Check branch
When version is gotten, the branch for tracking changes of fixes needed for compatibility is checked. If such a branch does not exist, new branch is created from master and the version of Che 
parent is changed. The branch is named in this format: ```upstream-check-<SNAPSHOT>``` where <SNAPSHOT> is current latest SNAPSHOT of upstream Che. The specific branch than can look like 
that: ```upstream-check-6.16.0-SNAPSHOT```.

#### Check pull request
For each upstream SNAPSHOT there should be pull request to allow easily test changes made for fixing compatibility. When branch is checked/created, test checks if there is a PR for that branch. 
It is found by PR title, which should look like ```Update to <VERSIOn>``` e.g. ```Update to 6.16.0```. 
If there isn't such a pull request, changes are pushed (if there are any) and new PR is created. 

### Testing phase
<<<<<<< HEAD
The docker image is build from current version of code. The docker image is pushed to quay.io with two tags. Tag ```upstream-check-latest``` is for running test. Another tag 
```upstream-check-{upstream_hash}-{downastream_hash}``` should serve for developers to found exact version in easy way by knowing commits short hashes. 
```upstream-hash``` is the short hash of last commit to upstream that was used in that SNAPSHOT.
```downstream-hash``` is the short hash of last commit to downstream. That should ease the investigation if the compatibility test fails.

If test fails, it sends comment to related PR with link to the job console output on Jenkins.
=======

The docker image is build from current version of code. The docker image is pushed to quay.io with two tags. Tag ```upstream-check-latest``` is for running test. Another tag 
```upstream-check-{upstream_hash}-{downastream_hash}``` is mainly for testing purposes. ```upstream-hash``` is the short hash of last commit to upstream that was used in that SNAPSHOT.
```downstream-hash``` is the short hash of last commit to downstream. That should ease the investigation if the compatibility test fails.

If test fails, it sends comment to related PR with link to the related job run.
>>>>>>> Enhancing compatibility test.
