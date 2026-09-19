def call(String status) {
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

    def elapsedSeconds = ((System.currentTimeMillis() - currentBuild.startTimeInMillis) / 1000) as int

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

    withCredentials([
        string(credentialsId: 'telegram-bot-token', variable: 'BOT_TOKEN'),
        string(credentialsId: 'telegram-chat-id', variable: 'CHAT_ID')
    ]) {
        sh """
            curl -sS -X POST "https://api.telegram.org/bot\${BOT_TOKEN}/sendMessage" \
                -d chat_id="\${CHAT_ID}" \
                --data-urlencode "text=${message}"
        """
    }
}
