from __future__ import absolute_import
from __future__ import print_function
from __future__ import unicode_literals

import os
import uuid
import websocket
from locust import HttpLocust, TaskSet, task


class GetWorkspacesTasks (TaskSet):

    payload = '{"jsonrpc":"2.0","method":"broker/log","params":{' \
              '"time":"2018-11-02T10:54:51Z","text":"!!!!!!!!!!!!!! MESSAGE ' \
              '!!!!!!!","runtimeId":{' \
              '"ownerId":"67e486ff-0ea3-41b9-b5a1-201ea7e3d16e",' \
              '"envName":"default",' \
              '"workspaceId":"workspace5lvq0t1s5eb7uoo3"},' \
              '"machineName":"ws/theia-ide"}} '
    token = os.environ.get("LOCUST_RHCHE_ACTIVE_TOKEN")

    def on_start(self):
        self.locust.userID = str(uuid.uuid4())
        uri = 'ws://rhche-rhche.192.168.99.100.nip.io/api/websocket?token=' \
              + self.token
        print("Opening websocket connection from user ["
              + self.locust.userID + "].")
        ws = websocket.WebSocket()
        ws.connect(uri)
        print("Connection open.")
        self.ws = ws
        #self.login()

    # def login(self):
        #self.client.post("https://developers.redhat.com/auth/realms/rhd"
        #                 "/protocol/openid-connect/auth?scope=openid&state"
        #                 "=F3JmHdXop_jhkR6z0RUGUNJkjpaykDtp-nTuk4cGQ-8"
        #                 ".fabric8-online-platform&response_type=code"
        #                 "&client_id=fabric8-online&redirect_uri=https%3A%2F"
        #                 "%2Fsso.prod-preview.openshift.io%2Fauth%2Frealms"
        #                 "%2Ffabric8%2Fbroker%2Frhd%2Fendpoint")

    @task
    def get_websocket_ping(self):
        self.ws.ping()

    @task
    def get_workspaces(self):
        # print("Sending payload. [" + self.locust.userID + "]")
        self.ws.send(self.payload)
        print("Payload sent. [" + self.locust.userID + "]")

    @task
    def get_main_page(self):
        self.client.get("")


class LocustManager (HttpLocust):
    task_set = GetWorkspacesTasks
    min_wait = 100
    max_wait = 5000
