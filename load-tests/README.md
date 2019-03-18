# Locust based load tests for RH-Che/CReW

## Prepare environment

It is recommended to do python-pip installations in a separate python virtual-env.

### Installing dependencies

    ```{allowEmpy=true}
    yum install python-pip --assumeyes \
    pip install locust \
    pip install websocket-client
    ```

## Running the tests

```{allowEmpty=true}
cd rh-che/load-tests && \
LOCUST_RHCHE_ACTIVE_TOKEN="<active_token>" \
LOCUST_RHCHE_PROTOCOL="<http|https>" \
LOCUST_RHCHE_BASE_URI="rhche-rhche.192.168.99.100.nip.io" \
locust -f simple.py
```

Alternatively you can use docker to run these tests with all the dependencies pre-packaged inside:

```{allowEmpty=true}
docker run -p 8089:8089 \
-e "TOKEN=<active_token>" \
-e "BASEURL=rhche-rhche.192.168.99.100.nip.io" \
-e "PROTOCOL=<http|https>" \
quay.io/tdancs/rhche-locust-load-tests
```

This should launch a local locust instance on your host machine.
You should be able to open your browser and go to <http://127.0.0.1:8089/>

From here on now it should be as simple as clicking a button :)
Set number of locusts to spawn, set the hatch rate and watch the world burn.
