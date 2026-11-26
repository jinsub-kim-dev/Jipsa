# Jipsa 설치 가이드 — 라즈베리파이

Beetle 이 이미 돌고 있는 **라즈베리파이 두 대에 Jipsa 를 함께 올리는** 절차다.
명령마다 "보여야 할 것" 을 적어 두었다. 다르게 보이면 다음으로 넘어가지 않는다.

| 장비 | 주소 | 이미 도는 것 | Jipsa 가 더하는 것 |
|---|---|---|---|
| 앱 서버 | `192.168.45.101` | Beetle 백엔드·프론트엔드(80) | Jipsa 백엔드·프론트엔드(**8082**) |
| DB 서버 | `192.168.45.102` | `beetle-mysql`(3306) | 같은 MySQL 안에 **`jipsa` 데이터베이스와 계정** |

- MySQL 을 하나 더 띄우지 않는다. 파이 메모리를 아끼기 위해 Beetle 의 MySQL 을 같이 쓴다.
- 대신 `jipsa` 계정은 **`jipsa` 데이터베이스 안에서만** 권한을 가진다. Beetle 데이터에는 닿지 않는다.
- 전제: Beetle `SETUP.md` 2부(두 대 공통 준비 — 시간대·Docker·방화벽)와 DB 서버 기동이 끝나 있다.

로컬에서 띄우는 방법은 [README.md](README.md) 를 본다.

---

# 1부. 먼저 정할 것 — 비밀번호 1개

`jipsa` 계정의 비밀번호(`DB_PASSWORD`) 하나만 새로 정한다. **두 파이에 같은 값**을 쓴다.

**Beetle 의 두 비밀번호(`MYSQL_ROOT_PASSWORD`, Beetle 의 `DB_PASSWORD`)와 달라야 한다.**
같으면 한쪽 앱 서버 설정이 새는 순간 다른 쪽 데이터까지 열린다. 2-1 의 스크립트가 같은 값을 거부한다.

> **안전한 문자:** 영문 대소문자 · 숫자 · `-` `_` `.` `!` `#` `%`, **12자 이상**.
> `$`·공백·따옴표는 `.env` 에서 **조용히 잘린다**(Beetle `SETUP.md` 2-0).

직접 정하기 싫으면:

```bash
echo "JIPSA DB_PASSWORD: $(openssl rand -base64 18 | tr -d '/+=')"
```

---

# 2부. 배포

**DB 서버를 먼저 한다.** 데이터베이스가 없으면 앱 서버의 백엔드가 재시작을 반복한다.

## 2-1. DB 서버 (`192.168.45.102`) — 데이터베이스와 계정 만들기

```bash
ssh swiri@192.168.45.102
```

### 파일 받기

소스는 받지 않는다. 필요한 건 준비 스크립트 하나다.

```bash
git clone --depth 1 --filter=blob:none --sparse -b main https://github.com/jinsub-kim-dev/Jipsa.git && cd Jipsa
```

```bash
git sparse-checkout set --no-cone /scripts/provision-db.sh
```

```bash
ls scripts
```

> 보여야 할 것: `provision-db.sh`

### 실행

루트 비밀번호는 **Beetle 의 `.env` 에서 읽는다.** 따로 입력하지 않는다.
`<...>` 를 1부에서 정한 값으로 바꾼다.

```bash
JIPSA_DB_PASSWORD='<jipsa 비밀번호>' ./scripts/provision-db.sh ~/Beetle
```

> 보여야 할 것:
> ```
> GRANT USAGE ON *.* TO `jipsa`@`%`
> GRANT ALL PRIVILEGES ON `jipsa`.* TO `jipsa`@`%`
> 완료: beetle-mysql 에 데이터베이스 'jipsa' 과 계정 'jipsa' 가 준비됐습니다.
> ```
> **테이블은 아직 없다.** 앱 서버의 백엔드가 처음 뜰 때 만든다.

```bash
history -c && history -w
```

> 비밀번호가 셸 히스토리에 남지 않게 지운다.

- 여러 번 실행해도 안전하다. 이미 있으면 만들지 않는다.
- 방화벽은 손대지 않는다. Beetle 설치 때 앱 서버 IP 에서 3306 으로 오는 접속을 이미 열었다.

