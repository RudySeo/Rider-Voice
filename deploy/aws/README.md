# Rider Voice AWS 백엔드 배포 가이드

이 문서는 서울 리전(`ap-northeast-2`)의 기존 Ubuntu x86_64 EC2 한 대에 backend를 배포하는 절차다. 운영 Prometheus와 Grafana, frontend, ALB, ECS, Route 53, NAT Gateway와 Multi-AZ는 사용하지 않는다. AWS Budget은 알림일 뿐 리소스나 지출을 자동으로 중지하지 않는다.

## 준비할 값

- AWS 계정 ID
- 기존 EC2 instance ID, VPC, subnet과 security group
- 비용 알림을 받을 이메일
- Kakao REST API key와 client secret
- Docker Hub 사용자명
- RDS master 비밀번호, `rider_app` 비밀번호, `rider_migration` 비밀번호

비밀번호와 token은 이 저장소, 채팅, GitHub 변수나 workflow 입력에 붙여 넣지 않는다. AWS Console의 RDS 입력 화면과 SSM SecureString 화면에 직접 입력한다.

## 1. 월 비용 알림

AWS Console에서 `Billing and Cost Management` → `Budgets` → `Create budget`을 선택한다.

- Monthly cost budget: `$30`
- 실제 비용 80%: 이메일 알림
- 실제 비용 100%: 이메일 알림

## 2. Elastic IP와 EC2 security group

1. `EC2` → `Elastic IP addresses`에서 주소를 하나 할당해 기존 EC2에 연결한다.
2. Elastic IP가 `203.0.113.10`이라면 임시 도메인은 `203-0-113-10.sslip.io`다.
3. EC2 security group inbound를 다음으로 제한한다.

Elastic IP를 포함한 public IPv4 주소는 EC2에 연결되어 있거나 EC2가 중지되어 있어도 시간당 비용이 발생한다. 운영을 끝내면 연결만 해제하지 말고 더 이상 사용하지 않는 주소를 release한다.

| 포트 | 소스 | 용도 |
| --- | --- | --- |
| 80/TCP | `0.0.0.0/0` | Let's Encrypt HTTP-01 및 HTTPS 이동 |
| 443/TCP | `0.0.0.0/0` | 공개 HTTPS API |
| 22/TCP | 현재 관리자 공인 IP `/32` | 최초 bootstrap만 사용하고 SSM 검증 후 삭제 |

8080은 inbound에 추가하지 않는다. EC2 outbound는 Docker Hub, AWS API, Let's Encrypt와 패키지 저장소에 HTTPS로 연결할 수 있어야 한다.

## 3. EC2 instance role과 SSM

`IAM`에서 EC2용 role을 만들고 기존 EC2에 instance profile로 연결한다.

1. AWS managed policy `AmazonSSMManagedInstanceCore`를 연결한다.
2. [ec2-parameter-policy.json](ec2-parameter-policy.json)의 `__AWS_ACCOUNT_ID__`를 실제 값으로 바꾼 inline policy를 추가한다.
3. custom KMS key를 쓰지 않고 Parameter Store 기본 `aws/ssm` key를 사용한다. custom key로 변경할 때만 해당 key의 `kms:Decrypt`를 별도로 허용한다.

EC2에서 SSM agent가 online이 되고 `Systems Manager` → `Fleet Manager`에 managed node로 나타나야 한다. bootstrap 완료 후 `Session Manager` 접속과 `Run Command`를 각각 확인한 다음 security group의 22/TCP 규칙을 삭제한다.

## 4. private RDS MySQL

`RDS` → `Create database`에서 다음 값을 사용한다.

- Standard create
- Engine: MySQL 8.4.10
- Template: Free tier가 보이면 선택하고, 아니면 Dev/Test
- Availability: Single-AZ
- Instance: `db.t4g.micro`
- Storage: gp3 20 GiB, storage encryption 활성화
- VPC: EC2와 같은 VPC
- Public access: No
- Initial database name: `rider`
- Backup retention: 7일
- Deletion protection(삭제 방지): 활성화
- Auto minor version upgrade: 비활성화
- Performance Insights와 Enhanced Monitoring: 초기에는 비활성화

RDS 전용 security group을 만들고 inbound `3306/TCP`의 소스를 IP가 아닌 EC2 security group ID로 지정한다. 다른 inbound 규칙은 추가하지 않는다.

RDS가 Available이 되면 EC2에서 global CA bundle과 RDS endpoint를 사용해 master 계정으로 접속한다.

