#!/usr/bin/env bash
set -Eeuo pipefail

readonly CONTAINER_NAME="rider-voice-api"
readonly DEPLOY_AWS_REGION="ap-northeast-2"
readonly PARAMETER_PATH="/rider-voice/prod"
readonly ENV_DIR="/run/rider-voice"
readonly ENV_FILE="${ENV_DIR}/api.env"
readonly PARAMETER_FILE="${ENV_DIR}/parameters.json"
readonly STATE_DIR="/var/lib/rider-voice"
readonly CURRENT_IMAGE_FILE="${STATE_DIR}/current-image"
readonly TRUSTSTORE_HOST_DIR="/opt/rider-voice/certs"
readonly TRUSTSTORE_HOST_PATH="/opt/rider-voice/certs/rds-truststore.p12"
readonly TRUSTSTORE_CONTAINER_PATH="/app/certs/rds-truststore.p12"
readonly TRUSTSTORE_PASSWORD="changeit"
readonly HEALTH_URL="http://127.0.0.1:8080/actuator/health"

usage() {
    echo "Usage: sudo $0 <docker-hub-image> <sha-12-character-tag>" >&2
}

if [[ "${EUID}" -ne 0 ]]; then
    echo "deploy.sh must run as root." >&2
    exit 1
fi
if [[ "$#" -ne 2 ]]; then
    usage
    exit 1
fi

readonly IMAGE_NAME="$1"
readonly IMAGE_TAG="$2"
readonly IMAGE_REF="${IMAGE_NAME}:${IMAGE_TAG}"

if [[ ! "${IMAGE_NAME}" =~ ^[a-z0-9]+([._-][a-z0-9]+)*/[a-z0-9]+([._-][a-z0-9]+)*$ ]]; then
    echo "Invalid Docker Hub image name: ${IMAGE_NAME}" >&2
    exit 1
fi
if [[ ! "${IMAGE_TAG}" =~ ^sha-[0-9a-f]{12}$ ]]; then
    echo "Only immutable sha-<12 hexadecimal characters> tags are deployable." >&2
    exit 1
fi
if [[ ! -r "${TRUSTSTORE_HOST_PATH}" ]]; then
    echo "Missing RDS truststore: ${TRUSTSTORE_HOST_PATH}" >&2
    exit 1
fi

install -m 0700 -d "${ENV_DIR}"
install -m 0755 -d "${STATE_DIR}"
umask 077
exec 9>/run/lock/rider-voice-deploy.lock
if ! flock --nonblock 9; then
    echo "Another Rider Voice deployment is already running." >&2
    exit 1
fi
trap 'rm -f -- "${ENV_FILE}" "${PARAMETER_FILE}"' EXIT

aws ssm get-parameters-by-path \
    --region "${DEPLOY_AWS_REGION}" \
    --path "${PARAMETER_PATH}" \
    --recursive \
    --with-decryption \
    --output json \
    > "${PARAMETER_FILE}"

readonly REQUIRED_PARAMETERS=(
    DB_URL
    DB_USERNAME
    DB_PASSWORD
    DB_MIGRATION_USERNAME
    DB_MIGRATION_PASSWORD
    KAKAO_CLIENT_ID
    KAKAO_LOCAL_REST_API_KEY
    KAKAO_REDIRECT_URI
    FRONTEND_BASE_URL
    AUTH_COOKIE_SECURE
)
readonly OPTIONAL_PARAMETERS=(KAKAO_CLIENT_SECRET)

if ! jq -e 'all(.Parameters[].Value; (contains("\n") or contains("\r")) | not)' \
    "${PARAMETER_FILE}" >/dev/null; then
    echo "SSM parameter values must not contain line breaks." >&2
    exit 1
fi

read_parameter() {
    local parameter_name="$1"
    jq -er \
        --arg full_name "${PARAMETER_PATH}/${parameter_name}" \
        '.Parameters[] | select(.Name == $full_name) | .Value' \
        "${PARAMETER_FILE}"
}

: > "${ENV_FILE}"
for parameter_name in "${REQUIRED_PARAMETERS[@]}"; do
    if ! parameter_value="$(read_parameter "${parameter_name}")"; then
        echo "Missing required SSM parameter: ${PARAMETER_PATH}/${parameter_name}" >&2
        exit 1
    fi
    printf '%s=%s\n' "${parameter_name}" "${parameter_value}" >> "${ENV_FILE}"
