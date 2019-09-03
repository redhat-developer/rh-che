import groovy.json.JsonOutput
import groovy.json.JsonParser

pipeline {
    agent { label 'osioche-crw-jenkins-node' }
    environment {
        ZABBIX_USER_PASSWORD_CREDENTIALS = credentials('${ZABBIX_CREDENTIALS_ID}')
        ZABBIX_REPORTER_USER = ""
        ZABBIX_REPORTER_PASSWORD = ""
        SLACK_URL = credentials('${SLACK_URL_ID}')
    }
    stages {
        stage ("Get credentials from jenkins") {
            steps {
                script {
                    zabbix_user_password_credentials_array = ZABBIX_USER_PASSWORD_CREDENTIALS.split(":")
                    ZABBIX_REPORTER_USER = zabbix_user_password_credentials_array[0]
                    ZABBIX_REPORTER_PASSWORD = zabbix_user_password_credentials_array[1]
                }
            }
        }
        stage ("Build zabbix reporter java application") {
            steps {
                dir (".ci/workspace-startup/start-workspace-reporter") {
                    sh "mvn clean install package"
                }
            }
        }
        stage ("Run zabbix reporter") {
            steps {
                dir (".ci/workspace-startup/start-workspace-reporter/target") {
                    sh '#!/bin/sh -e\n' + "ZABBIX_USERNAME=${ZABBIX_REPORTER_USER} ZABBIX_PASSWORD=${ZABBIX_REPORTER_PASSWORD} ZABBIX_URL=${ZABBIX_URL} SLACK_URL=${SLACK_URL} SLACK_CHANNEL=${SLACK_CHANNEL} java -jar start-workspace-reporter-1.0-SNAPSHOT-jar-with-dependencies.jar"
                }
            }
        }
    }
    post ("Cleanup") {
        always {
            deleteDir()
        }
    }
}
