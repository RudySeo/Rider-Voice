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
readonly RELEASE_RUN_ID="$(date -u +%s)-$$"
readonly UNIT_NAME="rider-voice-release-${RELEASE_SHA:0:12}-${RELEASE_RUN_ID}"
readonly REMOTE_SCRIPT="/tmp/rider-voice-deploy-release-${RELEASE_SHA}.sh"
readonly REMOTE_LOG_FILE="/tmp/rider-voice-release-${RELEASE_RUN_ID}.log"
readonly REMOTE_STATUS_FILE="/tmp/rider-voice-release-${RELEASE_RUN_ID}.status"
readonly REMOTE_STATUS_TMP="${REMOTE_STATUS_FILE}.tmp"
readonly MISSING_GRACE_ATTEMPTS=3
readonly START_COMMAND="rm -f ${REMOTE_SCRIPT} ${REMOTE_LOG_FILE} ${REMOTE_STATUS_FILE} ${REMOTE_STATUS_TMP} && curl --fail --silent --show-error --location ${RELEASE_SCRIPT_URL} --output ${REMOTE_SCRIPT} && chmod 0700 ${REMOTE_SCRIPT} && sudo systemd-run --no-block --unit ${UNIT_NAME} --property=Type=oneshot /bin/bash -c 'set +e; /bin/bash ${REMOTE_SCRIPT} ${IMAGE_NAME} ${IMAGE_TAG} ${RELEASE_SHA} > ${REMOTE_LOG_FILE} 2>&1; release_status=\$?; printf \"%s\\n\" \"\$release_status\" > ${REMOTE_STATUS_TMP}; mv -f ${REMOTE_STATUS_TMP} ${REMOTE_STATUS_FILE}; rm -f ${REMOTE_SCRIPT}; exit \"\$release_status\"'"

SSM_OUTPUT=""

run_ssm_command() {
    local remote_command="$1"
    local comment="$2"
    local parameters
    local command_id
    local invocation=""
    local status="Pending"

    parameters="$(jq -cn \
        --arg command "${remote_command}" \
        '{commands: [$command], executionTimeout: ["60"]}')"
    command_id="$(aws ssm send-command \
        --instance-ids "${INSTANCE_ID}" \
        --document-name AWS-RunShellScript \
        --comment "${comment}" \
        --parameters "${parameters}" \
        --query 'Command.CommandId' \
        --output text)"

    if [[ ! "${command_id}" =~ ^[0-9a-f-]{36}$ ]]; then
        echo "SSM did not return a valid command ID." >&2
        return 1
    fi

    for attempt in $(seq 1 24); do
        invocation="$(aws ssm get-command-invocation \
            --command-id "${command_id}" \
            --instance-id "${INSTANCE_ID}" \
            --output json 2>/dev/null || true)"
        if [[ -z "${invocation}" ]]; then
            sleep 2
            continue
        fi

        status="$(jq -r '.Status' <<< "${invocation}")"
        case "${status}" in
            Pending|InProgress|Delayed)
                sleep 2
                ;;
            Success)
                SSM_OUTPUT="$(jq -r '.StandardOutputContent' <<< "${invocation}")"
                return 0
                ;;
            *)
                jq -r '.StandardOutputContent' <<< "${invocation}"
                jq -r '.StandardErrorContent' <<< "${invocation}" >&2
                echo "SSM command failed with status ${status}: ${comment}" >&2
                return 1
                ;;
        esac
    done

    if [[ -n "${invocation}" ]]; then
        jq -r '.StandardOutputContent' <<< "${invocation}"
        jq -r '.StandardErrorContent' <<< "${invocation}" >&2
    fi
    echo "SSM command timed out while status was ${status}: ${comment}" >&2
    return 1
}

start_release() {
    run_ssm_command "${START_COMMAND}" "Start Rider Voice ${IMAGE_TAG}"
    printf '%s\n' "${SSM_OUTPUT}"
}

print_release_log() {
    local log_command
    log_command="tail -c 20000 ${REMOTE_LOG_FILE} 2>/dev/null || true; rm -f ${REMOTE_LOG_FILE} ${REMOTE_STATUS_FILE} ${REMOTE_STATUS_TMP} ${REMOTE_SCRIPT}; sudo systemctl reset-failed ${UNIT_NAME} >/dev/null 2>&1 || true"
    if run_ssm_command "${log_command}" "Read Rider Voice ${IMAGE_TAG} result"; then
        printf '%s\n' "${SSM_OUTPUT}"
    fi
}

poll_release() {
    local poll_command
    local release_state
    local release_status
    local missing_attempts=0

    poll_command="if [ -f ${REMOTE_STATUS_FILE} ]; then printf 'COMPLETE '; cat ${REMOTE_STATUS_FILE}; else unit_state=\$(systemctl is-active ${UNIT_NAME} 2>/dev/null || true); case \"\${unit_state}\" in active|activating|deactivating) echo RUNNING ;; *) if [ -f ${REMOTE_STATUS_FILE} ]; then printf 'COMPLETE '; cat ${REMOTE_STATUS_FILE}; else echo MISSING; fi ;; esac; fi"
    for attempt in $(seq 1 150); do
        run_ssm_command "${poll_command}" "Check Rider Voice ${IMAGE_TAG}"
        release_state="$(tr -d '\r\n' <<< "${SSM_OUTPUT}")"
        case "${release_state}" in
            RUNNING)
                missing_attempts=0
                sleep 5
                ;;
            COMPLETE\ *)
                release_status="${release_state#COMPLETE }"
                print_release_log
                if [[ "${release_status}" == "0" ]]; then
                    return 0
                fi
                echo "Rider Voice release exited with status ${release_status}." >&2
                return 1
                ;;
            MISSING)
                missing_attempts=$((missing_attempts + 1))
                if (( missing_attempts < MISSING_GRACE_ATTEMPTS )); then
                    sleep 2
                    continue
                fi
                print_release_log
                echo "Rider Voice systemd release unit disappeared before writing status after ${MISSING_GRACE_ATTEMPTS} checks." >&2
                return 1
                ;;
            *)
                echo "Unexpected Rider Voice release state: ${release_state}" >&2
                return 1
                ;;
        esac
    done

    print_release_log
    echo "Rider Voice release timed out while systemd unit ${UNIT_NAME} was running." >&2
    return 1
}

start_release
poll_release