```bash
curl --fail --location \
  https://truststore.pki.rds.amazonaws.com/global/global-bundle.pem \
  --output /tmp/rds-global-bundle.pem

MYSQL_HISTFILE=/dev/null mysql \
  --host=<RDS_ENDPOINT> \
  --user=<RDS_MASTER_USERNAME> \
  --password \
  --ssl-mode=VERIFY_IDENTITY \
  --ssl-ca=/tmp/rds-global-bundle.pem
```

MySQL prompt 안에서 서로 다른 강한 비밀번호로 두 계정을 만든다. 아래 `<...>` 자리에는 실제 비밀번호를 입력하고 작업 후 terminal scrollback을 정리한다.

```sql
CREATE USER 'rider_migration'@'%' IDENTIFIED BY '<MIGRATION_PASSWORD>' REQUIRE SSL;
CREATE USER 'rider_app'@'%' IDENTIFIED BY '<APP_PASSWORD>' REQUIRE SSL;

GRANT CREATE, ALTER, DROP, INDEX, REFERENCES, SELECT, INSERT, UPDATE, DELETE
ON rider.* TO 'rider_migration'@'%';

GRANT SELECT, INSERT, UPDATE, DELETE
ON rider.* TO 'rider_app'@'%';

SHOW GRANTS FOR 'rider_migration'@'%';
SHOW GRANTS FOR 'rider_app'@'%';
```

RDS master 계정은 애플리케이션과 SSM `/rider-voice/prod/` 경로에 저장하지 않는다.

## 5. SSM Parameter Store

`Systems Manager` → `Parameter Store`에서 Standard tier 파라미터를 만든다. 이름은 대소문자를 포함해 정확히 일치해야 한다.

| 이름 | 타입 | 값의 형태 |
| --- | --- | --- |
| `/rider-voice/prod/DB_URL` | String | 아래 JDBC URL |
| `/rider-voice/prod/DB_USERNAME` | String | `rider_app` |
| `/rider-voice/prod/DB_PASSWORD` | SecureString | runtime 비밀번호 |
| `/rider-voice/prod/DB_MIGRATION_USERNAME` | String | `rider_migration` |
| `/rider-voice/prod/DB_MIGRATION_PASSWORD` | SecureString | migration 비밀번호 |
| `/rider-voice/prod/KAKAO_CLIENT_ID` | String | Kakao REST API key |
| `/rider-voice/prod/KAKAO_CLIENT_SECRET` | SecureString | 값이 있을 때만 생성 |
| `/rider-voice/prod/KAKAO_LOCAL_REST_API_KEY` | SecureString | Kakao Local REST API key |
| `/rider-voice/prod/KAKAO_REDIRECT_URI` | String | `https://<DOMAIN>/api/v1/auth/oauth2/callback/kakao` |

`DB_URL`은 다음 형식을 사용한다.

```text
jdbc:mysql://<RDS_ENDPOINT>:3306/rider?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&sslMode=VERIFY_IDENTITY
```

SSM에는 위 기본 URL만 저장한다. EC2 배포 script가 container에 mount한 RDS CA truststore의 Connector/J 속성을 runtime과 Flyway가 함께 사용하는 `DB_URL`에 추가한다. `trustCertificateKeyStore*` 속성을 SSM 값에 직접 중복해서 넣지 않는다. JVM 기본 truststore는 Kakao 같은 공개 HTTPS provider 인증에 사용하고 RDS 전용 truststore로 교체하지 않는다.

Kakao developer console의 redirect URI에는 backend callback URL을 정확히 등록한다. 로그인 완료 후 서버는 모바일 앱의 `ridervoice://auth/callback` deep link로 이동한다.

## 6. EC2 bootstrap과 최초 배포

먼저 80/443 inbound, Elastic IP와 `sslip.io` 주소가 준비되어 있어야 한다. 로컬에서 배포 asset을 EC2로 복사하고 bootstrap을 실행한다.

```bash
scp -r deploy/ec2 ubuntu@<ELASTIC_IP>:/tmp/rider-voice-ec2
ssh ubuntu@<ELASTIC_IP>
sudo /tmp/rider-voice-ec2/bootstrap.sh <EIP-WITH-DASHES>.sslip.io <CERTIFICATE_EMAIL>
```

bootstrap은 Docker, Nginx, Certbot, AWS CLI, MySQL client와 SSM agent를 준비하고 MySQL 연결에만 사용하는 RDS CA truststore를 설치한다. 완료 후 현재 검증된 최초 backend 이미지를 배포한다. 운영 EC2에는 Prometheus와 Grafana를 설치하지 않는다.

