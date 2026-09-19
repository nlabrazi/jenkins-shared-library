<a name="readme-top"></a>

<!-- PROJECT SHIELDS -->
[![Contributors][contributors-shield]][contributors-url]
[![Forks][forks-shield]][forks-url]
[![Stargazers][stars-shield]][stars-url]
[![Issues][issues-shield]][issues-url]
[![License][license-shield]][license-url]
[![GitHub][github-shield]][github-url]

<!-- TABLE OF CONTENTS -->
<details>
  <summary>Table of Contents</summary>
  <ol>
    <li>
      <a href="#about-the-project">About The Project</a>
      <ul>
        <li><a href="#description">Description</a></li>
        <li><a href="#planned-features">Planned Features</a></li>
        <li><a href="#built-with">Built With</a></li>
      </ul>
    </li>
    <li>
      <a href="#getting-started">Getting Started</a>
      <ul>
        <li><a href="#repository-structure">Repository Structure</a></li>
        <li><a href="#jenkins-configuration">Jenkins Configuration</a></li>
        <li><a href="#credentials">Credentials</a></li>
        <li><a href="#usage">Usage</a></li>
      </ul>
    </li>
    <li>
      <a href="#contributing">Contributing</a>
      <ul>
        <li><a href="#license">License</a></li>
        <li><a href="#contact">Contact</a></li>
      </ul>
    </li>
  </ol>
</details>

<!-- ABOUT THE PROJECT -->
<a name="about-the-project"></a>
# ⚙️ About The Project

<a name="description"></a>
### ℹ️ Description

Jenkins Shared Library is a reusable Jenkins Pipeline library used to centralize common CI/CD logic across multiple repositories.

The goal is to keep individual `Jenkinsfile` files small and focused on project-specific build steps while shared concerns such as notifications, reusable helpers, and deployment utilities live in one versioned repository.

The first shared capability is Telegram CI notification support through the Telegram Bot API.

- ♻️ Reusable Jenkins Pipeline helpers shared across multiple repositories.
- 📦 Versioned independently from application repositories.
- 🔐 Jenkins credentials remain stored in Jenkins and are never committed to Git.
- 📬 Telegram notifications can be triggered from any Pipeline with a single helper call.
- 🧩 Designed to progressively host common CI/CD logic without turning project `Jenkinsfile` files into large scripts.
- 🛠️ Compatible with Jenkins Declarative Pipelines and Multibranch Pipelines.

---

<a name="planned-features"></a>
## 🚀 Planned Features

- 📬 Enrich Telegram notifications with branch, commit, author, build URL and duration.
- 🔄 Add shared helpers for deployment workflows.
- 🐳 Add reusable Docker build and cleanup helpers.
- ✅ Add common CI helpers for Node.js projects.
- 🧪 Add reusable test/reporting helpers where useful.
- 📊 Integrate deployment status with Sentinel.
- 🔔 Add notification rules such as `failure`, `fixed`, and deployment-only events.

---

<a name="built-with"></a>
### 🏗️ Built With

* [![Jenkins][Jenkins.io]][Jenkins-url]
* [![Groovy][Groovy.io]][Groovy-url]
* [![Docker][Docker.io]][Docker-url]
* [![Telegram][Telegram.io]][Telegram-url]
* [![GitHub][GitHub.io]][GitHubRepo-url]

<p align="right">(<a href="#readme-top">back to top</a>)</p>

<!-- GETTING STARTED -->
<a name="getting-started"></a>
# ✅ Getting Started

This repository is not a standalone application. Jenkins loads it as a **Global Shared Library** and exposes the scripts stored in `vars/` to Pipeline projects.

<a name="repository-structure"></a>
### 📁 Repository Structure

```text
jenkins-shared-library/
├── vars/
│   └── notifyTelegram.groovy
└── README.md
```

Jenkins automatically exposes files inside `vars/` as global Pipeline steps.

For example:

```text
vars/notifyTelegram.groovy
```

becomes available in a Pipeline as:

```groovy
notifyTelegram('✅ SUCCESS')
```

---

<a name="jenkins-configuration"></a>
### ⚙️ Jenkins Configuration

In Jenkins:

```text
Manage Jenkins
→ System
→ Global Trusted Pipeline Libraries
```

Add a library with settings similar to:

```text
Name: nabster-ci
Default version: main
Retrieval method: Modern SCM
SCM: Git
Repository URL: https://github.com/nlabrazi/jenkins-shared-library.git
```

If the repository is private, select the appropriate GitHub credential.

A Pipeline can then load the library with:

```groovy
@Library('nabster-ci') _
```

---

<a name="credentials"></a>
### 🔐 Credentials

Secrets are stored in Jenkins Credentials and must never be committed to this repository.

The Telegram helper currently expects:

```text
telegram-bot-token
telegram-chat-id
```

Both should be created as **Secret text** credentials in Jenkins.

Example Jenkins locations:

```text
Manage Jenkins
→ Credentials
→ System
→ Global credentials
```

The shared library accesses them through Jenkins' `withCredentials` mechanism only while the notification step is running.

---

