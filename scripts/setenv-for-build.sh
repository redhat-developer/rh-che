#!/bin/bash

export CHE_IMAGE_REPO=rhche/che-server
export CHE_IMAGE_TAG=nightly
export GITHUB_REPO="${HOME}/github/che"
eval $(minishift docker-env)
