# Theia tests of Che 7

These tests are written in typescript and serves to test functionality of che.openshift.io and che.prod-preview.openshift.io. It expects MultiUser setup of che. 
Tests are based on upstream Che 7 Theia tests, which can be found [here](https://github.com/eclipse/che/tree/master/e2e). 

## Running the tests
There are two ways how to run them - using docker file with pre-installed dependencies or using a script. 
*... in progress ...*

## Test flow
The tests are end-to-end tests that should represent Happy path through a product. Current flow is following:

- Login
- Create Che 7 workspace based on Che 7 stack with console-java-simple project
- Add Java Language Support plugin
- Start and open workspace
- Open file in editor
- Stop workspace
- Delete workspace