이미 bootstrap이 끝난 EC2의 일반 release에서는 GitHub Actions가 병합된 정확한 commit SHA의 release script를 짧은 SSM Run Command로 transient systemd service에 등록한다. GitHub는 별도의 짧은 SSM 명령으로 상태 파일과 exit status를 확인해 image pull과 health 검증을 SSM document worker의 IPC 수명에서 분리한다. 이 조회 명령은 Ubuntu의 `/bin/sh`에서 동작하는 POSIX 문법을 사용한다. systemd unit 종료와 상태 파일 확인이 겹치는 순간에는 상태 파일을 다시 확인하고 제한된 횟수만큼 재시도하며, 재시도 후에도 결과가 없을 때만 release 실패로 판정한다. 이 script는 backend image와 Nginx 설정만 갱신한다. 아래 수동 bootstrap 절차는 최초 설치 또는 자동 복구가 불가능한 경우에만 사용한다. `<MERGED_COMMIT_SHA>`는 GitHub master의 40자리 commit SHA로 바꾸고, 현재 Nginx의 domain과 기존 Let's Encrypt 등록 email을 그대로 사용한다.

```bash
MERGED_COMMIT_SHA="replace-with-40-character-lowercase-commit-sha"
case "${MERGED_COMMIT_SHA}" in
  *[!0-9a-f]*|'') echo "40자리 소문자 commit SHA가 필요합니다." >&2; exit 1 ;;
esac
test "${#MERGED_COMMIT_SHA}" -eq 40

ASSET_ROOT="$(mktemp -d /tmp/rider-voice-assets.XXXXXX)"
install -d "${ASSET_ROOT}/source"
curl --fail --silent --show-error --location \
  "https://github.com/RudySeo/Rider-Voice/archive/${MERGED_COMMIT_SHA}.tar.gz" \
  --output "${ASSET_ROOT}/source.tar.gz"
tar --extract --gzip \
  --file "${ASSET_ROOT}/source.tar.gz" \
  --directory "${ASSET_ROOT}/source" \
  --strip-components=1
cp -a "${ASSET_ROOT}/source/deploy/ec2" "${ASSET_ROOT}/rider-voice-ec2"
sudo "${ASSET_ROOT}/rider-voice-ec2/bootstrap.sh" \
  "203-0-113-10.sslip.io" \
  "admin@example.com"
```

```bash
sudo /opt/rider-voice/deploy.sh <DOCKERHUB_USERNAME>/rider-voice-api sha-81eb17b7a492
curl --fail https://<EIP-WITH-DASHES>.sslip.io/actuator/health
sudo /snap/bin/certbot renew --dry-run
```

Docker API의 8080 binding이 `127.0.0.1`인지 확인하고 외부에서 `http://<DOMAIN>`이 HTTPS로 이동하는지 확인한다. 외부 `https://<DOMAIN>/actuator/prometheus`, `/grafana`와 `/grafana/` 요청은 `404`여야 한다.

자동 release는 기존 운영 monitoring container와 named volume, `/opt/rider-voice/monitoring`을 제거하고 미사용 Docker image를 정리한다. API container와 현재 rollback image는 참조 상태로 보존한다. 정확한 master commit의 Nginx 설정을 검증한 뒤 `/grafana` 경로를 `404`로 전환하고 새 backend image를 배포한다. API image health 실패는 기존과 같이 직전 image로 자동 복구한다.

## 7. 로컬 Prometheus와 Grafana

Prometheus와 Grafana는 개발 PC에서 필요할 때만 실행한다. 로컬 Spring API를 `localhost:8080`에서 먼저 실행하고 저장소 루트에서 다음 명령을 사용한다.

```bash
cp monitoring/.env.example monitoring/.env
# monitoring/.env의 GRAFANA_ADMIN_PASSWORD를 로컬 전용 값으로 변경
docker compose --env-file monitoring/.env -f monitoring/compose.yml up --detach
```

Grafana는 `http://localhost:3000`, Prometheus는 `http://localhost:9090`에서 확인한다. 종료할 때는 `down`으로 local volume을 보존하고 데이터를 의도적으로 초기화할 때만 `down --volumes`를 사용한다. 운영 문제는 공개 health endpoint, application log와 SSM 진단으로 확인한다.

## 8. GitHub OIDC deploy role

