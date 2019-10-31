def ACTIVE_USER_TOKENS_RAW
def RELATIVE_PATH = ".ci/workspace-startup"
def PROPERTIES_FILE = "users.properties"
def EXPORT_FILE = "exports"
def TOKENS_FILE = "tokens.txt"
def CHE_SERVER_URL = "https://che.prod-preview.openshift.io"
def USER_TOKENS = ""
def JOB_TIMEOUT = "${PIPELINE_TIMEOUT}".toInteger()

def silent_sh(cmd) {
    sh('#!/bin/sh -e\n' + cmd)
}

pipeline {
    agent { label 'osioche-crw-jenkins-node' }
    environment {
        USERS_PROPERTIES_FILE = credentials('${USERS_PROPERTIES_FILE_ID}')
        LOG_DIR = ""
        ZABBIX_FILE = ""
    }
    stages {
        stage ("Prepairing environment") {
            steps {
                echo ("Getting user active tokens")
                    sh "./${RELATIVE_PATH}/get_active_tokens.sh \"${TOKENS_FILE}\""
                    script {
                        USER_TOKENS = sh(returnStdout:true, script:"cat ${TOKENS_FILE}").trim()
                    }
            }
        }
        stage ("Running worksapce test") {
            options {
                timeout(time: JOB_TIMEOUT, unit: 'MINUTES') 
            }
            steps {
                script {
                    LOG_DIR = sh(returnStdout:true, script:"echo ${JOB_BASE_NAME}-${BUILD_NUMBER}").trim()
                    ZABBIX_FILE = "${LOG_DIR}/${JOB_BASE_NAME}-${BUILD_NUMBER}-zabbix.log"
                    echo ("Creating logs directory: ${LOG_DIR}")
                }
                dir ("${LOG_DIR}") {
                    silent_sh """
                    wget https://che-devfile-registry.prod-preview.openshift.io/devfiles/nodejs/devfile.yaml -O ../${RELATIVE_PATH}/nodejs_eph.yaml
                    export USER_TOKENS="$USER_TOKENS"
                    export CYCLES_COUNT="$CYCLES_COUNT"
                    export CHE_STACK_FILE="../${RELATIVE_PATH}/nodejs_eph.yaml"
                    export ZABBIX_SERVER="${ZABBIX_SERVER}"
                    export ZABBIX_PORT="${ZABBIX_PORT}"
                    export ZABBIX_EPHEMERAL="true"
                    export START_SOFT_FAILURE_TIMEOUT="${START_SOFT_FAILURE_TIMEOUT}"
                    export START_HARD_FAILURE_TIMEOUT="${START_HARD_FAILURE_TIMEOUT}"
                    export STOP_SOFT_FAILURE_TIMEOUT="${STOP_SOFT_FAILURE_TIMEOUT}"
                    export STOP_HARD_FAILURE_TIMEOUT="${STOP_HARD_FAILURE_TIMEOUT}"
                    locust -f "../${RELATIVE_PATH}/osioperf.py" --no-web -c `echo -e "$USER_TOKENS" | wc -l` -r 1 --only-summary --csv="$EXPORT_FILE"
                    """
                }
            }
        }
        stage ("Generating zabbix report") {
            when {
                expression { currentBuild.currentResult == "SUCCESS" }
            }
            steps {
                script {
                    def long DATETIME_TAG = System.currentTimeMillis() / 1000L
                    def test_log_file = readFile("${LOG_DIR}/${EXPORT_FILE}_requests.csv")
                    def BASE_DIR = pwd()
                    def lines = test_log_file.split("\n")
                    sh "touch ${ZABBIX_FILE}"
                    for (line in lines) {
                        def elements = line.split(",")
                        def method = elements[0].replace("\"","")
                        if (method.equals("Method") || method.equals("None")) {
                            continue
                        }
                        def name_host_metric = elements[1].replace("\"","").split("_")
                        def name = name_host_metric[0]
                        if (name.equals("getWorkspaces") | name.equals("getWorkspaceStatus")) {
                            continue
                        }
                        def host = name_host_metric[1]
                        def int average = elements[5]
                        def output_basestring = "qa-".concat(host).concat(" ")
                                                .concat("che-start-workspace.").concat(method).concat(".")
                                                .concat(name).concat(".eph")
                        def output = output_basestring.concat(" ")
                                     .concat(String.valueOf(DATETIME_TAG)).concat(" ")
                                     .concat(String.valueOf(average))
                        silent_sh "echo $output >> ${ZABBIX_FILE}"
                    }
                }
            }
        }
        stage ("Reporting to zabbix") {
            when {
                expression { currentBuild.currentResult == "SUCCESS" }
            }
            steps {
                silent_sh "zabbix_sender -vv -i ${ZABBIX_FILE} -T -z ${ZABBIX_SERVER} -p ${ZABBIX_PORT}"
            }
        }
    }
    post("Cleanup") {
        always {
            echo "Current build status: ${currentBuild.currentResult}"
            deleteDir()
        }
        aborted {
            echo "Jenkins job timed out."
        }
        failure {
            echo "Job failed, resetting environment for all users."
            // mail to: team@example.com, subject: 'The Pipeline failed :('
            script {
                def user_tokens = USER_TOKENS.split("\n")
                for (user in user_tokens) {
                    def user_array = user.split(";")
                    def active_token = user_array[0]
                    def username = user_array[1]
                    echo "Resetting environment for $username"
                    def environment = user_array[2]
                    def reset_api_url = "https://api.openshift.io/api/user/services"
                    if (environment.equals("prod-preview")) {
                        reset_api_url = "https://api.prod-preview.openshift.io/api/user/services"
                    }
                    silent_sh "curl -s -X DELETE --header 'Content-Type: application/json' --header 'Authorization: Bearer ${active_token}' ${reset_api_url}"
                    silent_sh "curl -s -X PATCH --header 'Content-Type: application/json' --header 'Authorization: Bearer ${active_token}' ${reset_api_url}"
                }
            }
        }
    }
}