<a name="usage"></a>
### ▶️ Usage

Example Declarative Pipeline:

```groovy
@Library('nabster-ci') _

pipeline {
    agent {
        docker {
            image 'node:24'
        }
    }

    stages {
        stage('Install') {
            steps {
                sh 'npm ci'
            }
        }

        stage('Check') {
            steps {
                sh 'npm run check'
            }
        }

        stage('Unit tests') {
            steps {
                sh 'npm run test:unit'
            }
        }

        stage('Build') {
            steps {
                sh 'npm run build'
            }
        }
    }

    post {
        success {
            notifyTelegram('✅ CI SUCCESS')
        }

        failure {
            notifyTelegram('❌ CI FAILED')
        }
    }
}
```

Current Telegram helper:

```groovy
def call(String status) {
    withCredentials([
        string(credentialsId: 'telegram-bot-token', variable: 'BOT_TOKEN'),
        string(credentialsId: 'telegram-chat-id', variable: 'CHAT_ID')
    ]) {
        sh """
            curl -s -X POST "https://api.telegram.org/bot${BOT_TOKEN}/sendMessage" \
              -d chat_id="${CHAT_ID}" \
              --data-urlencode "text=${status} - ${env.JOB_NAME} #${env.BUILD_NUMBER}"
        """
    }
}
```

This keeps application repositories focused on their own CI steps while notification logic remains centralized here.

<p align="right">(<a href="#readme-top">back to top</a>)</p>

<!-- CONTRIBUTING -->
<a name="contributing"></a>
# 🙌 Contributing

Changes to this repository can affect every Jenkins Pipeline using the shared library, so modifications should remain small, reusable, and backward-compatible whenever possible.

To contribute:

1. 🍴 Fork or clone the repository.
2. 🔧 Create a dedicated branch (`git checkout -b feat/my-feature`).
3. 💬 Commit your changes (`git commit -m "feat: add my feature"`).
4. 🚀 Push the branch.
5. 📨 Open a Pull Request.
6. ✅ Validate the change against a test Pipeline before merging to `main`.

Avoid placing project-specific deployment logic here unless it can be safely parameterized and reused across multiple repositories.

<p align="right">(<a href="#readme-top">back to top</a>)</p>

<!-- LICENSE -->
<a name="license"></a>
### 📄 License

This repository currently does not include an explicit license file.

In practice, this means the code is not formally distributed under a declared open source license. If you want to clearly allow usage, modification, and redistribution, add a `LICENSE` file and then update this section.

<p align="right">(<a href="#readme-top">back to top</a>)</p>

<!-- CONTACT -->
<a name="contact"></a>
### 📬 Contact

- 👤 [GitHub Profile][github-url]
- 📨 [Open an issue][issues-url]
- 📁 [Project Repository][project-url]

<p align="right">(<a href="#readme-top">back to top</a>)</p>

<!-- MARKDOWN LINKS & IMAGES -->
[contributors-shield]: https://img.shields.io/github/contributors/nlabrazi/jenkins-shared-library.svg?style=for-the-badge
[contributors-url]: https://github.com/nlabrazi/jenkins-shared-library/graphs/contributors
[forks-shield]: https://img.shields.io/github/forks/nlabrazi/jenkins-shared-library.svg?style=for-the-badge
[forks-url]: https://github.com/nlabrazi/jenkins-shared-library/network/members
[stars-shield]: https://img.shields.io/github/stars/nlabrazi/jenkins-shared-library.svg?style=for-the-badge
[stars-url]: https://github.com/nlabrazi/jenkins-shared-library/stargazers
[issues-shield]: https://img.shields.io/github/issues/nlabrazi/jenkins-shared-library.svg?style=for-the-badge
[issues-url]: https://github.com/nlabrazi/jenkins-shared-library/issues
[license-shield]: https://img.shields.io/badge/license-not%20specified-lightgrey.svg?style=for-the-badge
[license-url]: #license
[github-shield]: https://img.shields.io/badge/GitHub-nlabrazi-181717.svg?style=for-the-badge&logo=github&logoColor=white
[github-url]: https://github.com/nlabrazi
[project-url]: https://github.com/nlabrazi/jenkins-shared-library
[Jenkins.io]: https://img.shields.io/badge/Jenkins-D24939?style=for-the-badge&logo=jenkins&logoColor=white
[Jenkins-url]: https://www.jenkins.io/
[Groovy.io]: https://img.shields.io/badge/Apache%20Groovy-4298B8?style=for-the-badge&logo=apachegroovy&logoColor=white
[Groovy-url]: https://groovy-lang.org/
[Docker.io]: https://img.shields.io/badge/Docker-2496ED?style=for-the-badge&logo=docker&logoColor=white
[Docker-url]: https://www.docker.com/
[Telegram.io]: https://img.shields.io/badge/Telegram-26A5E4?style=for-the-badge&logo=telegram&logoColor=white
[Telegram-url]: https://core.telegram.org/bots/api
[GitHub.io]: https://img.shields.io/badge/GitHub-181717?style=for-the-badge&logo=github&logoColor=white
[GitHubRepo-url]: https://github.com/
