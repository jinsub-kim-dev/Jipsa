# Jipsa 가계부

월별로 **어떤 종류의 소비를 하는지** 패턴을 분석하는 가계부. 이전 버전 [Beetle](../Beetle)과 같은
구조(Kotlin Spring Boot + React 모노레포)를 따른다.

- 지출마다 **카테고리** 하나와 **태그** 여러 개
- 지출 **종류**: 일반 지출 / 고정비(매월 자동 기록) / 할부(회차 자동 생성)
- 대시보드: 인사이트, 종류별 추이, 카테고리 × 월 히트맵, 태그 순위, 약정 지출 전망
- 캘린더: 날짜별 지출 강도와 고정비·할부 일정

도메인 규칙은 [`JIPSA_PRD.md`](JIPSA_PRD.md), 개발 규칙은 [`CLAUDE.md`](CLAUDE.md)가 정본이다.

## 구조

```text
Jipsa/
 ┣ backend/           Spring Boot 4 · Kotlin · JPA · Flyway · MySQL (클린 아키텍처 + DDD)
 ┣ frontend/          React 19 · Vite · TypeScript · Tailwind v4 · TanStack Query · Zustand · Recharts
 ┣ scripts/           generate-seed.mjs(dev 시드 생성) · provision-db.sh(배포 DB 준비)
 ┣ ui-prototypes/     화면 후보 HTML (main.html 이 현재 화면의 원본)
 ┣ docker-compose.yml           공통
 ┣ docker-compose.override.yml  로컬(dev) — 자동 적용
 ┣ docker-compose.prod.yml      배포(prod) — -f 로 명시
 ┗ docker-compose.app.yml       배포 앱 서버 — DB 는 다른 호스트(Beetle MySQL)
```

## 실행

로컬 포트는 Beetle 과 동시에 띄울 수 있도록 비켜 둔다.

| 서비스 | 주소 |
|---|---|
| 프론트엔드 | http://localhost:5174 |
| 백엔드 API · Swagger | http://localhost:8081 · http://localhost:8081/swagger-ui.html |
| MySQL | localhost:13307 (jipsa / jipsapassword) |

**전부 컨테이너로**

```bash
docker compose up -d --build
```

**개발할 때** — DB 만 컨테이너로 띄우고 백엔드·프론트엔드는 직접 실행한다.

```bash
docker compose up -d mysql
```

```bash
cd backend && ./gradlew bootRun
```

```bash
cd frontend && npm install && npm run dev
```

dev 프로필은 `db/seed` 의 데모 데이터(2026년 6~11월, 약 500건)를 함께 넣는다.
시드를 바꾸려면 `ui-prototypes/data.js` 를 고친 뒤 다시 생성한다.

```bash
node scripts/generate-seed.mjs
```

**배포(prod)** — Beetle 이 도는 라즈베리파이 두 대에 함께 올린다. 앱은 앱 서버의 **8082**,
DB 는 DB 서버의 Beetle MySQL 안 `jipsa` 데이터베이스다. 절차는 [`SETUP.md`](SETUP.md).

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml -f docker-compose.app.yml up -d --build
```

## 검증

```bash
cd backend && ./gradlew check
```

```bash
cd frontend && npm run check
```

백엔드 `check` 는 ArchUnit 아키텍처 규칙, 환경별 설정 정책, 도메인 분기 커버리지 90%, 실제 MySQL
(Testcontainers) 영속성 테스트를 포함한다. Docker 가 떠 있어야 한다.

## API 요약

| 메서드 · 경로 | 설명 |
|---|---|
| `GET /api/categories` | 카테고리 목록 |
| `GET /api/transactions?month=&kind=&categoryId=&tag=&keyword=` | 월별 거래 검색 |
| `POST /api/transactions` | 일반 지출 등록 |
| `PATCH /api/transactions/{id}` · `DELETE` | 수정(바뀐 필드만) · 삭제(일반 지출만) |
| `GET /api/fixed-expenses?month=` · `POST` | 고정비 목록(그 달 납부일 포함) · 등록 |
| `POST /api/fixed-expenses/{id}/terminate` | 고정비 종료 |
| `POST /api/fixed-expenses/records?month=` | 그 달 고정비 기록(멱등) |
| `GET /api/installment-plans?month=` · `POST` · `DELETE /{id}` | 할부 목록(진행 상황 포함) · 등록 · 해지 |
| `GET /api/statistics/monthly-summary?month=` | 월 요약과 전월 대비 |
| `GET /api/statistics/kind-trend?from=&to=` | 종류별 추이 |
| `GET /api/statistics/category-trend?from=&to=&targetMonth=` | 카테고리 × 월 |
| `GET /api/statistics/tags?month=&limit=` | 태그 순위 |
| `GET /api/statistics/insight?month=` | 인사이트 |
| `GET /api/statistics/commitment-forecast?month=&months=` | 약정 지출 전망 |
