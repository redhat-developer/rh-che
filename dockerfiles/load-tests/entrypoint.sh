#!/usr/bin/env bash

export LOCUST_RHCHE_PROTOCOL=${PROTOCOL}
export LOCUST_RHCHE_BASE_URI=${BASEURL}
export LOCUST_RHCHE_ACTIVE_TOKEN=${TOKEN}

locust -f real-life.py
