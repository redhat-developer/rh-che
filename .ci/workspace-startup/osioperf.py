import json, os, threading
from locust import Locust, HttpLocust, TaskSet, task, events
from locust.clients import HttpSession
from locust.exception import StopLocust
from datetime import datetime
import time

_users = -1
_userTokens = []
_userEnvironment = []
_userNames = []
_currentUser = 0
_userLock = threading.RLock()
_stackDefinitionFilePath = os.getenv("CHE_STACK_FILE")
if (not os.path.isfile(_stackDefinitionFilePath)):
  raise IOError("Stack input file does not exist: " + _stackDefinitionFilePath)
if (not os.access(_stackDefinitionFilePath, os.R_OK)):
  raise IOError("Cannot read from the stack file: " + _stackDefinitionFilePath)
_stackDefinitionFile = open(_stackDefinitionFilePath, "r")
_stackDefinitionFileRaw = _stackDefinitionFile.read()
_zabbixServer = os.getenv("ZABBIX_SERVER")
_zabbixPort = os.getenv("ZABBIX_PORT")
_zabbixEphemeral = False if os.getenv("ZABBIX_EPHEMERAL") == None else True


class TokenBehavior(TaskSet):
  id = ""
  openshiftToken = ""
  cluster = ""
  soft_start_failure_cmd = ""
  soft_stop_failure_cmd = ""
  hard_start_failure_cmd = ""
  hard_stop_failure_cmd = ""
  cycles = 0
  cyclesMax = 1

  def on_start(self):
    self.log("Username:" + self.locust.taskUserName
            #  + " Token:" + self.taskUserToken
             + " Environment:" + self.locust.taskUserEnvironment)
    if (os.getenv("CYCLES_COUNT") != None):
      self.cyclesMax = int(os.getenv("CYCLES_COUNT"))
    self.setOsTokenAndCluster()
    self.deleteExistingWorkspaces()

  def setOsTokenAndCluster(self):
    self.log("Getting info about user ")
    username = self.locust.taskUserName
    # Set URLs based on environment
    if "prod-preview" in self.locust.taskUserEnvironment:
      userInfoURL = "https://auth.prod-preview.openshift.io/api/userinfo"
      usersURL = "https://api.prod-preview.openshift.io/api/users?filter[username]="
      tokensURL = "https://auth.prod-preview.openshift.io/api/token?for="
    else:
      userInfoURL = "https://auth.openshift.io/api/userinfo"
      usersURL = "https://api.openshift.io/api/users?filter[username]="
      tokensURL = "https://auth.openshift.io/api/token?for="
    if "@" in self.locust.taskUserName:
      userInfo = self.client.get(userInfoURL,
                                 headers={
                                   "Authorization": "Bearer " + self.locust.taskUserToken},
                                 name="getUsername", catch_response=True)
      username = (userInfo.json())['preferred_username']
    self.locust.taskUserName = username
    infoResponse = self.client.get(
        usersURL + self.locust.taskUserName,
        name="getUserInfo", catch_response=True)
    infoResponseJson = infoResponse.json()
    self.cluster = infoResponseJson['data'][0]['attributes']['cluster']
    if "prod-preview" in self.locust.taskUserEnvironment:
      self.clusterName = self.cluster.split(".")[1] + "-preview"
    else:
      self.clusterName = self.cluster.split(".")[1]
    os_token_response = self.client.get(
        tokensURL + self.cluster,
        headers={"Authorization": "Bearer " + self.locust.taskUserToken},
        name="getOpenshiftToken", catch_response=True)
    os_token_response_json = os_token_response.json()
    self.openshiftToken = os_token_response_json["access_token"]
    self.soft_start_failure_cmd = "zabbix_sender -vv" + \
      " -z " + _zabbixServer + \
      " -p " + _zabbixPort + \
      " -s qa-" + self.clusterName + \
      " -k " + str("che-start-workspace.FAIL.start.soft.failure.eph" if _zabbixEphemeral else "che-start-workspace.FAIL.start.soft.failure")
    self.soft_stop_failure_cmd = "zabbix_sender -vv" + \
      " -z " + _zabbixServer + \
      " -p " + _zabbixPort + \
      " -s qa-" + self.clusterName + \
      " -k " + str("che-start-workspace.FAIL.stop.soft.failure.eph" if _zabbixEphemeral else "che-start-workspace.FAIL.stop.soft.failure")
    self.hard_start_failure_cmd = "zabbix_sender -vv" + \
      " -z " + _zabbixServer + \
      " -p " + _zabbixPort + \
      " -s qa-" + self.clusterName + \
      " -k " + str("che-start-workspace.FAIL.start.hard.failure.eph" if _zabbixEphemeral else "che-start-workspace.FAIL.start.hard.failure")
    self.hard_stop_failure_cmd = "zabbix_sender -vv" + \
      " -z " + _zabbixServer + \
      " -p " + _zabbixPort + \
      " -s qa-" + self.clusterName + \
      " -k " + str("che-start-workspace.FAIL.stop.hard.failure.eph" if _zabbixEphemeral else "che-start-workspace.FAIL.stop.hard.failure")

  @task
  def createStartDeleteWorkspace(self):
    print("\n["+self.clusterName+"] Running workspace start test "+str(self.cycles + 1)+" of "+str(self.cyclesMax)+"\n")
    self.log("Checking if there are some removing pods before creating and running new workspace.")
    self.waitUntilDeletingIsDone()
    self.id = self.createWorkspace()
    self.wait()
    self._reset_timer()
    self.startWorkspace()
    self.wait()
    self.waitForWorkspaceToStart()
    self._reset_timer()
    self.stopWorkspaceSelf()
    self.waitForWorkspaceToStopSelf()
    self.wait()
    self.deleteWorkspaceSelf()
    if (self.cycles == (self.cyclesMax - 1)):
      raise StopLocust("Tests finished, unable to set Locust to run set number of times (https://github.com/locustio/locust/pull/656), issuing hard-stop.")
    self.cycles += 1

  def createWorkspace(self):
    self.log("Creating workspace")
    now_time_ms = "%.f" % (time.time() * 1000)
    json = _stackDefinitionFileRaw.replace("WORKSPACE_NAME", now_time_ms)
    response = self.client.post("/api/workspace/devfile", headers={
      "Authorization": "Bearer " + self.locust.taskUserToken,
      "Content-Type": "text/yaml"}, 
     name="createWorkspace_"+self.clusterName, data=json, catch_response=True)
    self.log("Create workspace server api response:" + str(response.ok))
    try:
      if not response.ok:
        self.log("Can not create workspace: [" + str(response.content) + "]")
        response.failure("Can not create workspace: [" + str(response.content) + "]")
      else:
        resp_json = response.json()
        self.log("Workspace with id " 
                 + resp_json["id"] 
                 + " was successfully created.")
        response.success()
        return resp_json["id"]
    except ValueError:
      response.failure("Failed to process response value - createWorkspace")

  def startWorkspace(self):
    self.log("Starting workspace id " + str(self.id))
    response = self.client.post("/api/workspace/" + self.id + "/runtime",
                                headers={
                                  "Authorization": "Bearer " + self.locust.taskUserToken},
                                name="startWorkspace_"+self.clusterName, catch_response=True)
    try:
      content = response.content
      if not response.ok:
        response.failure("Got wrong response: [" + str(content) + "]")
      else:
        response.success()
    except ValueError:
      response.failure("Failed to process response value - startWorkspace")

  def waitForWorkspaceToStart(self):
    timeout_in_seconds = 300 if os.getenv("START_HARD_FAILURE_TIMEOUT") == None else int(os.getenv("START_HARD_FAILURE_TIMEOUT"))
    soft_timeout_seconds = 60 if os.getenv("START_SOFT_FAILURE_TIMEOUT") == None else int(os.getenv("START_SOFT_FAILURE_TIMEOUT"))
    isSoftFailure = False
    workspace_status = self.getWorkspaceStatusSelf()
    while workspace_status != "RUNNING":
      now = time.time()
      elapsed_time = int(now - self.start)
      if (workspace_status == "STOPPED"):
        events.request_failure.fire(request_type="REPEATED_GET",
                                    name="startWorkspace_"+self.clusterName,
                                    response_time=self._tick_timer(),
                                    exception="Workspace became STOPPED after " 
                                              + str(elapsed_time)
                                              + " seconds.")
        self.log("Workspace " + self.id + " became STOPPED after " 
                 + str(elapsed_time) + " seconds.")
        os.system(self.hard_start_failure_cmd+" -o 1 >/dev/null 2>&1")
        return
      if elapsed_time > soft_timeout_seconds and isSoftFailure == False:
        self.log("Workspace startup on "+self.clusterName+" failed with soft failure.")
        os.system(self.soft_start_failure_cmd+" -o 1 >/dev/null 2>&1")
        isSoftFailure = True
      if elapsed_time > timeout_in_seconds:
        events.request_failure.fire(request_type="REPEATED_GET",
                                    name="startWorkspace_"+self.clusterName,
                                    response_time=self._tick_timer(),
                                    exception="Workspace wasn't able to start in " 
                                              + str(elapsed_time)
                                              + " seconds.")
        self.log("Workspace " + self.id + " wasn't able to start in " 
                 + str(elapsed_time) + " seconds.")
        os.system(self.hard_start_failure_cmd+" -o 1 >/dev/null 2>&1")
        return
      self.log("Workspace id " + self.id + " is still not in state RUNNING ["
               + workspace_status +"] {" + str(elapsed_time) + " of " + str(timeout_in_seconds) + "}")
      self.wait()
      workspace_status = self.getWorkspaceStatusSelf()
    self.log("Workspace id " + self.id + " is RUNNING")
    events.request_success.fire(request_type="REPEATED_GET",
                                name="startWorkspace_"+self.clusterName,
                                response_time=self._tick_timer(),
                                response_length=0)
    if (isSoftFailure == False):
      os.system(self.soft_start_failure_cmd+" -o 0 >/dev/null 2>&1")
    os.system(self.hard_start_failure_cmd+" -o 0 >/dev/null 2>&1")

  def waitForWorkspaceToStopSelf(self):
    self.waitForWorkspaceToStop(self.id)

  def waitForWorkspaceToStop(self, id):
    timeout_in_seconds = 60 if os.getenv("STOP_HARD_FAILURE_TIMEOUT") == None else int(os.getenv("STOP_HARD_FAILURE_TIMEOUT"))
    soft_timeout_seconds = 5 if os.getenv("STOP_SOFT_FAILURE_TIMEOUT") == None else int(os.getenv("STOP_SOFT_FAILURE_TIMEOUT"))
    isSoftFailure = False
    workspace_status = self.getWorkspaceStatus(id)
    while workspace_status != "STOPPED":
      now = time.time()
      elapsed_time = int(now - self.start)
      if elapsed_time > soft_timeout_seconds and isSoftFailure == False:
        self.log("Workspace stopping on "+self.clusterName+" failed with soft failure.")
        os.system(self.soft_stop_failure_cmd+" -o 1 >/dev/null 2>&1")
        isSoftFailure = True
      if elapsed_time > timeout_in_seconds:
        events.request_failure.fire(request_type="REPEATED_GET",
                                    name="stopWorkspace_"+self.clusterName,
                                    response_time=self._tick_timer(),
                                    exception="Workspace wasn't able to stop in " 
                                              + str(elapsed_time)
                                              + " seconds.")
        self.log("Workspace " + self.id + " wasn't able to stop in " 
                 + str(elapsed_time) + " seconds.")
        os.system(self.hard_stop_failure_cmd+" -o 1 >/dev/null 2>&1")
        return
      self.log("Workspace id " + id + " is still not in state STOPPED ["
               + workspace_status +"] {" + str(elapsed_time) + " of " + str(timeout_in_seconds) + "}")
      self.wait()
      workspace_status = self.getWorkspaceStatus(id)
    self.log("Workspace id " + id + " is STOPPED")
    events.request_success.fire(request_type="REPEATED_GET",
                                name="stopWorkspace_"+self.clusterName,
                                response_time=self._tick_timer(),
                                response_length=0)
    if (isSoftFailure == False):
      os.system(self.soft_stop_failure_cmd+" -o 0 >/dev/null 2>&1")
    os.system(self.hard_stop_failure_cmd+" -o 0 >/dev/null 2>&1")

  def stopWorkspaceSelf(self):
    return self.stopWorkspace(self.id)

  def stopWorkspace(self, id):
    self.log("Stopping workspace id " + id)
    status = self.getWorkspaceStatus(id)
    if status == "STOPPED":
      self.log("Workspace " + id + "  is already stopped.")
      return
    response = self.client.delete("/api/workspace/" + id + "/runtime", headers={
                                    "Authorization": "Bearer " + self.locust.taskUserToken},
                                  name="stopWorkspace_"+self.clusterName, catch_response=True)
    try:
      content = response.content
      if not response.ok:
        response.failure("Got wrong response: [" + str(content) + "]")
      else:
        response.success()
    except ValueError:
      response.failure("Failed to process response value - stopWorkspace")

  def deleteWorkspaceSelf(self):
    self.deleteWorkspace(self.id)

  def deleteWorkspace(self, id):
    self.log("Deleting workspace id " + id)
    response = self.client.delete("/api/workspace/" + id, headers={
                                    "Authorization": "Bearer " + self.locust.taskUserToken},
                                  name="deleteWorkspace_"+self.clusterName, catch_response=True)
    try:
      content = response.content
      if not response.ok:
        response.failure("Got wrong response: [" + str(content) + "]")
      else:
        response.success()
    except ValueError:
      response.failure("Failed to process response value - deleteWorkspace")

  def waitUntilDeletingIsDone(self):
    self._reset_timer()
    delay = 10
    failcount = 0
    clusterSubstring = (self.cluster.split("."))[1]
    getPodsResponse = self.client.get(
        "https://console." + clusterSubstring + ".openshift.com/api/v1/namespaces/" + self.locust.taskUserName + "-che/pods",
        headers={"Authorization": "Bearer " + self.openshiftToken},
        name="getPods-"+self.cluster, catch_response=True)
    podsJson = getPodsResponse.json()
    while "rm-" in str(podsJson):
      rmpods = str(podsJson).count("rm-") / 7
      self.log("[" + str(failcount) + "] There are still removing pods running. Trying again after " + str(delay) + " seconds.")
      self.log("Number of removing pods running: " + str(rmpods))
      time.sleep(delay)
      getPodsResponse = self.client.get(
          "https://console." + clusterSubstring + ".openshift.com/api/v1/namespaces/" + self.locust.taskUserName + "-che/pods",
          headers={"Authorization": "Bearer " + self.openshiftToken},
          name="getPods_"+self.clusterName, catch_response=True)
      podsJson = getPodsResponse.json()
      failcount += 1
      # After waiting for a minute, stop the locust test with generating the results
      if (failcount >= 6):
        raise StopLocust("The remove pod failed to finish execution within a minute. Stopping locust thread.")
    events.request_success.fire(request_type="REPEATED_GET",
                                name="deleteWorkspace_"+self.clusterName,
                                response_time=self._tick_timer(),
                                response_length=0)
    self.log("All removing pods finished.")

  def getWorkspaceStatusSelf(self):
    return self.getWorkspaceStatus(self.id)

  def getWorkspaceStatus(self, id):
    response = self.client.get("/api/workspace/" + id, headers={
      "Authorization": "Bearer " + self.locust.taskUserToken},
                               name="getWorkspaceStatus_"+self.clusterName, catch_response=True)
    try:
      resp_json = response.json()
      content = response.content
      if not response.ok:
        response.failure("Got wrong response: [" + str(content) + "]")
      else:
        response.success()
        return resp_json["status"]
    except ValueError:
      response.failure("Failed to process response value - getWorkspaceStatus")

  def _reset_timer(self):
    self.start = time.time()

  def _tick_timer(self):
    self.stop = time.time()
    ret_val = (self.stop - self.start) * 1000
    return ret_val

  def deleteExistingWorkspaces(self):
    self._reset_timer()
    response = self.client.get("/api/workspace/", headers={
                               "Authorization": "Bearer " + self.locust.taskUserToken},
                               name="getWorkspaces_"+self.clusterName, catch_response=True)
    try:
      resp_json = response.json()
      content = response.content
      if not response.ok:
        response.failure("Got wrong response: [" + str(content) + "]")
      else:
        response.success()
        self.log("Removing " + str(len(resp_json)) + " existing workspaces.")
        for wkspc in resp_json:
          wkspid = wkspc["id"]
          if wkspc["status"] != "STOPPED":
            self.stopWorkspace(wkspid)
            self.waitForWorkspaceToStop(wkspid)
          self.deleteWorkspace(wkspid)
    except ValueError:
      response.failure("Failed to process response value - deleteExistingWorkspaces")

  def log(self, message):
    print(self.locust.taskUserName + ": " + message)


