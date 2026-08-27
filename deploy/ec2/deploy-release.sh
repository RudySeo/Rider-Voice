#!/usr/bin/env bash
set -Eeuo pipefail

if [[ "$#" -ne 3 ]]; then
    echo "Usage: sudo $0 <docker-hub-image> <sha-12-character-tag> <40-character-release-sha>" >&2
    exit 1
fi

if [[ "${EUID}" -ne 0 ]]; then
    echo "deploy-release.sh must run as root." >&2
    exit 1
fi

readonly IMAGE_NAME="$1"
readonly IMAGE_TAG="$2"
readonly RELEASE_SHA="$3"
readonly INSTALL_DIR="/opt/rider-voice"
readonly MONITORING_INSTALL_DIR="${INSTALL_DIR}/monitoring"
readonly MONITORING_ENV_FILE="${MONITORING_INSTALL_DIR}/.env"
readonly GRAFANA_SECRET_FILE="${MONITORING_INSTALL_DIR}/secrets/grafana_admin_password"
readonly DEPLOY_AWS_REGION="ap-northeast-2"
readonly PARAMETER_PATH="/rider-voice/prod"
readonly KAKAO_REDIRECT_PATTERN='^https://([a-z0-9]([a-z0-9.-]*[a-z0-9])?)/api/v1/auth/oauth2/callback/kakao$'
readonly RELEASE_LOCK="/run/lock/rider-voice-release.lock"
readonly RELEASE_ARCHIVE_URL="https://github.com/RudySeo/Rider-Voice/archive/${RELEASE_SHA}.tar.gz"
readonly -a MONITORING_ASSETS=(
    "compose.prod.yml"
    "prometheus/prometheus-prod.yml"
    "grafana/provisioning/datasources/prometheus.yml"
    "grafana/provisioning/dashboards/rider-voice.yml"
    "grafana/dashboards/rider-voice-overview.json"
)

if [[ ! "${IMAGE_NAME}" =~ ^[a-z0-9]+([._-][a-z0-9]+)*/[a-z0-9]+([._-][a-z0-9]+)*$ ]]; then
    echo "Invalid Docker Hub image name." >&2
    exit 1
fi
if [[ ! "${IMAGE_TAG}" =~ ^sha-[0-9a-f]{12}$ ]]; then
    echo "Only immutable sha-<12 hexadecimal characters> tags are deployable." >&2
    exit 1
fi
if [[ ! "${RELEASE_SHA}" =~ ^[0-9a-f]{40}$ ]]; then
    echo "Only a 40-character lowercase Git commit SHA is accepted." >&2
    exit 1
fi

for required_command in aws curl df docker flock install jq mktemp tar; do
    if ! command -v "${required_command}" >/dev/null 2>&1; then
        echo "Missing required command: ${required_command}" >&2
        exit 1
    fi
done

exec 9>"${RELEASE_LOCK}"
if ! flock --nonblock 9; then
    echo "Another Rider Voice release is already running." >&2
    exit 1
fi

release_work_dir="$(mktemp -d /tmp/rider-voice-release.XXXXXX)"
trap 'rm -rf -- "${release_work_dir}"' EXIT
readonly release_work_dir
readonly source_dir="${release_work_dir}/source"
readonly previous_monitoring_dir="${release_work_dir}/previous-monitoring"
readonly monitoring_source_dir="${source_dir}/monitoring"
readonly backend_deploy_source="${source_dir}/deploy/ec2/deploy.sh"

install -m 0755 -d "${source_dir}" "${previous_monitoring_dir}"

initialize_monitoring_runtime() {
    local redirect_uri
    local domain
    local grafana_password

    install -m 0755 -d "${MONITORING_INSTALL_DIR}"
    install -m 0700 -d "$(dirname "${GRAFANA_SECRET_FILE}")"

    if [[ ! -s "${MONITORING_ENV_FILE}" ]]; then
        if ! redirect_uri="$(aws ssm get-parameter \
            --region "${DEPLOY_AWS_REGION}" \
            --name "${PARAMETER_PATH}/KAKAO_REDIRECT_URI" \
            --query Parameter.Value \
            --output text)"; then
            echo "Unable to read KAKAO_REDIRECT_URI from SSM." >&2
            exit 1
        fi
        if [[ ! "${redirect_uri}" =~ ${KAKAO_REDIRECT_PATTERN} ]]; then
            echo "KAKAO_REDIRECT_URI cannot initialize the Grafana root URL." >&2
            exit 1
        fi
        domain="${BASH_REMATCH[1]}"
        printf 'GRAFANA_ROOT_URL=https://%s/grafana/\n' "${domain}" \
            > "${release_work_dir}/monitoring.env"
        install -m 0600 "${release_work_dir}/monitoring.env" "${MONITORING_ENV_FILE}"
    fi

    if [[ ! -s "${GRAFANA_SECRET_FILE}" ]]; then
        if ! grafana_password="$(aws ssm get-parameter \
            --region "${DEPLOY_AWS_REGION}" \
            --name "${PARAMETER_PATH}/GRAFANA_ADMIN_PASSWORD" \
            --with-decryption \
            --query Parameter.Value \
            --output text)"; then
            echo "Unable to read GRAFANA_ADMIN_PASSWORD from SSM." >&2
            exit 1
        fi
        if [[ -z "${grafana_password}" || "${grafana_password}" == *$'\n'* || "${grafana_password}" == *$'\r'* ]]; then
            echo "GRAFANA_ADMIN_PASSWORD must be non-empty and single-line." >&2
            exit 1
        fi
        printf '%s' "${grafana_password}" > "${release_work_dir}/grafana_admin_password"
        install -m 0600 "${release_work_dir}/grafana_admin_password" "${GRAFANA_SECRET_FILE}"
        unset grafana_password
    fi
}

