def call(Map config = [:]) {
  def nodeImage = config.image ?: 'node:26'
  def workingDir = config.workingDir ?: '.'
  def useGithubPackages = config.containsKey('useGithubPackages') ? config.useGithubPackages : true
  def runCheck = config.containsKey('check') ? config.check : true
  def runTypecheck = config.containsKey('typecheck') ? config.typecheck : false
  def runUnitTests = config.containsKey('unitTests') ? config.unitTests : true
  def runBuild = config.containsKey('build') ? config.build : true
  def runE2e = config.containsKey('e2e') ? config.e2e : false

  pipeline {
    agent {
      docker {
        image nodeImage
      }
    }

    options {
      timestamps()
      disableConcurrentBuilds()
    }

    stages {
      stage('Notify start') {
        steps {
          notifyTelegram('started')
        }
      }

      stage('Notify Renovate PR') {
        when {
          changeRequest target: 'staging'
        }
        steps {
          notifyRenovatePr()
        }
      }

      stage('Install') {
        steps {
          npmCi(
            workingDir: workingDir,
            useGithubPackages: useGithubPackages
          )
        }
      }

      stage('Check') {
        when {
          expression { return runCheck }
        }
        steps {
          dir(workingDir) {
            sh 'npm run check'
          }
        }
      }

      stage('Typecheck') {
        when {
          expression { return runTypecheck }
        }
        steps {
          dir(workingDir) {
            sh 'npm run typecheck'
          }
        }
      }

      stage('Unit tests') {
        when {
          expression { return runUnitTests }
        }
        steps {
          dir(workingDir) {
            sh 'npm test'
          }
        }
      }

      stage('Build') {
        when {
          expression { return runBuild }
        }
        steps {
          dir(workingDir) {
            sh 'npm run build'
          }
        }
      }

      stage('End-to-end tests') {
        when {
          expression { return runE2e }
        }
        steps {
          dir(workingDir) {
            sh 'npm run test:e2e'
          }
        }
      }
    }

    post {
      success {
        notifyTelegram('success')
      }

      failure {
        notifyTelegram('failed')
      }
    }
  }
}
