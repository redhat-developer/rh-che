from __future__ import absolute_import
from __future__ import print_function
from __future__ import unicode_literals

import os
import uuid
import time

from locust import TaskSet, task
from locust.exception import StopLocust

from WSLocust import *


class GetWorkspacesTasks (TaskSet):

    FAILED_TO_CONNECT = 1

    with open("websocket_init.json") as websocket_init:
        for line in websocket_init:
            payload, size, timestamp_ = line.decode('utf-8').split("\t")
            timestamp = time.fromisoformat(timestamp_)
            print("loaded values: " + payload + "|" + size + "|" + timestamp)

    token = os.environ.get("LOCUST_RHCHE_ACTIVE_TOKEN")
    base_uri = os.environ.get("LOCUST_RHCHE_BASE_URI")
    protocol = os.environ.get("LOCUST_RHCHE_PROTOCOL")
    uuid = str(uuid.uuid4())
    if protocol == "https":
        uri = 'wss://' + base_uri + '/api/websocket?token=' + token
    elif protocol == "http":
        uri = 'ws://' + base_uri + '/api/websocket?token=' + token
    else:
        raise ValueError('LOCUST_RHCHE_PROTOCOL env variable not set.'
                         'Must contain either \'http\' or \'https\'')

    # def on_start(self):
    #     print("Opening websocket connection from user [" + self.uuid + "].")
    #     try:
    #         self.locust.client.connect(self.uuid, self.uri)
    #     except:
    #         raise StopLocust("Failed to connect to master")
    #
    # @task
    # def get_workspaces(self):
    #     self.locust.client.send_json_rpc(self.payload)


class LocustManager (WSLocust):
    task_set = GetWorkspacesTasks
    min_wait = 100
    max_wait = 5000
