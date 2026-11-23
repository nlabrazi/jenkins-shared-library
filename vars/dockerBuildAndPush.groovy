def call(Map config = [:]) {
  def imageName = (config.imageName ?: config.image ?: '').toString().trim().toLowerCase()
  if (!imageName) {
    error 'dockerBuildAndPush requires an "imageName" parameter (e.g. imageName: "sentinel").'
  }
  if (!(imageName ==~ /^[a-z0-9][a-z0-9._\/-]*$/)) {
    error "dockerBuildAndPush received an invalid imageName: '${imageName}'."
  }

  def registry = (config.registry ?: 'ghcr.io').toString().trim().toLowerCase()
  def owner = (config.owner ?: 'nlabrazi').toString().trim().toLowerCase()
  def repoName = (config.repoName ?: '').toString().trim()
  def target = (config.target ?: '').toString().trim()
  def dockerfile = (config.dockerfile ?: 'Dockerfile').toString().trim()
  def context = (config.context ?: '.').toString().trim()
  def secretId = (config.secretId ?: 'npmrc').toString().trim()
  def useGithubPackages = config.containsKey('useGithubPackages') ? (config.useGithubPackages as boolean) : true
  def npmCredentialsId = (config.credentialsId ?: 'github-packages-publish').toString().trim()
  def ghcrCredentialsId = (config.ghcrCredentialsId ?: npmCredentialsId).toString().trim()
  def ghcrUser = (config.ghcrUser ?: owner).toString().trim()
  def scope = (config.scope ?: '@nlabrazi').toString().trim()
  def npmRegistryHost = (config.npmRegistryHost ?: 'npm.pkg.github.com').toString().trim()
  def tagLatest = config.containsKey('tagLatest') ? (config.tagLatest as boolean) : true
  def cleanupLocalShaTag = config.containsKey('cleanupLocalShaTag') ? (config.cleanupLocalShaTag as boolean) : true

  def pushBranches = []
  def rawPushBranches = (config.pushBranches instanceof List) ? config.pushBranches : ['master']
  for (def b in rawPushBranches) {
    def trimmed = b.toString().trim()
    if (trimmed) {
      pushBranches.add(trimmed)
    }
  }

  def extraTags = []
  if (config.tags instanceof List) {
    for (def t in config.tags) {
      def trimmed = t.toString().trim()
      if (trimmed) {
        if (!(trimmed ==~ /^[a-zA-Z0-9._-]+$/)) {
          error "dockerBuildAndPush received an invalid tag: '${trimmed}'."
        }
        extraTags.add(trimmed)
      }
    }
  }

  if (!(registry ==~ /^[a-z0-9.-]+(:[0-9]+)?$/)) {
    error "dockerBuildAndPush received an invalid registry: '${registry}'."
  }
  if (!(owner ==~ /^[a-z0-9][a-z0-9._-]*$/)) {
    error "dockerBuildAndPush received an invalid owner: '${owner}'."
  }
  if (repoName && !(repoName ==~ /^[a-zA-Z0-9._-]+$/)) {
    error "dockerBuildAndPush received an invalid repoName: '${repoName}'."
  }
  if (target && !(target ==~ /^[a-zA-Z0-9._-]+$/)) {
    error "dockerBuildAndPush received an invalid target: '${target}'."
  }
  if (!(secretId ==~ /^[a-zA-Z0-9._-]+$/)) {
    error "dockerBuildAndPush received an invalid secretId: '${secretId}'."
  }

  def serializedBuildArgsList = []
  if (config.buildArgs instanceof Map) {
    for (def entry in config.buildArgs.entrySet()) {
      def key = entry.key.toString().trim()
      if (!(key ==~ /^[A-Za-z_][A-Za-z0-9_]*$/)) {
        error "dockerBuildAndPush received an invalid buildArg key: '${key}'."
      }
      serializedBuildArgsList.add("${key}=${entry.value.toString()}")
    }
  }

  def fullImage = imageName.contains('/') ? "${registry}/${imageName}" : "${registry}/${owner}/${imageName}"
  def defaultRepoName = imageName.tokenize('/').last()
  def branch = (env.BRANCH_NAME ?: '').toString().trim()
  def isPullRequest = (env.CHANGE_ID ?: '').toString().trim() != ''
  def shouldPush = config.containsKey('push')
    ? (config.push as boolean)
    : (!isPullRequest && pushBranches.contains(branch))

  def fullSha = (env.GIT_COMMIT ?: sh(script: 'git rev-parse HEAD', returnStdout: true).trim()).toString().trim()
  def shortSha = fullSha.take(7)
  def createdAt = sh(script: 'date -u +%Y-%m-%dT%H:%M:%SZ', returnStdout: true).trim()

  def imageTags = ["${fullImage}:sha-${shortSha}"]
  if (shouldPush && tagLatest) {
    imageTags.add("${fullImage}:latest")
  }
  for (def t in extraTags) {
    def candidate = "${fullImage}:${t}"
    if (!imageTags.contains(candidate)) {
      imageTags.add(candidate)
    }
  }

  def envVars = [
    "NABSTER_DOCKER_FULL_IMAGE=${fullImage}",
    "NABSTER_DOCKER_REGISTRY=${registry}",
    "NABSTER_DOCKER_OWNER=${owner}",
    "NABSTER_DOCKER_REPO_NAME=${repoName}",
    "NABSTER_DOCKER_DEFAULT_REPO_NAME=${defaultRepoName}",
    "NABSTER_DOCKER_TARGET=${target}",
    "NABSTER_DOCKER_FILE=${dockerfile}",
    "NABSTER_DOCKER_BUILD_CONTEXT=${context}",
    "NABSTER_DOCKER_SECRET_ID=${secretId}",
    "NABSTER_DOCKER_USE_GH_PACKAGES=${useGithubPackages ? 'true' : 'false'}",
    "NABSTER_DOCKER_NPM_SCOPE=${scope}",
    "NABSTER_DOCKER_NPM_REGISTRY_HOST=${npmRegistryHost}",
    "NABSTER_DOCKER_GHCR_USER=${ghcrUser}",
    "NABSTER_DOCKER_SHOULD_PUSH=${shouldPush ? 'true' : 'false'}",
    "NABSTER_DOCKER_GIT_SHA=${fullSha}",
    "NABSTER_DOCKER_SHA_TAG=${fullImage}:sha-${shortSha}",
    "NABSTER_DOCKER_CLEANUP_SHA_TAG=${cleanupLocalShaTag ? 'true' : 'false'}",
    "NABSTER_DOCKER_CREATED_AT=${createdAt}",
    "NABSTER_DOCKER_TAGS_LIST=${imageTags.join('\n')}",
    "NABSTER_DOCKER_BUILD_ARGS_LIST=${serializedBuildArgsList.join('\n')}",
  ]

  def creds = []
  if (useGithubPackages) {
    creds.add(string(credentialsId: npmCredentialsId, variable: 'GITHUB_PACKAGES_TOKEN'))
  }
  if (shouldPush && !(ghcrCredentialsId == npmCredentialsId && useGithubPackages)) {
    creds.add(string(credentialsId: ghcrCredentialsId, variable: 'GHCR_PUSH_TOKEN'))
  }

  def buildScript = '''#!/usr/bin/env bash
    set +x
    set -euo pipefail
    umask 077

    NPMRC=""
    DOCKER_CFG=""
    cleanup() {
      if [ -n "$NPMRC" ]; then
        rm -f "$NPMRC"
      fi
      if [ -n "$DOCKER_CFG" ]; then
        rm -rf "$DOCKER_CFG"
      fi
    }
    trap cleanup EXIT

    REPO_NAME="${NABSTER_DOCKER_REPO_NAME:-}"
    if [ -z "$REPO_NAME" ]; then
      ORIGIN_URL="$(git config --get remote.origin.url 2>/dev/null || true)"
      if [ -n "$ORIGIN_URL" ]; then
        REPO_NAME="$(basename "${ORIGIN_URL%.git}")"
      fi
    fi
    if [ -z "$REPO_NAME" ]; then
      REPO_NAME="${NABSTER_DOCKER_DEFAULT_REPO_NAME:-}"
    fi

    BUILD_CMD=(docker build -f "$NABSTER_DOCKER_FILE")

    if [ -n "${NABSTER_DOCKER_TARGET:-}" ]; then
      BUILD_CMD+=(--target "${NABSTER_DOCKER_TARGET}")
    fi

    if [ "${NABSTER_DOCKER_USE_GH_PACKAGES:-false}" = "true" ]; then
      NPMRC="$(mktemp)"
      printf '%s\n' \
        "${NABSTER_DOCKER_NPM_SCOPE}:registry=https://${NABSTER_DOCKER_NPM_REGISTRY_HOST}" \
        "//${NABSTER_DOCKER_NPM_REGISTRY_HOST}/:_authToken=${GITHUB_PACKAGES_TOKEN}" \
        > "$NPMRC"
      BUILD_CMD+=(--secret "id=${NABSTER_DOCKER_SECRET_ID},src=${NPMRC}")
    fi

    if [ -n "${NABSTER_DOCKER_BUILD_ARGS_LIST:-}" ]; then
      while IFS= read -r build_arg; do
        if [ -n "$build_arg" ]; then
          BUILD_CMD+=(--build-arg "$build_arg")
        fi
      done <<< "${NABSTER_DOCKER_BUILD_ARGS_LIST}"
    fi

    while IFS= read -r tag; do
      if [ -n "$tag" ]; then
        BUILD_CMD+=(-t "$tag")
      fi
    done <<< "${NABSTER_DOCKER_TAGS_LIST:-}"

    BUILD_CMD+=(
      --label "org.opencontainers.image.source=https://github.com/${NABSTER_DOCKER_OWNER}/${REPO_NAME}"
      --label "org.opencontainers.image.revision=${NABSTER_DOCKER_GIT_SHA}"
      --label "org.opencontainers.image.created=${NABSTER_DOCKER_CREATED_AT}"
      "$NABSTER_DOCKER_BUILD_CONTEXT"
    )

    echo "🐳 Building ${NABSTER_DOCKER_FULL_IMAGE} (push=${NABSTER_DOCKER_SHOULD_PUSH})..."
    DOCKER_BUILDKIT=1 "${BUILD_CMD[@]}"

    if [ "${NABSTER_DOCKER_SHOULD_PUSH:-false}" = "true" ]; then
      PUSH_TOKEN="${GHCR_PUSH_TOKEN:-${GITHUB_PACKAGES_TOKEN:-}}"
      if [ -z "$PUSH_TOKEN" ]; then
        echo "❌ Missing registry token for pushing to ${NABSTER_DOCKER_REGISTRY}" >&2
        exit 1
      fi

      DOCKER_CFG="$(mktemp -d)"
      export DOCKER_CONFIG="$DOCKER_CFG"
      printf '%s' "$PUSH_TOKEN" | docker login "$NABSTER_DOCKER_REGISTRY" -u "$NABSTER_DOCKER_GHCR_USER" --password-stdin

      while IFS= read -r tag; do
        if [ -n "$tag" ]; then
          echo "🚀 Pushing ${tag}..."
          docker push "$tag"
        fi
      done <<< "${NABSTER_DOCKER_TAGS_LIST:-}"

      docker logout "$NABSTER_DOCKER_REGISTRY" >/dev/null 2>&1 || true
      echo "✅ Published ${NABSTER_DOCKER_FULL_IMAGE}"
    else
      echo "ℹ️ Skipping registry push (branch not enabled for push or PR build)."
    fi

    if [ "${NABSTER_DOCKER_CLEANUP_SHA_TAG:-true}" = "true" ] && [ -n "${NABSTER_DOCKER_SHA_TAG:-}" ]; then
      echo "🧹 Removing local tag ${NABSTER_DOCKER_SHA_TAG} (BuildKit cache preserved)..."
      docker rmi "${NABSTER_DOCKER_SHA_TAG}" >/dev/null 2>&1 || true
    fi
  '''

  if (creds.isEmpty()) {
    withEnv(envVars) {
      sh buildScript
    }
  } else {
    withCredentials(creds) {
      withEnv(envVars) {
        sh buildScript
      }
    }
  }

  return [
    image: fullImage,
    shaTag: "${fullImage}:sha-${shortSha}",
    tags: imageTags,
    pushed: shouldPush,
  ]
}
