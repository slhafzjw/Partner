#!/usr/bin/env bash
set -euo pipefail

MODULE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REPO_DIR="$(cd "${MODULE_DIR}/.." && pwd)"
IMAGE_NAME="${PARTNERCTL_ARM64_BUILDER_IMAGE:-partnerctl-native-arm64-builder:21}"
DOCKERFILE="${MODULE_DIR}/Dockerfile.native-arm64"

if ! docker image inspect "${IMAGE_NAME}" >/dev/null 2>&1; then
  echo "[partnerctl] building reusable arm64 native builder image: ${IMAGE_NAME}"
  docker buildx build \
    --platform linux/arm64 \
    -f "${DOCKERFILE}" \
    -t "${IMAGE_NAME}" \
    --load \
    "${MODULE_DIR}"
fi

echo "[partnerctl] building linux-aarch64 native executable"
docker run --rm \
  --platform linux/arm64 \
  --entrypoint /bin/bash \
  -v "${REPO_DIR}":/workspace \
  -v "${HOME}/.m2":/root/.m2 \
  -w /workspace/PartnerCtl \
  "${IMAGE_NAME}" \
  -lc 'mvn -Pnative native:compile && file target/partnerctl'