done
for parameter_name in "${OPTIONAL_PARAMETERS[@]}"; do
    if parameter_value="$(read_parameter "${parameter_name}" 2>/dev/null)"; then
        printf '%s=%s\n' "${parameter_name}" "${parameter_value}" >> "${ENV_FILE}"
    fi
done
chmod 600 "${ENV_FILE}"

readonly DB_URL="$(read_parameter DB_URL)"
if ! grep -Eiq '(^|[?&])sslMode=VERIFY_IDENTITY(&|$)' <<< "${DB_URL}"; then
    echo "DB_URL must contain sslMode=VERIFY_IDENTITY." >&2
    exit 1
fi
if [[ "$(read_parameter AUTH_COOKIE_SECURE)" != "true" ]]; then
    echo "AUTH_COOKIE_SECURE must be true in production." >&2
    exit 1
fi

start_container() {
    local image_ref="$1"
    docker run --detach \
        --name "${CONTAINER_NAME}" \
        --restart unless-stopped \
        --init \
        --publish 127.0.0.1:8080:8080 \
        --env-file "${ENV_FILE}" \
        --env SPRING_PROFILES_ACTIVE=prod \
        --env "JAVA_TOOL_OPTIONS=-Djavax.net.ssl.trustStore=${TRUSTSTORE_CONTAINER_PATH} -Djavax.net.ssl.trustStorePassword=${TRUSTSTORE_PASSWORD} -Djavax.net.ssl.trustStoreType=PKCS12" \
        --volume "${TRUSTSTORE_HOST_DIR}:/app/certs:ro" \
        --log-driver json-file \
        --log-opt max-size=10m \
        --log-opt max-file=5 \
        "${image_ref}" >/dev/null
}

stop_container() {
    if docker container inspect "${CONTAINER_NAME}" >/dev/null 2>&1; then
        docker stop --time 30 "${CONTAINER_NAME}" >/dev/null 2>&1 || true
        docker rm --force "${CONTAINER_NAME}" >/dev/null 2>&1 || true
    fi
}

wait_for_health() {
    local attempt
    for attempt in $(seq 1 90); do
        if curl --fail --silent "${HEALTH_URL}" | grep --quiet '"status":"UP"'; then
            return 0
        fi
        if [[ "$(docker inspect --format='{{.State.Running}}' "${CONTAINER_NAME}" 2>/dev/null || true)" != "true" ]]; then
            return 1
        fi
        sleep 2
    done
    return 1
}

previous_image="$(docker inspect --format='{{.Config.Image}}' "${CONTAINER_NAME}" 2>/dev/null || true)"
if [[ -z "${previous_image}" && -s "${CURRENT_IMAGE_FILE}" ]]; then
    previous_image="$(<"${CURRENT_IMAGE_FILE}")"
fi

echo "Pulling ${IMAGE_REF}."
docker pull "${IMAGE_REF}" >/dev/null
stop_container
deployment_healthy=false
if start_container "${IMAGE_REF}" && wait_for_health; then
    deployment_healthy=true
fi

if [[ "${deployment_healthy}" == "true" ]]; then
    printf '%s\n' "${IMAGE_REF}" > "${CURRENT_IMAGE_FILE}.tmp"
    chmod 0644 "${CURRENT_IMAGE_FILE}.tmp"
    mv "${CURRENT_IMAGE_FILE}.tmp" "${CURRENT_IMAGE_FILE}"
    echo "Deployment succeeded: ${IMAGE_REF}"
    exit 0
fi

echo "New container failed its health check: ${IMAGE_REF}" >&2
docker logs --tail 200 "${CONTAINER_NAME}" >&2 || true
stop_container

rollback_succeeded=false
if [[ -n "${previous_image}" && "${previous_image}" != "${IMAGE_REF}" ]]; then
    echo "Starting automatic rollback to ${previous_image}." >&2
    if start_container "${previous_image}" && wait_for_health; then
        rollback_succeeded=true
        printf '%s\n' "${previous_image}" > "${CURRENT_IMAGE_FILE}.tmp"
        chmod 0644 "${CURRENT_IMAGE_FILE}.tmp"
        mv "${CURRENT_IMAGE_FILE}.tmp" "${CURRENT_IMAGE_FILE}"
        echo "Rollback succeeded: ${previous_image}" >&2
    else
        echo "Rollback container also failed its health check." >&2
        docker logs --tail 200 "${CONTAINER_NAME}" >&2 || true
    fi
else
    echo "No different previous image is available for rollback." >&2
fi

if [[ "${rollback_succeeded}" == "true" ]]; then
    exit 1
fi
exit 2
