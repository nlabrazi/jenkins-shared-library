def call(Map config = [:]) {
  def workingDir = config.workingDir ?: '.'
  def credentialsId = config.credentialsId ?: 'github-packages-publish'
  def scope = config.scope ?: '@nlabrazi'
  def registryHost = config.registryHost ?: 'npm.pkg.github.com'
  def useGithubPackages = config.containsKey('useGithubPackages') ? config.useGithubPackages : true

  dir(workingDir) {
    if (!useGithubPackages) {
      sh 'npm ci'
      return
    }

    withCredentials([
      string(
        credentialsId: credentialsId,
        variable: 'GITHUB_PACKAGES_TOKEN'
      )
    ]) {
      sh """
        set +x
        set -eu
        umask 077

        NPMRC="\$(mktemp)"
        trap 'rm -f "\$NPMRC"' EXIT

        printf '%s\\n' \\
          '${scope}:registry=https://${registryHost}' \\
          "//${registryHost}/:_authToken=\${GITHUB_PACKAGES_TOKEN}" \\
          > "\$NPMRC"

        NPM_CONFIG_USERCONFIG="\$NPMRC" npm ci
      """
    }
  }
}
