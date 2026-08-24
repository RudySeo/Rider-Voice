# Rider Voice AWS 백엔드 배포 가이드

이 문서는 서울 리전(`ap-northeast-2`)의 기존 Ubuntu x86_64 EC2 한 대에 backend와 비공개 Prometheus·Grafana 관측 stack을 배포하는 절차다. frontend, ALB, ECS, Route 53, NAT Gateway와 Multi-AZ는 사용하지 않는다. AWS Budget은 알림일 뿐 리소스나 지출을 자동으로 중지하지 않는다.

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

3000, 8080과 9090은 inbound에 추가하지 않는다. EC2 outbound는 Docker Hub, AWS API, Let's Encrypt와 패키지 저장소에 HTTPS로 연결할 수 있어야 한다.

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
| `/rider-voice/prod/FRONTEND_BASE_URL` | String | `https://<DOMAIN>` |
| `/rider-voice/prod/AUTH_COOKIE_SECURE` | String | `true` |
| `/rider-voice/prod/GRAFANA_ADMIN_PASSWORD` | SecureString | Grafana 전용 강한 관리자 비밀번호 |

`DB_URL`은 다음 형식을 사용한다.

```text
jdbc:mysql://<RDS_ENDPOINT>:3306/rider?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&sslMode=VERIFY_IDENTITY
```

SSM에는 위 기본 URL만 저장한다. EC2 배포 script가 container에 mount한 RDS CA truststore의 Connector/J 속성을 runtime과 Flyway가 함께 사용하는 `DB_URL`에 추가한다. `trustCertificateKeyStore*` 속성을 SSM 값에 직접 중복해서 넣지 않는다. JVM 기본 truststore는 Kakao 같은 공개 HTTPS provider 인증에 사용하고 RDS 전용 truststore로 교체하지 않는다.

frontend가 아직 배포되지 않았으므로 OAuth callback 후 이동하는 frontend 화면은 동작하지 않는다. Kakao developer console의 redirect URI에는 backend callback URL을 정확히 등록하되, 현재 운영 확인은 공개 API, OpenAPI와 health endpoint까지만 수행한다.

## 6. EC2 bootstrap과 최초 배포

먼저 80/443 inbound, Elastic IP와 `sslip.io` 주소가 준비되어 있어야 한다. 로컬에서 배포 asset을 EC2로 복사하고 bootstrap을 실행한다.

```bash
scp -r deploy/ec2 ubuntu@<ELASTIC_IP>:/tmp/rider-voice-ec2
scp -r monitoring ubuntu@<ELASTIC_IP>:/tmp/monitoring
ssh ubuntu@<ELASTIC_IP>
sudo /tmp/rider-voice-ec2/bootstrap.sh <EIP-WITH-DASHES>.sslip.io <CERTIFICATE_EMAIL>
```

bootstrap은 Docker와 Compose plugin, Nginx, Certbot, AWS CLI, MySQL client와 SSM agent를 준비하고 MySQL 연결에만 사용하는 RDS CA truststore와 관측 stack의 Compose 자산을 설치한다. 완료 후 현재 검증된 최초 이미지를 배포하고 Grafana secret 파일을 준비한 다음 관측 stack을 시작한다.

```bash
sudo /opt/rider-voice/deploy.sh <DOCKERHUB_USERNAME>/rider-voice-api sha-81eb17b7a492
sudo sh -ceu '
  umask 077
  grafana_admin_password="$(aws ssm get-parameter \
    --region ap-northeast-2 \
    --name /rider-voice/prod/GRAFANA_ADMIN_PASSWORD \
    --with-decryption \
    --query Parameter.Value \
    --output text)"
  test -n "${grafana_admin_password}"
  printf "%s" "${grafana_admin_password}" \
    > /opt/rider-voice/monitoring/secrets/grafana_admin_password
  test -s /opt/rider-voice/monitoring/secrets/grafana_admin_password
  unset grafana_admin_password
'
sudo docker compose \
  -f /opt/rider-voice/monitoring/compose.prod.yml \
  up --detach --wait --pull always
curl --fail https://<EIP-WITH-DASHES>.sslip.io/actuator/health
sudo /snap/bin/certbot renew --dry-run
```

