def call(Map config = [:]) {
  def image = config.image
  if (!image) {
    error 'dockerBuildWithPackages requires an "image" parameter (e.g. image: "myapp:ci").'
  }

  def target = config.target ?: ''
  def dockerfile = config.dockerfile ?: 'Dockerfile'
  def context = config.context ?: '.'
  def secretId = config.secretId ?: 'npmrc'
  def credentialsId = config.credentialsId ?: 'github-packages-publish'
  def scope = config.scope ?: '@nlabrazi'
  def registryHost = config.registryHost ?: 'npm.pkg.github.com'

  def targetArg = target ? "--target '${target}'" : ''

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

      DOCKER_BUILDKIT=1 docker build \\
        --secret "id=${secretId},src=\$NPMRC" \\
        -f '${dockerfile}' \\
        ${targetArg} \\
        -t '${image}' \\
        '${context}'
    """
  }
}