### 비밀번호를 잘못 넣었다면

스크립트는 **이미 있는 계정의 비밀번호를 바꾸지 않는다.** 직접 바꾼다.

```bash
set -a; . ~/Beetle/.env; set +a; docker exec -e MYSQL_PWD="$MYSQL_ROOT_PASSWORD" beetle-mysql mysql -uroot -e "ALTER USER 'jipsa'@'%' IDENTIFIED BY '<새 비밀번호>';"
```

> 그 뒤 앱 서버 `~/Jipsa/.env` 의 `DB_PASSWORD` 를 같은 값으로 고치고 백엔드를 재시작한다.

```bash
exit
```

## 2-2. 앱 서버 (`192.168.45.101`)

```bash
ssh swiri@192.168.45.101
```

### 방화벽에 8082 열기

```bash
sudo ufw allow 8082/tcp && sudo ufw status | grep 8082
```

> 보여야 할 것: `8082/tcp  ALLOW  Anywhere`

### DB 서버에 닿는지

```bash
timeout 3 bash -c 'cat < /dev/null > /dev/tcp/192.168.45.102/3306' && echo "열림" || echo "닫힘"
```

> 보여야 할 것: `열림` (Beetle 이 같은 경로로 붙고 있으므로 열려 있어야 정상이다)

### 메모리 여유 확인

이 파이에 JVM 이 하나 더 뜬다.

```bash
free -h
```

> `available` 이 **1GB 이상**이면 된다. 부족하면 빌드 중 OOM 으로 죽을 수 있다(5부).

### 소스 받기

여기서 **소스를 직접 빌드**하므로 전체를 받는다.

```bash
cd ~ && git clone -b main https://github.com/jinsub-kim-dev/Jipsa.git && cd Jipsa
```

### `.env` 작성

`DB_PASSWORD` 는 2-1 에서 쓴 값이다. **루트 비밀번호는 넣지 않는다.**

```bash
cat > .env <<'EOF'
DB_NAME=jipsa
DB_USER=jipsa
DB_PASSWORD=<jipsa 비밀번호>
DB_HOST=192.168.45.102
DB_PORT_TARGET=3306
FRONTEND_PORT=8082
COMPOSE_FILE=docker-compose.yml:docker-compose.prod.yml:docker-compose.app.yml
EOF
chmod 600 .env
```

```bash
sed 's/=.*/=***/' .env
```

> 보여야 할 것: 7줄. 비어 있는 값이 없어야 한다.

```bash
history -c && history -w
```

`COMPOSE_FILE` 덕분에 이 디렉토리에서는 `-f` 세 개를 매번 적지 않아도 된다.

### 빌드하고 띄운다

```bash
docker compose up -d --build
```

> **첫 빌드는 10~20분** 걸린다(파이에서 gradle·npm 을 돌린다). 로그가 조용해도 진행 중이다.

```bash
docker compose ps
```

> 보여야 할 것: `jipsa-backend`·`jipsa-frontend` 가 `healthy`. `jipsa-mysql` 은 **없어야 한다**
> (`docker-compose.app.yml` 이 이 호스트에서 뺀다). 백엔드는 마이그레이션까지 끝나야 올라오므로
> `starting` 이면 1분 더 기다린다.

```bash
docker ps --format '{{.Names}}\t{{.Ports}}'
```

> 보여야 할 것: Beetle 컨테이너(`beetle-*`, 80)와 Jipsa 컨테이너(`jipsa-*`, 8082)가 **함께** 떠 있다.

---

# 3부. 배포 확인

브라우저에서 연다.

```
http://192.168.45.101:8082
```

> 보여야 할 것: 대시보드. 데이터는 비어 있다(배포 환경은 데모 데이터를 넣지 않는다).
> **지출 추가**에서 카테고리 12개가 보이고, 하나를 등록하면 대시보드에 반영된다.

스키마가 만들어졌는지 (DB 서버에서):

```bash
ssh swiri@192.168.45.102 'set -a && . ~/Beetle/.env && set +a && docker exec -e MYSQL_PWD="$MYSQL_ROOT_PASSWORD" beetle-mysql mysql -uroot jipsa -e "SELECT version, description, success FROM flyway_schema_history;"'
```

