def call(Map config = [:]) {
  def expectedAuthor = config.author ?: 'renovate[bot]'

  if (env.CHANGE_AUTHOR == expectedAuthor && currentBuild.number == 1) {
    notifyTelegram('renovate_pr', [
      prNumber: env.CHANGE_ID,
      prUrl: env.CHANGE_URL
    ])
  }
}