class OsioperfLocust(Locust):
  taskUser = -1
  taskUserName = ""
  taskUserToken = ""
  taskUserEnvironment = ""

  def __init__(self, *args, **kwargs):
    global _currentUser, _userLock, _users, _userTokens, _userEnvironment, _userNames
    super(Locust, self)
    TOKEN_INDEX = 0
    USERNAME_INDEX = 1
    ENVIRONMENT_INDEX = 2
    usenv = os.getenv("USER_TOKENS")
    lines = usenv.split('\n')
    _users = len(lines)
    for u in lines:
      up = u.split(';')
      _userTokens.append(up[TOKEN_INDEX])
      _userNames.append(up[USERNAME_INDEX])
      _userEnvironment.append(up[ENVIRONMENT_INDEX])
    # Async lock user to prevent two threads runing with the same user
    _userLock.acquire()
    self.taskUser = _currentUser
    self.taskUserToken = _userTokens[_currentUser]
    self.taskUserName = _userNames[_currentUser]
    self.taskUserEnvironment = _userEnvironment[_currentUser]
    print("Spawning user ["+str(self.taskUser)+"] on ["+self.taskUserEnvironment+"]")
    if _currentUser < _users - 1:
      _currentUser += 1
    else:
      _currentUser = 0
    _userLock.release()
    # User lock released, critical section end
    host = "https://che.prod-preview.openshift.io" if "prod-preview" in self.taskUserEnvironment else "https://che.openshift.io"
    self.client = HttpSession(base_url=host)


class TokenUser(OsioperfLocust):
  task_set = TokenBehavior
  min_wait = 1000
  max_wait = 10000
