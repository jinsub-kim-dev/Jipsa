#!/usr/bin/env bash
# Jipsa 데이터베이스와 계정을 Beetle 의 MySQL 안에 만든다. **DB 서버 파이에서** 실행한다.
#
#   JIPSA_DB_PASSWORD=<jipsa 계정 비밀번호> ./provision-db.sh [Beetle 디렉토리]
#
# - 루트 비밀번호는 Beetle 의 .env(MYSQL_ROOT_PASSWORD) 에서 읽는다. 따로 입력하지 않는다.
# - `jipsa` 계정은 `jipsa` 데이터베이스 하나 안에서만 권한을 갖는다. Beetle 의 데이터에는 닿지 않는다.
# - 여러 번 실행해도 안전하다. 이미 있으면 만들지 않는다. **이미 있는 계정의 비밀번호는 바꾸지 않는다**
#   (바꾸려면 SETUP.md 2-2 "비밀번호를 잘못 넣었다면").
#
# 환경 변수
#   JIPSA_DB_PASSWORD  필수. 영문·숫자와 - _ . ! # % 만, 12자 이상 (SETUP.md 2-0)
#   JIPSA_DB_NAME      기본 jipsa
#   JIPSA_DB_USER      기본 jipsa
#   MYSQL_CONTAINER    기본 beetle-mysql
set -euo pipefail

BEETLE_DIR="${1:-$HOME/Beetle}"
CONTAINER="${MYSQL_CONTAINER:-beetle-mysql}"
DB_NAME="${JIPSA_DB_NAME:-jipsa}"
DB_USER="${JIPSA_DB_USER:-jipsa}"
DB_PASSWORD="${JIPSA_DB_PASSWORD:?JIPSA_DB_PASSWORD 를 지정해야 합니다}"

fail() { echo "실패: $*" >&2; exit 1; }

# .env 에서 조용히 잘리는 문자를 막는다. 이름도 SQL 에 들어가므로 같은 이유로 제한한다.
[[ "$DB_PASSWORD" =~ ^[A-Za-z0-9._!#%-]{12,}$ ]] \
  || fail "비밀번호는 영문·숫자와 - _ . ! # % 만 써서 12자 이상이어야 합니다."
[[ "$DB_NAME" =~ ^[a-z][a-z0-9_]{0,31}$ && "$DB_USER" =~ ^[a-z][a-z0-9_]{0,31}$ ]] \
  || fail "DB 이름과 계정 이름은 영문 소문자·숫자·밑줄만 쓸 수 있습니다."

[ -f "$BEETLE_DIR/.env" ] || fail "$BEETLE_DIR/.env 가 없습니다. Beetle 디렉토리를 인자로 주세요."
ROOT_PASSWORD="$(set -a; . "$BEETLE_DIR/.env"; printf '%s' "${MYSQL_ROOT_PASSWORD:-}")"
BEETLE_DB_PASSWORD="$(set -a; . "$BEETLE_DIR/.env"; printf '%s' "${DB_PASSWORD:-}")"
[ -n "$ROOT_PASSWORD" ] || fail "$BEETLE_DIR/.env 에 MYSQL_ROOT_PASSWORD 가 없습니다."

# 같은 비밀번호를 쓰면 한쪽 계정이 새면 다른 쪽까지 열린다.
[ "$DB_PASSWORD" != "$ROOT_PASSWORD" ] || fail "jipsa 비밀번호가 루트 비밀번호와 같습니다."
[ "$DB_PASSWORD" != "$BEETLE_DB_PASSWORD" ] || fail "jipsa 비밀번호가 Beetle 계정 비밀번호와 같습니다."

docker inspect -f '{{.State.Health.Status}}' "$CONTAINER" 2>/dev/null | grep -q healthy \
  || fail "$CONTAINER 컨테이너가 healthy 상태가 아닙니다."

docker exec -i -e MYSQL_PWD="$ROOT_PASSWORD" "$CONTAINER" mysql -uroot <<SQL
CREATE DATABASE IF NOT EXISTS \`$DB_NAME\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS '$DB_USER'@'%' IDENTIFIED BY '$DB_PASSWORD';
GRANT ALL PRIVILEGES ON \`$DB_NAME\`.* TO '$DB_USER'@'%';
FLUSH PRIVILEGES;
SQL

# 새 계정으로 실제로 접속되는지, 권한이 그 DB 하나뿐인지 확인한다.
docker exec -e MYSQL_PWD="$DB_PASSWORD" "$CONTAINER" mysql -u"$DB_USER" -N -e "SHOW GRANTS FOR CURRENT_USER();" \
  || fail "$DB_USER 계정으로 접속하지 못했습니다. 계정이 이미 있었다면 비밀번호가 다를 수 있습니다."
echo "완료: $CONTAINER 에 데이터베이스 '$DB_NAME' 과 계정 '$DB_USER' 가 준비됐습니다."
