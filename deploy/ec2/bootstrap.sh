#!/usr/bin/env bash
set -Eeuo pipefail

readonly SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
readonly INSTALL_DIR="/opt/rider-voice"
readonly CERT_DIR="${INSTALL_DIR}/certs"
readonly NGINX_SITE="/etc/nginx/sites-available/rider-voice"
readonly CERTBOT_ROOT="/var/www/certbot"
readonly RDS_CA_URL="https://truststore.pki.rds.amazonaws.com/global/global-bundle.pem"

aws_work_dir=""
trust_work_dir=""

cleanup() {
    if [[ -n "${aws_work_dir}" && -d "${aws_work_dir}" ]]; then
        rm -rf -- "${aws_work_dir}"
    fi
    if [[ -n "${trust_work_dir}" && -d "${trust_work_dir}" ]]; then
        rm -rf -- "${trust_work_dir}"
    fi
}
trap cleanup EXIT

usage() {
    echo "Usage: sudo $0 <api-domain> <letsencrypt-email>" >&2
}

if [[ "${EUID}" -ne 0 ]]; then
    echo "bootstrap.sh must run as root." >&2
    exit 1
fi

if [[ "$#" -ne 2 ]]; then
    usage
    exit 1
fi

readonly DOMAIN="$1"
readonly LETSENCRYPT_EMAIL="$2"

if [[ ! "${DOMAIN}" =~ ^[a-z0-9]([a-z0-9.-]*[a-z0-9])?$ ]]; then
    echo "Invalid domain: ${DOMAIN}" >&2
    exit 1
fi
if [[ ! "${LETSENCRYPT_EMAIL}" =~ ^[^[:space:]@]+@[^[:space:]@]+\.[^[:space:]@]+$ ]]; then
    echo "Invalid Let's Encrypt email." >&2
    exit 1
fi
if [[ "$(uname -m)" != "x86_64" ]]; then
    echo "This bootstrap targets the existing x86_64 EC2 instance." >&2
    exit 1
fi

for asset in deploy.sh nginx-http.conf.template nginx.conf.template; do
    if [[ ! -f "${SCRIPT_DIR}/${asset}" ]]; then
        echo "Missing deployment asset: ${SCRIPT_DIR}/${asset}" >&2
        exit 1
    fi
done

export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get install --yes ca-certificates curl default-mysql-client gnupg jq nginx snapd unzip

if ! command -v docker >/dev/null 2>&1; then
    install -m 0755 -d /etc/apt/keyrings
    curl --fail --silent --show-error --location \
        https://download.docker.com/linux/ubuntu/gpg \
        --output /etc/apt/keyrings/docker.asc
    chmod a+r /etc/apt/keyrings/docker.asc
    # shellcheck disable=SC1091
    . /etc/os-release
    printf 'deb [arch=amd64 signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu %s stable\n' \
        "${VERSION_CODENAME}" > /etc/apt/sources.list.d/docker.list
    apt-get update
    apt-get install --yes docker-ce docker-ce-cli containerd.io docker-buildx-plugin
fi
systemctl enable --now docker

if ! command -v aws >/dev/null 2>&1; then
    aws_work_dir="$(mktemp -d)"
    curl --fail --silent --show-error --location \
        https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip \
        --output "${aws_work_dir}/awscliv2.zip"
    unzip -q "${aws_work_dir}/awscliv2.zip" -d "${aws_work_dir}"
    "${aws_work_dir}/aws/install"
fi

if ! snap list certbot >/dev/null 2>&1; then
    if ! snap list core >/dev/null 2>&1; then
        snap install core
    else
        snap refresh core
    fi
    snap install --classic certbot
fi

if systemctl list-unit-files --type=service | grep --quiet '^amazon-ssm-agent\.service'; then
    systemctl enable --now amazon-ssm-agent
elif systemctl list-unit-files --type=service | grep --quiet '^snap\.amazon-ssm-agent\.amazon-ssm-agent\.service'; then
    systemctl enable --now snap.amazon-ssm-agent.amazon-ssm-agent.service
else
    snap install amazon-ssm-agent --classic
    systemctl enable --now snap.amazon-ssm-agent.amazon-ssm-agent.service
fi

install -m 0755 -d "${INSTALL_DIR}" "${CERT_DIR}" /var/lib/rider-voice "${CERTBOT_ROOT}"
install -m 0755 "${SCRIPT_DIR}/deploy.sh" "${INSTALL_DIR}/deploy.sh"

trust_work_dir="$(mktemp -d)"
curl --fail --silent --show-error --location "${RDS_CA_URL}" \
    --output "${trust_work_dir}/global-bundle.pem"
awk '
    /-----BEGIN CERTIFICATE-----/ { certificate += 1 }
    certificate > 0 { print > (output "/rds-ca-" certificate ".pem") }
' output="${trust_work_dir}" "${trust_work_dir}/global-bundle.pem"

if ! compgen -G "${trust_work_dir}/rds-ca-*.pem" >/dev/null; then
    echo "The RDS CA bundle did not contain a certificate." >&2
    exit 1
fi

rm -f "${CERT_DIR}/rds-truststore.p12"
docker run --rm \
    --volume "${trust_work_dir}:/certs:ro" \
    --volume "${CERT_DIR}:/out" \
    eclipse-temurin:25-jdk-noble \
    sh -ceu '
        for certificate in /certs/rds-ca-*.pem; do
            alias_name="$(basename "${certificate}" .pem)"
            keytool -importcert -noprompt \
                -storetype PKCS12 \
                -keystore /out/rds-truststore.p12 \
                -storepass changeit \
                -alias "${alias_name}" \
                -file "${certificate}"
        done
    '
chmod 0444 "${CERT_DIR}/rds-truststore.p12"

render_nginx_template() {
    local source_template="$1"
    sed "s/__DOMAIN__/${DOMAIN}/g" "${source_template}" > "${NGINX_SITE}"
}

render_nginx_template "${SCRIPT_DIR}/nginx-http.conf.template"
ln -sfn "${NGINX_SITE}" /etc/nginx/sites-enabled/rider-voice
rm -f /etc/nginx/sites-enabled/default
nginx -t
systemctl enable --now nginx
systemctl reload nginx

/snap/bin/certbot certonly \
    --webroot \
    --webroot-path "${CERTBOT_ROOT}" \
    --domain "${DOMAIN}" \
    --email "${LETSENCRYPT_EMAIL}" \
    --agree-tos \
    --no-eff-email \
    --non-interactive \
    --keep-until-expiring

render_nginx_template "${SCRIPT_DIR}/nginx.conf.template"
install -m 0755 -d /etc/letsencrypt/renewal-hooks/deploy
printf '%s\n' \
    '#!/usr/bin/env bash' \
    'set -euo pipefail' \
    'nginx -t' \
    'systemctl reload nginx' \
    > /etc/letsencrypt/renewal-hooks/deploy/reload-rider-voice-nginx
chmod 0755 /etc/letsencrypt/renewal-hooks/deploy/reload-rider-voice-nginx
nginx -t
systemctl reload nginx

echo "EC2 bootstrap complete for https://${DOMAIN}"
echo "Next: create /rider-voice/prod SSM parameters, then run ${INSTALL_DIR}/deploy.sh."