> 보여야 할 것: `1`~`4` 가 `success=1`. **`100` (데모 시드)이 없는 것이 정상이다.**

닫혀 있어야 할 것이 닫혔는지:

```bash
for p in swagger-ui.html v3/api-docs actuator/env actuator/beans; do printf '%-18s %s\n' "$p" "$(curl -s -o /dev/null -w '%{http_code}' http://192.168.45.101:8082/$p)"; done
```

> 보여야 할 것: 전부 `404`

```bash
curl -s http://192.168.45.101:8082/actuator/health
```

> 보여야 할 것: `"status":"UP"`

Beetle 이 영향을 받지 않았는지:

```bash
curl -s -o /dev/null -w '%{http_code}\n' http://192.168.45.101/
```

> 보여야 할 것: `200`

---

# 4부. 운영

## 고정비 자동 기록

백엔드가 **기동할 때와 매월 1일 00:05** 에 그 달의 고정비를 기록한다. 몇 번 실행돼도 중복되지 않는다.
파이가 1일에 꺼져 있었어도 다음 기동 때 채워진다.

## 재배포

코드를 고쳐 `main` 에 올린 뒤(개발은 `dev` → `main`), **앱 서버 파이에서**:

```bash
cd ~/Jipsa && git pull origin main && docker compose up -d --build
```

> 새 마이그레이션이 있으면 백엔드가 기동하며 적용한다. 데이터는 유지된다.

## 로그와 상태

```bash
cd ~/Jipsa && docker compose ps
```

```bash
cd ~/Jipsa && docker compose logs -f backend
```

```bash
free -h && df -h /
```

## 백업

데이터는 DB 서버의 `beetle-mysql` 볼륨 안 `jipsa` 데이터베이스에 있다. Beetle 백업 스크립트는
`beetle` 데이터베이스만 받으므로 **Jipsa 백업을 따로 만든다.** DB 서버에서:

```bash
mkdir -p ~/backup
cat > ~/backup-jipsa.sh <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
set -a; . "$HOME/Beetle/.env"; set +a

STAMP="$(date +%Y%m%d)"
TMP="$HOME/backup/.jipsa-$STAMP.part"
trap 'rm -f "$TMP"' EXIT

docker exec -e MYSQL_PWD="$MYSQL_ROOT_PASSWORD" beetle-mysql \
  mysqldump -uroot --single-transaction --no-tablespaces jipsa > "$TMP"

if [ ! -s "$TMP" ] || ! grep -q "Dump completed" "$TMP"; then
  echo "백업이 불완전합니다. 기존 백업은 건드리지 않습니다." >&2
  exit 1
fi

gzip -c "$TMP" > "$HOME/backup/jipsa-$STAMP.sql.gz"
find "$HOME/backup" -name 'jipsa-*.sql.gz' -mtime +14 -delete
echo "백업 완료: jipsa-$STAMP.sql.gz"
EOF
chmod +x ~/backup-jipsa.sh
```

**먼저 손으로 돌려 본다.**

```bash
~/backup-jipsa.sh && ls -la ~/backup | grep jipsa
```

> 보여야 할 것: `백업 완료: jipsa-<날짜>.sql.gz` 와 그 파일.

매일 새벽에 돌린다. Beetle 백업(03:30)과 겹치지 않게 03:40 으로 둔다.

```bash
crontab -e
```

```
40 3 * * * /home/swiri/backup-jipsa.sh >> /home/swiri/backup/backup.log 2>&1
```

**덤프를 파이 밖으로 옮긴다.** Beetle 과 같은 폴더(`~/backup`)이므로 Beetle 의 rsync 가 함께 가져간다.

## 복구

DB 서버에서:

```bash
set -a && . ~/Beetle/.env && set +a
gunzip -c ~/backup/jipsa-20261201.sql.gz | docker exec -i -e MYSQL_PWD="$MYSQL_ROOT_PASSWORD" beetle-mysql mysql -uroot jipsa
```

## 되돌리기

**앱 서버 파이에서** 이전 커밋으로 체크아웃해 다시 빌드한다.

```bash
cd ~/Jipsa && git checkout <이전 커밋> && docker compose up -d --build
```

> **DB 마이그레이션은 되돌아가지 않는다.** 스키마가 이미 바뀌었다면 이전 버전이 뜨지 않을 수 있다.
> 그때는 백업으로 DB 도 함께 복구한다.

