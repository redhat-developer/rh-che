import time
import websocket

from locust import Locust, events


class WSLocustClient(object):
    def __init__(self):
        self.ws = websocket.WebSocket()
        self.connected = False

    def connect(self, uuid, uri, task):
        start_time = time.time()
        try:
            self.ws.connect(uri)
        except Exception as e:
            print('Client [' + uuid + '] failed to connect to websocket.')
            total_time = int((time.time() - start_time) * 1000)
            events.request_failure.fire(request_type="connect",
                                        name="Connect_to_websocket_"+task,
                                        response_time=total_time,
                                        exception=e)
            self.ws.close()
            raise e
        else:
            total_time = int((time.time() - start_time) * 1000)
            events.request_success.fire(request_type="connect",
                                        name="Connect_to_websocket_"+task,
                                        response_time=total_time,
                                        response_length=0)
            self.connected = True

    def send_json_rpc(self, payload, task):
        start_time = time.time()
        if not self.connected:
            print("Client is not connected, cannot send request")
            events.request_failure.fire(request_type="send_payload",
                                        name="Send_payload_"+task,
                                        response_time=0,
                                        exception="Not connected.")
        try:
            self.ws.send(payload)
        except Exception as e:
            print(e)
            total_time = int((time.time() - start_time) * 1000)
            events.request_failure.fire(request_type="send_payload",
                                        name="Send_payload_"+task,
                                        response_time=total_time,
                                        exception=e)
            raise e
        else:
            total_time = int((time.time() - start_time) * 1000)
            events.request_success.fire(request_type="send_payload",
                                        name="Send_payload_"+task,
                                        response_time=total_time,
                                        response_length=len(payload))

    def disconnect(self):
        self.ws.close()


class WSLocust(Locust):
    def __init__(self, *args, **kwargs):
        super(Locust, self)
        self.client = WSLocustClient()
