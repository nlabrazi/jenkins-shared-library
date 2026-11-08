def call(String status, Map details = [:]) {
  def project = env.JOB_NAME?.tokenize('/')?.first() ?: env.JOB_NAME
  def branch = env.BRANCH_NAME ?: 'unknown'

  def commit = env.GIT_COMMIT
        ? env.GIT_COMMIT.take(7)
        : sh(script: 'git rev-parse --short HEAD', returnStdout: true).trim()

  def author = sh(
        script: 'git log -1 --pretty=%an',
        returnStdout: true
    ).trim()

  def commitMessage = sh(
        script: 'git log -1 --pretty=%s',
        returnStdout: true
    ).trim()

  def elapsedSeconds =
        ((System.currentTimeMillis() - currentBuild.startTimeInMillis) / 1000) as int

  def icon
  def title
  def durationLine = ''

  switch (status) {
        case 'started':
      icon = '🚀'
      title = 'CI STARTED'
      break

        case 'success':
      icon = '✅'
      title = 'CI SUCCESS'
      durationLine = "\n⏱️ ${elapsedSeconds}s"
      break

        case 'failed':
      icon = '❌'
      title = 'CI FAILED'
      durationLine = "\n⏱️ ${elapsedSeconds}s"
      break

        case 'package_published':
      icon = '📦'
      title = 'PACKAGE PUBLISHED'
      break
        case 'renovate_pr':
      icon = '🔔'
      title = 'RENOVATE PR OPENED'
      break

        default:
            icon = 'ℹ️'
      title = status.toUpperCase()
  }

  def message = """${icon} ${title}

📦 ${project}
🌿 ${branch}
👤 ${author}
🧾 ${commit} — ${commitMessage}
🔨 Build #${env.BUILD_NUMBER}${durationLine}
🔗 ${env.BUILD_URL}"""

  if (status == 'package_published') {
    def packageName = details.packageName ?: 'unknown'
    def packageVersion = details.version ?: 'unknown'
    message = """📦 PACKAGE PUBLISHED

📦 ${packageName}
🏷️ Version : ${packageVersion}
🌿 ${branch}
🔨 Build #${env.BUILD_NUMBER}
🔗 ${env.BUILD_URL}"""
  }

  if (status == 'renovate_pr') {
    def prNumber = (details.prNumber ?: '').toString()
    def prUrl = (details.prUrl ?: '').toString()

    if (!(prNumber ==~ /[0-9]+/) ||
        !(prUrl ==~ /https:\/\/github\.com\/[A-Za-z0-9_.-]+\/[A-Za-z0-9_.-]+\/pull\/[0-9]+/)) {
      echo 'Invalid Renovate PR metadata. Notification skipped.'
      return
    }

    message = """🔔 RENOVATE PR OPENED

📦 ${project}
🔀 PR #${prNumber} → staging
🔗 ${prUrl}"""
  }

  try {
    withCredentials([
            string(credentialsId: 'telegram-bot-token', variable: 'BOT_TOKEN'),
            string(credentialsId: 'telegram-chat-id', variable: 'CHAT_ID')
        ]) {
      def exitCode = sh(
                script: """
                    curl --silent \
                         --show-error \
                         --fail \
                         --connect-timeout 5 \
                         --max-time 10 \
                         -X POST "https://api.telegram.org/bot\${BOT_TOKEN}/sendMessage" \
                         -d chat_id="\${CHAT_ID}" \
                         --data-urlencode "text=${message}"
                """,
                returnStatus: true
            )

      if (exitCode != 0) {
        echo "⚠️ Telegram notification unreachable (exit code: ${exitCode}). CI continues."
      }
    }
    } catch (Exception error) {
    echo "⚠️ Telegram notification failed: ${error.message}. CI continues."
  }
}