initialize_monitoring_runtime

curl --fail --silent --show-error --location \
    "${RELEASE_ARCHIVE_URL}" \
    --output "${release_work_dir}/source.tar.gz"
tar --extract --gzip \
    --file "${release_work_dir}/source.tar.gz" \
    --directory "${source_dir}" \
    --strip-components=1

if [[ ! -f "${backend_deploy_source}" ]]; then
    echo "Release archive does not contain deploy/ec2/deploy.sh." >&2
    exit 1
fi
had_previous_monitoring=true
for relative_path in "${MONITORING_ASSETS[@]}"; do
    if [[ ! -f "${monitoring_source_dir}/${relative_path}" ]]; then
        echo "Release archive is missing monitoring/${relative_path}." >&2
        exit 1
    fi
    if [[ -f "${MONITORING_INSTALL_DIR}/${relative_path}" ]]; then
        install -m 0755 -d "${previous_monitoring_dir}/$(dirname "${relative_path}")"
        cp -p "${MONITORING_INSTALL_DIR}/${relative_path}" "${previous_monitoring_dir}/${relative_path}"
    else
        had_previous_monitoring=false
    fi
done

compose_source() {
    docker compose \
        --env-file "${MONITORING_ENV_FILE}" \
        -f "${monitoring_source_dir}/compose.prod.yml" \
        "$@"
}

compose_installed() {
    docker compose \
        --env-file "${MONITORING_ENV_FILE}" \
        -f "${MONITORING_INSTALL_DIR}/compose.prod.yml" \
        "$@"
}

reclaim_docker_image_space() {
    echo "Docker disk usage before pruning unreferenced images:"
    df -h /var/lib/containerd
    docker image prune --all --force
    echo "Docker disk usage after pruning unreferenced images:"
    df -h /var/lib/containerd
}

compose_source config --quiet
reclaim_docker_image_space
compose_source pull

install -m 0755 "${backend_deploy_source}" "${INSTALL_DIR}/deploy.sh.next"
mv -f "${INSTALL_DIR}/deploy.sh.next" "${INSTALL_DIR}/deploy.sh"
"${INSTALL_DIR}/deploy.sh" "${IMAGE_NAME}" "${IMAGE_TAG}"

install_monitoring() {
    local relative_path
    for relative_path in "${MONITORING_ASSETS[@]}"; do
        install -m 0755 -d "${MONITORING_INSTALL_DIR}/$(dirname "${relative_path}")"
        install -m 0644 \
            "${monitoring_source_dir}/${relative_path}" \
            "${MONITORING_INSTALL_DIR}/${relative_path}"
    done
}

restore_monitoring() {
    local relative_path
    if [[ "${had_previous_monitoring}" != true ]]; then
        echo "No complete previous monitoring configuration is available to restore." >&2
        return 1
    fi
    echo "Restoring the previous monitoring configuration." >&2
    for relative_path in "${MONITORING_ASSETS[@]}"; do
        install -m 0644 \
            "${previous_monitoring_dir}/${relative_path}" \
            "${MONITORING_INSTALL_DIR}/${relative_path}"
    done
    compose_installed up --detach --wait --pull always
}

verify_monitoring() {
    local attempt
    local prometheus_response
    for attempt in $(seq 1 45); do
        if curl --fail --silent http://127.0.0.1:9090/-/ready >/dev/null && \
            curl --fail --silent http://127.0.0.1:3000/grafana/api/health >/dev/null; then
            prometheus_response="$(curl --fail --silent --get \
                --data-urlencode 'query=up{job="rider-voice-api"}' \
                http://127.0.0.1:9090/api/v1/query || true)"
            if jq --exit-status '.data.result[0].value[1] == "1"' \
                <<< "${prometheus_response}" >/dev/null 2>&1; then
                return 0
            fi
        fi
        sleep 2
    done
    return 1
}

install_monitoring
if compose_installed config --quiet && \
    compose_installed up --detach --wait && \
    verify_monitoring; then
    echo "Release succeeded: ${IMAGE_NAME}:${IMAGE_TAG} from ${RELEASE_SHA}"
    compose_installed images
    exit 0
fi

echo "Monitoring update failed health checks." >&2
if ! restore_monitoring; then
    echo "The previous monitoring configuration also failed to start." >&2
fi
exit 1
