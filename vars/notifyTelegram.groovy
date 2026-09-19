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
