#!/usr/bin/env bash
set -Eeuo pipefail

if [[ "$#" -ne 4 ]]; then
    echo "Usage: $0 <ec2-instance-id> <docker-hub-image> <sha-12-character-tag> <40-character-release-sha>" >&2
    exit 1
fi

readonly INSTANCE_ID="$1"
readonly IMAGE_NAME="$2"
readonly IMAGE_TAG="$3"
readonly RELEASE_SHA="$4"

if [[ ! "${INSTANCE_ID}" =~ ^i-[0-9a-f]{8,17}$ ]]; then
    echo "Invalid EC2 instance ID." >&2
    exit 1
fi
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

readonly RELEASE_SCRIPT_URL="https://raw.githubusercontent.com/RudySeo/Rider-Voice/${RELEASE_SHA}/deploy/ec2/deploy-release.sh"
readonly REMOTE_SCRIPT="/tmp/rider-voice-deploy-release-${RELEASE_SHA}.sh"
readonly REMOTE_COMMAND="curl --fail --silent --show-error --location ${RELEASE_SCRIPT_URL} --output ${REMOTE_SCRIPT} && sudo bash ${REMOTE_SCRIPT} ${IMAGE_NAME} ${IMAGE_TAG} ${RELEASE_SHA}; status=\$?; rm -f ${REMOTE_SCRIPT}; exit \$status"
readonly PARAMETERS="$(jq -cn \
    --arg command "${REMOTE_COMMAND}" \
    '{commands: [$command], executionTimeout: ["900"]}')"

command_id="$(aws ssm send-command \
    --instance-ids "${INSTANCE_ID}" \
    --document-name AWS-RunShellScript \
    --comment "Rider Voice ${IMAGE_TAG}" \
    --parameters "${PARAMETERS}" \
    --query 'Command.CommandId' \
    --output text)"

if [[ ! "${command_id}" =~ ^[0-9a-f-]{36}$ ]]; then
    echo "SSM did not return a valid command ID." >&2
    exit 1
fi
echo "SSM command started: ${command_id}"

invocation=""
status="Pending"
for attempt in $(seq 1 180); do
    invocation="$(aws ssm get-command-invocation \
        --command-id "${command_id}" \
        --instance-id "${INSTANCE_ID}" \
        --output json 2>/dev/null || true)"
    if [[ -z "${invocation}" ]]; then
        sleep 5
        continue
    fi

    status="$(jq -r '.Status' <<< "${invocation}")"
    case "${status}" in
        Pending|InProgress|Delayed)
            sleep 5
            ;;
        Success)
            jq -r '.StandardOutputContent' <<< "${invocation}"
            exit 0
            ;;
        *)
            jq -r '.StandardOutputContent' <<< "${invocation}"
            jq -r '.StandardErrorContent' <<< "${invocation}" >&2
            echo "SSM deployment failed with status ${status}." >&2
            exit 1
            ;;
    esac
done

if [[ -n "${invocation}" ]]; then
    jq -r '.StandardOutputContent' <<< "${invocation}"
    jq -r '.StandardErrorContent' <<< "${invocation}" >&2
fi
echo "SSM deployment timed out while status was ${status}." >&2
exit 1
