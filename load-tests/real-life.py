from __future__ import absolute_import
from __future__ import print_function
from __future__ import unicode_literals

import os
import uuid
import json
import datetime
import dateutil.parser

from locust import TaskSet, task

from WSLocust import *


class GetWorkspacesTasks (TaskSet):

    token = os.environ.get("LOCUST_RHCHE_ACTIVE_TOKEN")
    base_uri = os.environ.get("LOCUST_RHCHE_BASE_URI")
    protocol = os.environ.get("LOCUST_RHCHE_PROTOCOL")

    if base_uri == None:
        raise ValueError('LOCUST_RHCHE_BASE_URI env variable not set. '
                         'Must contain url in format \'che.openshift.io\'')

    def on_start(self):
        self.uuid = str(uuid.uuid4())
        if self.protocol == "https":
            self.uri_prefix = 'wss://'
        elif self.protocol == "http":
            self.uri_prefix = 'ws://'
        else:
            raise ValueError('LOCUST_RHCHE_PROTOCOL env variable not set. '
                             'Must contain either \'http\' or \'https\'')
        if self.token != None:
            self.websocket_uri = self.uri_prefix + self.base_uri + '/api/websocket?token=' + self.token
            self.wsmaster_uri = self.uri_prefix + self.base_uri + '/api/wsmaster?token=' + self.token
        else:
            self.websocket_uri = self.uri_prefix + self.base_uri + '/api/websocket'
            self.wsmaster_uri = self.uri_prefix + self.base_uri + '/api/wsmaster'

    def parse_json_and_send_websocket(self, json_file, task):
        LAST_TIMESTAMP : datetime.datetime
        LAST_TIMESTAMP = None

        for line in json_file:
            # Parsing line and variables
            json_payload_raw = line.rstrip()
            json_rpc_envelope : dict
            json_rpc_envelope = json.loads(json_payload_raw)
            # If it's not a request from the workspace side
            if 'params' in json_rpc_envelope:
                json_payload : dict
                json_payload = json_rpc_envelope.get('params')
                # If the method is installer/log
                if json_rpc_envelope.get('method') == "installer/log":
                    # Process and send payload with a delay
                    if LAST_TIMESTAMP != None:
                        time_delta : datetime.timedelta
                        time_delta = dateutil.parser.parse(json_payload.get('time')) - LAST_TIMESTAMP
                        if (time_delta.total_seconds() > 0):
                            time.sleep(time_delta.total_seconds())
                    self.locust.client.send_json_rpc(line, task)
                    LAST_TIMESTAMP = dateutil.parser.parse(json_payload.get('time'))

    def send_init_websocket_messages(self):
        print("Sending INIT WEBSOCKET messages [" + self.uuid + "].")
        self.locust.client.connect(self.uuid, self.websocket_uri, "websocket_init")
        with open("websocket_init.json") as raw_json:
            self.parse_json_and_send_websocket(raw_json, "websocket_init")
        self.locust.client.disconnect()

    def load_dashboard(self):
        print("Getting dashboard [" + self.uuid + "].")
        self.client.get(self.base_uri)

    def send_after_init_websocket_messages(self):
        print("Sending AFTER INIT WEBSOCKET messages [" + self.uuid + "].")
        self.locust.client.connect(self.uuid, self.websocket_uri, "websocket_after_init")
        with open("websocket_after_init.json") as raw_json:
            self.parse_json_and_send_websocket(raw_json, "websocket_after_init")
        self.locust.client.disconnect()

    @task
    def connect_and_send_websocket_messages(self):
        self.send_init_websocket_messages()
        self.send_after_init_websocket_messages()


class LocustManager (WSLocust):
    task_set = GetWorkspacesTasks
    min_wait = 100
    max_wait = 3000
