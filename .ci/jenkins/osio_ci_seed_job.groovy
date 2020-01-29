String seedJobName = 'osio_ci_seed_job'
String[] workspaceStartupNames = ['workspace-startup-test-ephemeral', 'workspace-startup-test']
String workspaceStartupReporterJobName = 'workspace-startup-reporter'
String[] mountVolumeNames = ['mount-volume-preview-2a', 'mount-volume-preview-2a-large', 'mount-volume-production-2']

workspaceStartupNames.each { workspaceStartupJobName ->
  pipelineJob("${workspaceStartupJobName}") {
    concurrentBuild(false)
    definition {
      cpsScm {
        scm {
          git {
            remote { url('https://www.github.com/redhat-developer/rh-che.git') }
            branches('*/master')
            scriptPath('.ci/workspace-startup/' + "${workspaceStartupJobName}" + '.groovy')
            extensions { }
          }
        }
        lightweight(true)
      }
    }
    parameters {
      stringParam('ZABBIX_SERVER', 'zabbix.devshift.net', 'An address of Zabbix server')
      stringParam('ZABBIX_PORT', '10051', 'A port of Zabbix server used by zabbix_sender utility')
      stringParam('CYCLES_COUNT', '1', 'Number of runs per user')
      stringParam('PIPELINE_TIMEOUT', '13', 'Job timeout in minutes')
      stringParam('START_SOFT_FAILURE_TIMEOUT', '60', 'Time in seconds at which workspace should start')
      stringParam('START_HARD_FAILURE_TIMEOUT', '300', 'Hard timeout in seconds for workspace startup (workspace failed to start)')
      stringParam('STOP_SOFT_FAILURE_TIMEOUT', '5', 'Time in seconds at which workspace should be stopped')
      stringParam('STOP_HARD_FAILURE_TIMEOUT', '120', 'Hard timeout in seconds for workspace stop (workspace failed to stop)')
      credentialsParam('USERS_PROPERTIES_FILE_ID'){
        type('org.jenkinsci.plugins.plaincredentials.impl.FileCredentialsImpl')
        required()
        defaultValue("${workspaceStartupJobName}" + '.users.properties')
        description('StartWorkspaceTest user credentials')
      }
    }
    properties {
      buildDiscarder {
        strategy {
          logRotator {
            daysToKeepStr('7')
            numToKeepStr('')
            artifactDaysToKeepStr('')
            artifactNumToKeepStr('')
          }
        }
      }
      pipelineTriggers {
        triggers {
          cron {
            spec('H * * * *')
          }
        }
      }
      durabilityHint {
        hint('MAX_SURVIVABILITY')
      }
    }
  }
}

pipelineJob("${workspaceStartupReporterJobName}") {
  concurrentBuild(false)
  definition {
    cpsScm {
      scm {
        git {
            remote { url('https://www.github.com/redhat-developer/rh-che.git') }
            branches('*/master')
          scriptPath('.ci/workspace-startup/' + "${workspaceStartupReporterJobName}" + '.groovy')
          extensions { }
        }
      }
      lightweight(true)
    }
  }
  parameters {
    stringParam('ZABBIX_URL', 'https://zabbix.devshift.net:9443/zabbix', 'URL for zabbix server endpoint')
    stringParam('SLACK_CHANNEL', '#forum-hosted-che', 'Slack channel to send the reports to')
    credentialsParam('SLACK_URL_ID') {
      type('org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl')
      required()
      defaultValue('workspace-startup-reporter-slack-api-webhook-url')
      description('Slack API endpoint URL for bot account')
    }
    credentialsParam('ZABBIX_CREDENTIALS_ID') {
      type('com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl')
      required()
      defaultValue('workspace-startup-reporter-zabbix-auth')
      description('Credentials for Zabbix endpoint authentication to pull data via API')
    }
  }
  properties {
    buildDiscarder {
      strategy {
        logRotator {
          daysToKeepStr('7')
          numToKeepStr('')
          artifactDaysToKeepStr('')
          artifactNumToKeepStr('')
        }
      }
    }
    pipelineTriggers {
      triggers {
        cron {
          spec('0 7 * * *')
        }
      }
    }
  }
}

mountVolumeNames.each { mountVolumeJobName ->
  pipelineJob("${mountVolumeJobName}") {
    concurrentBuild(false)
    definition {
      cpsScm {
        scm {
          git {
            remote { url('https://www.github.com/redhat-developer/rh-che.git') }
            branches('*/master')
            scriptPath('.ci/mount-volume/mount_volume_job.groovy')
            extensions { }
          }
        }
        lightweight(true)
      }
    }
    parameters {
      stringParam('ZABBIX_SERVER', 'zabbix.devshift.net', 'An address of Zabbix server')
      stringParam('ZABBIX_PORT', '10051', 'A port of Zabbix server used by zabbix_sender utility')
      credentialsParam('MOUNT_VOLUME_ACCOUNT_CREDENTIALS_ID'){
        type('com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl')
        required()
        defaultValue("${mountVolumeJobName}" + '-credentials')
        description('Mount volume job account credentials')
      }
    }
    properties {
      buildDiscarder {
        strategy {
          logRotator {
            daysToKeepStr('28')
            numToKeepStr('')
            artifactDaysToKeepStr('')
            artifactNumToKeepStr('')
          }
        }
      }
      pipelineTriggers {
        triggers {
          cron {
            spec('H */2 * * *')
          }
        }
      }
    }
  }
}

listView('OSIO_CI') {
  description('Rh-Che tests that can\'t be running on ci.centos.org because they are using JobDSL.')
  filterBuildQueue()
  filterExecutors()
  jobs {
    for (jobName in workspaceStartupNames
    .plus(seedJobName)
    .plus(workspaceStartupReporterJobName)
    .plus(mountVolumeNames)) {
      name(jobName)
    }
  }
  columns {
    status()
    weather()
    name()
    lastSuccess()
    lastFailure()
    lastDuration()
    buildButton()
    lastBuildConsole()
  }
}
