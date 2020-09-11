# Theia tests of Che 7

These tests are written in typescript and serves to test functionality of che.openshift.io and che.prod-preview.openshift.io. It expects MultiUser setup of che. 
Tests are based on upstream Che 7 Theia tests, which can be found [here](https://github.com/eclipse/che/tree/master/e2e). 

## Running the tests
There are two ways how to run them - using docker file with pre-installed dependencies or using a script. 

### Running tests via Docker
Docker image is pushed here: quay.io/openshiftio/rhchestage-rh-che-e2e-tests. To run this image, you need to add some parameters:
```
docker run 
-e USERNAME=<username>
-e PASSWORD=<password>
-e URL=<url of running Hosted Che>
--shm-size=256m
quay.io/openshiftio/rhchestage-rh-che-e2e-tests:<version>
```

If you would like to run tests with your source code, you can mount a volume to ` /tmp/rh-che/local_tests `.
```
-v /local/full/path/to/your/source/code/:/tmp/rh-che/local_tests
```

If you would like to save screenshots and reports, you can mount a volume to ` /root/rh-che/e2e-saas/report/ `. When you run your local test, you should replace `e2e-saas` by `local_tests`.

Default testsuite is `test-java-maven` suite. It can be changed by setting parametr `TEST_SUITE` in docker run command.

## Test flow
### PR check
The tests are end-to-end tests that should represent Happy path through a product. Current tests are testing Java Maven on a PR check. 

Pre-test:
- Login

Java Maven flow:

- Create Che 7 workspace based on Java Maven devfile with console-java-simple project
- Create and open workspace
- Build application
- Close the terminal task
- Run application
- Close the terminal tesk
- Check Java Language Server
  - Open file in editor
  - Check suggestion invoking
  - Error highlighting
  - Autocomplete
  - Codenavigation
- Stop workspace
- Delete workspace

### Pre-release test suite
The pre-release test suite includes devfiles that are implemented upstream. See https://github.com/eclipse/che/tree/master/tests/e2e/tests/devfiles.
Those tests can be run by commenting to a PR:

```
<url of a hosted che>
[pre-release-tests]
```

## QE jobs and images
| job purpose | image used | description | preserve |
| ----------- | ---------- | ----------- | -------- |
| [PR check](devtools-rh-che-rh-che-prcheck-dev.rdu2c.fabric8.io)  | server: [own image](https://quay.io/openshiftio/rhel-rhchestage-rh-che-automation) with tag `rhcheautomation-<pr_id>`<br>test: [test image with versino as a tag](https://quay.io/repository/openshiftio/rhchestage-rh-che-e2e-tests) | Deploys rh-che server based on changes in the PR.<br>Insert code changes from the PR to test image based on appropriate version. Runs Java Maven test. | no |
| [Openshiftio PR check](devtools-saas-openshiftio-pr-check-rh-che) | server: che.prod-preview.openshift.io<br>test: [test image with versino as a tag](https://quay.io/repository/openshiftio/rhchestage-rh-che-e2e-tests) | Cheks prod-preview when a PR is sent to the repository. Runs Java Maven test. | no |
| Route tests | server: production or prod-preivew <br>test: no image | Tests the time which it takes for a route to be exposed | no |
| Periodic tests | server: production or prod-preivew<br>test: [test image with versino as a tag](https://quay.io/repository/openshiftio/rhchestage-rh-che-e2e-tests) | Test Java Maven test on each cluster - production and prod-preview. Runs 4 times a day on each cluster. | yes |
| [Compatiility test](https://ci.centos.org/view/Devtools/job/devtools-rh-che-rh-che-compatibility-test-dev.rdu2c.fabric8.io/524/console) | same images like in PR check | Builds rh-che based on nightly upstream. Test image is rebuilt based on upstream nightly. | no |
| [Rollout test](https://ci.centos.org/view/Devtools/job/devtools-rh-che-rollout-test-devtools-dev.ext.devshift.net/681/console) | server: default<br>test: no image | Deploys rh-che, starts a workspace, runs a rollout, tests if workspace is still in the RUNNING state. Runs once a day. | no |
| Workspace startup test | server: prod and prod-preview<br>test: no image | Runs a devfile, measures time for the workspace and IDE to be loaded. | no |
| Mount volume tests | server: prod and prod-preview<br>test: no image | Used to test mounting of a volume. Job was temporarily dislabed and was not enabled again. | no |
| [Pre-release tests](https://ci.centos.org/view/Devtools/job/devtools-rh-che-pre-release-test/) | server: specified by URL parameter<br> test: [test image with versino as a tag](https://quay.io/repository/openshiftio/rhchestage-rh-che-e2e-tests) | Run all tests that we have against specified environment using prod-preview test account. | yes |
| [Openshiftio pre-release tests](devtools-saas-openshiftio-pre-release-test) | server: che.prod-preview.openshift.io<br> test: [test image with versino as a tag](https://quay.io/repository/openshiftio/rhchestage-rh-che-e2e-tests) |  Run all tests that we have against prod-preview environment. | no |
| [Build and push test image](https://ci.centos.org/view/Devtools/job/devtools-rh-che-build-func-dep/) | server: not used<br>test: [built, tagged and pushed](https://quay.io/repository/openshiftio/rhchestage-rh-che-e2e-tests)| When a PR is merged in rh-che repo, the test image is rebuild and pushed. | no |
| [Cleanup job](https://ci.centos.org/view/Devtools/job/devtools-rh-che-prcheck-cleanup/) | server: no server<br>test: no image | Goes through deployments on dev cluster and preserves only the ones with open PRs (or whitelisted). | no |
