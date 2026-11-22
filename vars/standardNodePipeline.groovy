def call(Map config = [:]) {
  def nodeImage = config.image ?: 'node:26'
  def nodeArgs = config.dockerArgs ?: ''
  def workingDir = config.workingDir ?: '.'
  def useGithubPackages = config.containsKey('useGithubPackages') ? config.useGithubPackages : true
  def runCheck = config.containsKey('check') ? config.check : true
  def runTypecheck = config.containsKey('typecheck') ? config.typecheck : false
  def runUnitTests = config.containsKey('unitTests') ? config.unitTests : true
  def runBuild = config.containsKey('build') ? config.build : true
  def runE2e = config.containsKey('e2e') ? config.e2e : false

  def dockerImage = (config.dockerImage ?: '').toString().trim()
  def dockerTarget = (config.dockerTarget ?: '').toString().trim()
  def dockerDockerfile = (config.dockerDockerfile ?: 'Dockerfile').toString().trim()
  def dockerContext = (config.dockerContext ?: '.').toString().trim()
  def dockerPushBranches = config.dockerPushBranches ?: ['master']
  def dockerUseGithubPackages = config.containsKey('dockerUseGithubPackages')
    ? config.dockerUseGithubPackages
    : useGithubPackages
  def dockerTagLatest = config.containsKey('dockerTagLatest') ? config.dockerTagLatest : true
  def dockerOwner = config.dockerOwner ?: 'nlabrazi'
  def dockerRegistry = config.dockerRegistry ?: 'ghcr.io'
  def dockerRepoName = config.dockerRepoName ?: ''

  pipeline {
    agent any

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

      stage('Node CI') {
        agent {
          docker {
            image nodeImage
            args nodeArgs
            reuseNode true
          }
        }

        stages {
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
      }

      stage('Docker build & push') {
        when {
          expression { return dockerImage != '' }
        }
        steps {
          script {
            def dockerOpts = [
              imageName: dockerImage,
              owner: dockerOwner,
              registry: dockerRegistry,
              target: dockerTarget,
              dockerfile: dockerDockerfile,
              context: dockerContext,
              useGithubPackages: dockerUseGithubPackages,
              pushBranches: dockerPushBranches,
              tagLatest: dockerTagLatest,
            ]
            if (dockerRepoName) {
              dockerOpts.repoName = dockerRepoName
            }
            dockerBuildAndPush(dockerOpts)
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