1. `IAM` → `Identity providers`에서 provider URL `https://token.actions.githubusercontent.com`, audience `sts.amazonaws.com`인 OIDC provider를 만든다.
2. GitHub deploy role을 만든다.
3. [github-oidc-trust-policy.json](github-oidc-trust-policy.json)의 계정 ID를 바꿔 trust policy로 사용한다. `sub`는 `repo:RudySeo@78248966/Rider-Voice@1308728176:environment:production` 그대로 제한한다.
4. [github-ssm-deploy-policy.json](github-ssm-deploy-policy.json)의 계정 ID와 EC2 instance ID를 바꿔 inline policy로 연결한다.

이 저장소는 2026년 7월 15일 이후 생성되어 GitHub의 immutable OIDC subject claim을 사용한다. `78248966`은 GitHub owner ID, `1308728176`은 repository ID이며 secret이 아니다. 이름만 포함한 구형 `repo:RudySeo/Rider-Voice:environment:production` subject는 실제 token과 일치하지 않아 `sts:AssumeRoleWithWebIdentity`가 거부된다. 영구 ID를 제거하거나 wildcard로 바꾸지 않는다.

이 role에는 Parameter Store 읽기, EC2 변경, RDS 변경과 IAM 변경 권한을 주지 않는다. 지정 EC2에서 `AWS-RunShellScript`를 실행하고 결과를 읽는 권한만 가진다.

GitHub 저장소 `Settings` → `Environments`에서 `production` environment를 만들고 deployment branch를 `master`로 제한한다. environment variables를 추가한다.

| 변수 | 값 |
| --- | --- |
| `AWS_REGION` | `ap-northeast-2` |
| `AWS_DEPLOY_ROLE_ARN` | 생성한 GitHub deploy role ARN |
| `EC2_INSTANCE_ID` | 기존 EC2 instance ID |
| `DOCKERHUB_USERNAME` | Docker Hub 사용자명 |

`AWS_ACCESS_KEY_ID`와 `AWS_SECRET_ACCESS_KEY` secret은 만들지 않는다. master workflow는 Docker Hub 게시가 성공한 뒤에만 OIDC role을 얻어 SSM 배포를 실행한다.

일반 release는 새 image를 받기 전에 어떤 container도 참조하지 않는 Docker image를 자동 정리하고 `/var/lib/containerd`의 남은 용량을 기록한다. 저장 공간 부족으로 배포가 중단되면 `Backend master publish`를 master에서 수동으로 다시 실행한다. API container, network와 volume은 정리 대상에 포함하지 않는다.

## 9. 완료 확인과 rollback

- Session Manager 접속 성공 후 EC2 security group에서 22/TCP 삭제
- RDS public access가 No이고 3306 소스가 EC2 security group뿐인지 확인
- `docker inspect rider-voice-api`에서 image가 병합 commit의 `sha-<12자리>`인지 확인
- `curl https://<DOMAIN>/actuator/health`가 `UP`인지 확인
- 외부 `curl https://<DOMAIN>/actuator/prometheus`가 `404`인지 확인
- 외부 `curl https://<DOMAIN>/grafana`와 `/grafana/`가 `404`인지 확인
- `docker ps -a`와 `docker volume ls`에 Rider Voice 운영 Prometheus·Grafana 자원이 없는지 확인
- GitHub Actions 로그와 EC2 Docker 로그에 password와 token이 없는지 확인
- EC2 재부팅 후 Nginx와 container가 자동 시작하는지 확인

전환 release가 성공한 뒤 AWS Console의 `Systems Manager` → `Parameter Store`에서 더 이상 사용하지 않는 `/rider-voice/prod/GRAFANA_ADMIN_PASSWORD`를 삭제한다. GitHub deploy role과 EC2 instance role에는 parameter 삭제 권한을 추가하지 않는다.

자동 배포 실패 시 EC2 script는 직전 image를 다시 시작하고 GitHub job은 실패로 남긴다. 수동 rollback은 GitHub Actions의 `Backend production rollback`에서 이전 `sha-<12자리>`를 입력한다. Flyway migration은 자동으로 되돌리지 않으므로 컬럼 삭제나 이름 변경은 호환 가능한 두 번의 배포로 나눈다.

배포 asset이 변경되면 새 버전의 `deploy/ec2` 디렉터리를 EC2로 복사하고 bootstrap을 다시 실행해 `/opt/rider-voice/deploy.sh`와 Nginx 설정을 갱신한다. 운영 monitoring이 필요해지면 EC2 disk 용량, 별도 host 또는 관리형 관측 서비스를 먼저 결정하고 ADR을 추가한다.
