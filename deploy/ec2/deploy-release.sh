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
readonly NGINX_SITE="/etc/nginx/sites-available/rider-voice"
readonly OBSERVABILITY_NETWORK="rider-voice-observability"
readonly DEPLOY_AWS_REGION="ap-northeast-2"
readonly PARAMETER_PATH="/rider-voice/prod"
readonly KAKAO_REDIRECT_PATTERN='^https://([a-z0-9]([a-z0-9.-]*[a-z0-9])?)/api/v1/auth/oauth2/callback/kakao$'
readonly RELEASE_LOCK="/run/lock/rider-voice-release.lock"
readonly RELEASE_ARCHIVE_URL="https://github.com/RudySeo/Rider-Voice/archive/${RELEASE_SHA}.tar.gz"
readonly -a MONITORING_CONTAINERS=(
    "rider-voice-prometheus"
    "rider-voice-grafana"
)
readonly -a MONITORING_VOLUMES=(
    "rider-voice-prometheus-data"
    "rider-voice-grafana-data"
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

for required_command in aws cp curl df docker flock install mktemp mv nginx sed systemctl tar; do
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
readonly backend_deploy_source="${source_dir}/deploy/ec2/deploy.sh"
readonly nginx_source="${source_dir}/deploy/ec2/nginx.conf.template"
readonly nginx_staged="${release_work_dir}/nginx.conf"
readonly nginx_backup="${release_work_dir}/nginx.conf.previous"

install -m 0755 -d "${source_dir}"

curl --fail --silent --show-error --location \
    "${RELEASE_ARCHIVE_URL}" \
    --output "${release_work_dir}/source.tar.gz"
tar --extract --gzip \
    --file "${release_work_dir}/source.tar.gz" \
    --directory "${source_dir}" \
    --strip-components=1

for required_asset in "${backend_deploy_source}" "${nginx_source}"; do
    if [[ ! -f "${required_asset}" ]]; then
        echo "Release archive is missing ${required_asset#${source_dir}/}." >&2
        exit 1
    fi
done
if [[ ! -f "${NGINX_SITE}" ]]; then
    echo "Missing installed Nginx site: ${NGINX_SITE}" >&2
    exit 1
fi

resolve_domain() {
    local redirect_uri

    if ! redirect_uri="$(aws ssm get-parameter \
        --region "${DEPLOY_AWS_REGION}" \
        --name "${PARAMETER_PATH}/KAKAO_REDIRECT_URI" \
        --query Parameter.Value \
        --output text)"; then
        echo "Unable to read KAKAO_REDIRECT_URI from SSM." >&2
        return 1
    fi
    if [[ ! "${redirect_uri}" =~ ${KAKAO_REDIRECT_PATTERN} ]]; then
        echo "KAKAO_REDIRECT_URI cannot initialize the Nginx configuration." >&2
        return 1
    fi
    printf '%s\n' "${BASH_REMATCH[1]}"
}

restore_nginx() {
    install -m 0644 "${nginx_backup}" "${NGINX_SITE}"
    nginx -t
    systemctl reload nginx
}

install_nginx() {
    local domain

    domain="$(resolve_domain)"
    sed "s/__DOMAIN__/${domain}/g" "${nginx_source}" > "${nginx_staged}"
    cp -p "${NGINX_SITE}" "${nginx_backup}"
    install -m 0644 "${nginx_staged}" "${NGINX_SITE}.next"
    mv -f "${NGINX_SITE}.next" "${NGINX_SITE}"
    if ! nginx -t; then
        echo "New Nginx configuration is invalid; restoring the previous configuration." >&2
        restore_nginx
        return 1
    fi
    if ! systemctl reload nginx; then
        echo "Nginx reload failed; restoring the previous configuration." >&2
        restore_nginx
        return 1
    fi
}

decommission_monitoring() {
    local container_name
    local volume_name

    for container_name in "${MONITORING_CONTAINERS[@]}"; do
        if docker container inspect "${container_name}" >/dev/null 2>&1; then
            docker rm --force "${container_name}"
        fi
    done
    for volume_name in "${MONITORING_VOLUMES[@]}"; do
        if docker volume inspect "${volume_name}" >/dev/null 2>&1; then
            docker volume rm "${volume_name}"
        fi
    done
    if [[ -d "${MONITORING_INSTALL_DIR}" ]]; then
        rm -rf -- "${MONITORING_INSTALL_DIR}"
    fi
}

reclaim_docker_image_space() {
    echo "Docker disk usage before pruning unreferenced images:"
    df -h /var/lib/containerd
    docker image prune --all --force
    echo "Docker disk usage after pruning unreferenced images:"
    df -h /var/lib/containerd
}

install_nginx
decommission_monitoring
reclaim_docker_image_space

install -m 0755 "${backend_deploy_source}" "${INSTALL_DIR}/deploy.sh.next"
mv -f "${INSTALL_DIR}/deploy.sh.next" "${INSTALL_DIR}/deploy.sh"
"${INSTALL_DIR}/deploy.sh" "${IMAGE_NAME}" "${IMAGE_TAG}"

if docker network inspect "${OBSERVABILITY_NETWORK}" >/dev/null 2>&1; then
    docker network rm "${OBSERVABILITY_NETWORK}"
fi

echo "Release succeeded: ${IMAGE_NAME}:${IMAGE_TAG} from ${RELEASE_SHA}"
