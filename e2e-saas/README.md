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
The tests are end-to-end tests that should represent Happy path through a product. Current tests are testing Java Maven stack and Java Vert.x stack. 

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

Java Vert.x test flow is following:

- Create Che 7 workspace based on Java Vert.x devfile with default project
- Create and open workspace
- Build application
- Close the terminal task
- Check Java Language Server
  - Open file in editor
  - Check suggestion invoking
  - Error highlighting
  - Autocomplete
  - Codenavigation
- Stop workspace
- Delete workspace

Java Vert.x and Java Maven tests are saved in upstream and are only reused on Hosted Che side. Login test is used from downstream repo.