## 재부팅

모든 서비스가 `restart: unless-stopped` 라 재부팅해도 알아서 올라온다. **DB 서버를 먼저 켠다.**
앱 서버가 먼저 떠도 백엔드가 DB 를 찾을 때까지 재시작을 반복하다가 붙는다.

## 내리기

Jipsa 만 내리고 Beetle 은 그대로 둔다. 앱 서버에서:

```bash
cd ~/Jipsa && docker compose down
```

> 데이터는 DB 서버에 있으므로 남는다. 데이터까지 지우려면 DB 서버에서 `DROP DATABASE jipsa;` 를
> 직접 실행해야 한다. **`beetle-mysql` 에 `down -v` 를 하면 Beetle 데이터까지 사라진다.**

---

# 5부. 문제 해결

| 증상 | 원인과 해결 |
|---|---|
| 스크립트가 `jipsa 비밀번호가 ... 와 같습니다` | Beetle 비밀번호와 다른 값으로 다시 정한다 (1부) |
| 스크립트가 `beetle-mysql 컨테이너가 healthy 상태가 아닙니다` | DB 서버에서 Beetle MySQL 이 떠 있는지 본다. `cd ~/Beetle && docker compose ps` |
| 스크립트가 `jipsa 계정으로 접속하지 못했습니다` | 계정이 이미 있고 비밀번호가 다르다. 2-1 "비밀번호를 잘못 넣었다면" |
| 백엔드가 `Communications link failure` 로 재시작 반복 | DB 에 닿지 못한다. 2-2 의 포트 확인, `.env` 의 `DB_HOST`/`DB_PORT_TARGET` |
| 백엔드가 `Access denied for user 'jipsa'` | 앱 서버 `.env` 의 `DB_PASSWORD` 가 2-1 과 다르다 |
| 백엔드가 `Unknown database 'jipsa'` | 2-1 을 하지 않았다 |
| `required variable ... is missing` | 앱 서버 `.env` 에 값이 빠졌다. 부록 참고 |
| `Bind for 0.0.0.0:8082 failed: port is already allocated` | 다른 프로세스가 8082 를 쓴다. `.env` 의 `FRONTEND_PORT` 를 바꾸고 방화벽도 맞춘다 |
| 브라우저에서 `:8082` 가 열리지 않는다 | 앱 서버 ufw 에 8082 가 없다 (2-2) |
| 지출 추가에 카테고리가 없다 | 마이그레이션 V4 가 적용되지 않았다. 3부의 `flyway_schema_history` 를 본다 |
| 재배포했는데 바뀐 것이 안 보인다 | 컨테이너가 새 이미지로 바뀌지 않았다. `docker compose up -d --build --force-recreate` |
| 빌드 중 프로세스가 죽는다 (OOM) | 파이 메모리가 부족하다. swap 을 늘리거나 Beetle 이 한가할 때 다시 빌드한다 |

---

# 부록. 앱 서버 `.env`

| 변수 | 필수 | 값 |
|---|:---:|---|
| `DB_NAME` | ✅ | `jipsa` |
| `DB_USER` | ✅ | `jipsa` |
| `DB_PASSWORD` | ✅ | 2-1 에서 쓴 값. **Beetle 비밀번호와 달라야 한다** |
| `DB_HOST` | ✅ | `192.168.45.102` |
| `DB_PORT_TARGET` | ✅ | `3306` |
| `FRONTEND_PORT` | | 기본 `8082` |
| `COMPOSE_FILE` | | `docker-compose.yml:docker-compose.prod.yml:docker-compose.app.yml` |
| `DB_POOL_SIZE` | | 기본 `10` |

`MYSQL_ROOT_PASSWORD` 는 **앱 서버에 두지 않는다.** DB 서버의 Beetle `.env` 에만 있다.
`.env.example` 은 로컬 기준값이다. 파이에 복사하지 않는다.

---

# 마지막으로 — 인증이 없다

Beetle 과 같다. **로그인이 없고 HTTPS 도 없다.** 주소를 아는 누구나 읽고 쓰고 지울 수 있다.
집 네트워크 안에서만 쓰고, 공유기에서 8082 를 외부로 포워딩하지 않는다.