Docker의 3000, 8080과 9090 binding이 `127.0.0.1`인지 확인하고 외부에서 `http://<DOMAIN>`이 HTTPS로 이동하는지 확인한다. 외부 `https://<DOMAIN>/actuator/prometheus` 요청은 `404`여야 한다.

## 7. Prometheus와 Grafana 접속

운영 Compose는 Prometheus와 Grafana만 관리한다. API는 health check와 rollback을 유지하기 위해 기존 `deploy.sh`가 관리한다. Prometheus는 `rider-voice-observability` Docker network에서 `rider-voice-api:8080/actuator/prometheus`를 수집한다. Grafana datasource와 Rider Voice dashboard는 자동으로 provisioning되며 Prometheus는 7일 또는 2GB 중 먼저 도달하는 시점까지 metric을 보관한다.

두 UI는 인터넷에 공개하지 않는다. 관리자 PC에 AWS CLI와 Session Manager plugin을 설치하고 다음 명령으로 Grafana를 local port에 연결한다.
접속하는 IAM principal에는 대상 instance의 `ssm:StartSession`과 session 종료 권한을 최소 범위로 부여한다. GitHub deploy role에는 이 권한을 추가하지 않는다.

```bash
aws ssm start-session \
  --region ap-northeast-2 \
  --target <EC2_INSTANCE_ID> \
  --document-name AWS-StartPortForwardingSession \
  --parameters '{"portNumber":["3000"],"localPortNumber":["3000"]}'
```

session이 열린 동안 browser에서 `http://localhost:3000`으로 접속한다. Prometheus UI가 필요할 때는 별도 terminal에서 `portNumber`와 `localPortNumber`를 모두 `9090`으로 바꿔 실행한다. port forwarding session 내용은 기록되지 않으므로 운영 접근 권한은 최소 인원에게만 부여한다.

Grafana container는 root 전용 파일을 Compose secret으로 mount하고 `GF_SECURITY_ADMIN_PASSWORD__FILE`로 읽는다. 관리자 비밀번호는 최초 database 생성 때만 적용된다. 비밀번호를 회전할 때는 SSM 값을 바꾸고 secret 파일을 다시 내려받은 뒤 EC2에서 Grafana CLI로 기존 database의 관리자 비밀번호도 함께 변경하고 container를 재기동한다.

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

## 9. 완료 확인과 rollback

- Session Manager 접속 성공 후 EC2 security group에서 22/TCP 삭제
- RDS public access가 No이고 3306 소스가 EC2 security group뿐인지 확인
- `docker inspect rider-voice-api`에서 image가 병합 commit의 `sha-<12자리>`인지 확인
- `curl https://<DOMAIN>/actuator/health`가 `UP`인지 확인
- 외부 `curl https://<DOMAIN>/actuator/prometheus`가 `404`인지 확인
- `curl http://127.0.0.1:9090/-/ready`와 `curl http://127.0.0.1:3000/api/health`가 성공하는지 확인
- Prometheus query `up{job="rider-voice-api"}`가 `1`이고 Grafana dashboard가 데이터를 표시하는지 확인
- GitHub Actions 로그와 EC2 Docker 로그에 password와 token이 없는지 확인
- EC2 재부팅 후 Nginx와 container가 자동 시작하는지 확인

자동 배포 실패 시 EC2 script는 직전 image를 다시 시작하고 GitHub job은 실패로 남긴다. 수동 rollback은 GitHub Actions의 `Backend production rollback`에서 이전 `sha-<12자리>`를 입력한다. Flyway migration은 자동으로 되돌리지 않으므로 컬럼 삭제나 이름 변경은 호환 가능한 두 번의 배포로 나눈다.

배포 asset이 변경되면 새 버전의 `deploy/ec2`와 `monitoring` 디렉터리를 EC2로 복사하고 bootstrap을 다시 실행해 `/opt/rider-voice/deploy.sh`와 관측 Compose 설정을 갱신한다. 이후 `docker compose -f /opt/rider-voice/monitoring/compose.prod.yml up --detach --wait --pull always`를 다시 실행한다. API와 관측 stack은 같은 EC2에 있으므로 instance 장애 시 둘 다 중단된다. 외부 장애 감지가 필요해지면 별도 host나 관리형 관측 서비스를 결정한다.
