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
    agent { label 'osioche-qe-workspace-startup-pvc-slave' }
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
                    curl -Lso ../${RELATIVE_PATH}/nodejs_pvc_preview.yaml https://che-devfile-registry.prod-preview.openshift.io/devfiles/nodejs/devfile.yaml
                    curl -Lso ../${RELATIVE_PATH}/nodejs_pvc_prod.yaml https://che-devfile-registry.openshift.io/devfiles/nodejs/devfile.yaml
                    sed -i.bak 's/persistVolumes: \'false\'/persistVolumes: \'true\'/g' ../${RELATIVE_PATH}/nodejs_pvc_preview.yaml
                    sed -i.bak 's/persistVolumes: \'false\'/persistVolumes: \'true\'/g' ../${RELATIVE_PATH}/nodejs_pvc_prod.yaml
                    export USER_TOKENS="$USER_TOKENS"
                    export CYCLES_COUNT="$CYCLES_COUNT"
                    export IS_EPHEMERAL="false"
                    export CHE_STACK_FILES_PATH="../${RELATIVE_PATH}/"
                    export ZABBIX_SERVER="${ZABBIX_SERVER}"
                    export ZABBIX_PORT="${ZABBIX_PORT}"
                    export START_SOFT_FAILURE_TIMEOUT="${START_SOFT_FAILURE_TIMEOUT}"
                    export START_HARD_FAILURE_TIMEOUT="${START_HARD_FAILURE_TIMEOUT}"
                    export STOP_SOFT_FAILURE_TIMEOUT="${STOP_SOFT_FAILURE_TIMEOUT}"
                    export STOP_HARD_FAILURE_TIMEOUT="${STOP_HARD_FAILURE_TIMEOUT}"
                    locust -f "../${RELATIVE_PATH}/osioperf.py" --no-web -c `echo -e "$USER_TOKENS" | wc -l` -r 1 --only-summary --csv="$EXPORT_FILE"
                    """
                }
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
