# Jipsa Backend 프로젝트 가이드라인

## 1. 프로젝트 개요
- **기술 스택:** Kotlin, Spring Boot (4.x), Spring Data JPA, MySQL, Gradle
- **아키텍처:** 클린 아키텍처 + 도메인 주도 설계 (엄격한 의존성 역전 원칙(DIP) 적용)
- **도메인 요구사항의 정본:** `../JIPSA_PRD.md`

---

## 2. 패키지 구조 (`com.example.jipsa`)
```text
com.example.jipsa
 ┣ domain/                 # 순수 비즈니스 로직 및 도메인 모델 (Spring/JPA에 독립적)
 │   ┣ model/              # 애그리거트 루트, 엔티티, 값 객체(VO)
 │   ┣ service/            # 도메인 서비스 (여러 애그리거트에 걸친 순수 규칙)
 │   ┣ exception/          # 도메인 예외
 │   ┣ repository/         # 리포지토리 인터페이스 = 아웃바운드 포트 (DIP)
 │   ┗ query/              # 통계 집계 전용 조회 포트 (Read Model)
 │
 ┣ application/            # 유스케이스 및 애플리케이션 서비스
 │   ┣ port/               # 인바운드 포트(유스케이스 인터페이스) 전용
 │   ┗ service/            # 비즈니스 오케스트레이션 (트랜잭션 경계)
 │
 ┣ infrastructure/         # 외부 시스템 연동 (DB, 외부 API 등)
 │   ┗ persistence/        # JPA 엔티티, Spring Data 리포지토리, 매퍼, 어댑터
 │
 ┗ presentation/           # 외부 클라이언트 인터페이스
     ┗ controller/         # REST 컨트롤러 및 DTO
```

> **아웃바운드 포트의 위치는 `domain/repository` 하나로 확정한다.** `application/port`에 아웃바운드
> 포트를 중복 정의하지 않는다. `application/port`는 인바운드(유스케이스 인터페이스) 전용이다.

---

## 3. 핵심 아키텍처 규칙 (엄격 준수)
1. **의존성 역전 (DIP):**
   - `domain` 레이어는 절대 `infrastructure`나 `presentation` 레이어를 의존해서는 안 됩니다.
   - `domain`은 `org.springframework`, `jakarta.persistence`, `tools.jackson` 등 **프레임워크 타입을
     import 하지 않습니다.** 도메인 서비스도 Spring 빈이 아닌 순수 클래스입니다.
   - 리포지토리는 `domain/repository`에 인터페이스로 정의하고, `infrastructure/persistence`에서 구현합니다.
2. **모델 분리:**
   - JPA 엔티티를 애플리케이션이나 프레젠테이션 레이어에 직접 노출하지 않습니다.
   - 순수 Kotlin **도메인 모델**과 **JPA 엔티티**를 철저히 분리하고, 매퍼(Mapper)를 통해 변환합니다.
   - 컨트롤러는 도메인 모델도 직접 반환하지 않고 **응답 DTO**로 변환합니다.
3. **코틀린 관점 (Idioms):**
   - 가변성보다 불변성(`val` 우선 사용)을 지향합니다.
   - 코틀린의 타입 시스템을 활용해 널 안전성(Null-safety)을 확보하고 불필요한 `!!` 사용을 지양합니다.
   - `data class` 사용 범위는 4.2절의 규칙을 따릅니다. (무조건 권장이 아님)
4. **규칙의 강제:** 위 1~2번 규칙은 리뷰가 아니라 **ArchUnit 적합성 테스트로 강제**합니다.
   규칙을 어긴 코드는 `./gradlew test`에서 실패해야 합니다.

---

## 4. DDD 전술 설계 규칙

### 4.1 애그리거트 경계
- 애그리거트 루트는 `Category`, `Transaction`, `FixedExpense`, `InstallmentPlan`
  **4개이며 각각 독립 애그리거트**입니다. 태그(`Tag`)는 애그리거트가 아니라 거래·고정비·할부가 갖는 값 객체입니다.
- **애그리거트 간 참조는 반드시 식별자(ID)로만 합니다.** 도메인 모델에 객체 참조나 JPA 연관관계를
  두지 않습니다.
  - `Transaction`은 `category: Category`가 아니라 `categoryId: CategoryId`를 가집니다.
  - 이유: 로딩 범위와 트랜잭션 경계를 애그리거트 하나로 한정합니다. 36개월 할부처럼 연관 레코드가
    많은 경우 객체 그래프가 폭발합니다.
- 하나의 유스케이스에서는 **하나의 애그리거트만 변경**하는 것을 기본으로 합니다. 둘 이상을 변경해야
  하면 그 이유를 코드 주석에 남깁니다.
- 리포지토리는 **애그리거트 루트 단위로만** 정의합니다. 테이블 단위로 만들지 않습니다.
- 통계/집계 조회는 애그리거트 재구성이 불필요하므로, 리포지토리가 아닌 **전용 조회 포트(Read Model)**
  로 분리합니다. 포트는 `domain/query` 에 두고 구현은 `infrastructure/persistence/query` 에 둡니다.
  집계는 DB 에서 수행합니다. 애그리거트를 모두 읽어 메모리에서 합산하면 데이터가 늘어날수록
  감당할 수 없습니다.

### 4.2 엔티티와 값 객체의 구분 (`data class` 사용 규칙)
| 분류 | 클래스 형태 | 동일성 판단 기준 |
|---|---|---|
| 값 객체(VO) — `Money` 등 다중 필드 | `data class` (불변) | 전체 필드 값 |
| 값 객체(VO) — `CategoryId`, `DayOfMonthValue` 등 단일 값 | `@JvmInline value class` | 래핑한 값 |
| 엔티티 / 애그리거트 루트 | 일반 `class` | **식별자(ID)** — `equals`/`hashCode` 직접 구현 |
| 요청·응답 DTO (`presentation`) | `data class` | — |
| JPA 엔티티 (`infrastructure`) | 일반 `class` | 식별자(ID) |

**애그리거트 루트에 `data class`를 쓰지 않는 이유:**
1. `equals`가 전체 필드 기반이라, 금액만 수정된 동일 ID 객체가 서로 다른 객체로 취급됩니다.
2. `copy()`가 모든 필드의 외부 임의 교체를 허용하여 불변식 검증을 우회합니다.
   (예: 할부 회차를 `tx.copy(amount = ...)` 로 바꿔 회차 합계 == 총액을 깨뜨림)

애그리거트 루트는 `domain/model/AggregateRoot.kt` 의 마커 인터페이스를 구현합니다. 이 인터페이스를
구현하면 `DomainModelConventionTest` 가 위 규약(`data class` 금지, `copy()` 미노출, `equals`/`hashCode`
직접 구현)을 자동으로 검사하므로, 애그리거트를 추가할 때 테스트를 수정할 필요가 없습니다.

### 4.3 불변식(Invariant)은 도메인이 보호한다
- 모든 불변식은 **생성 시점(`init` 블록 또는 `companion object` 팩토리 메서드)에서 검증**하여,
  유효하지 않은 상태의 객체가 애초에 존재할 수 없게 만듭니다.
- 생성 규칙이 복잡하면 주 생성자를 `private`으로 막고 팩토리 메서드만 노출합니다.
- 상태 변경은 setter가 아니라 **의도를 드러내는 메서드**로만 합니다.
  (`tx.amount = x` 금지 → `tx.correctAmount(x)`, `tx.settle()`)
- 검증 실패는 `IllegalArgumentException`이 아니라 `domain/exception`의 **도메인 예외**로 던집니다.
  코틀린 표준 `require` 대신 `checkInvariant { }` 헬퍼를 사용해 예외 계층을 유지합니다.
- **도달할 수 없는 방어 코드를 두지 않습니다.** 다른 불변식이나 `private` 생성자가 이미 보장하는
  조건을 다시 검사하면, 그 분기는 테스트로 커버할 수 없어 커버리지 게이트에서 실패합니다.
  그런 경우 검사를 제거하고 "무엇이 이 조건을 보장하는지" 를 주석으로 남깁니다.

**도메인에서 반드시 보장해야 할 불변식:** (근거는 PRD 3절)
- `Transaction`: `amount > 0`. 지출 종류와 출처가 일치한다 — `GENERAL` 은 출처 없음, `FIXED` 는
  `fixedExpenseId` 만, `INSTALLMENT` 는 `installmentPlanId` + `installmentSequence(>=1)` 만
- `Transaction` (할부 회차): **금액과 소비일은 변경할 수 없다.** 카테고리·태그·메모는 허용한다.
  일반 지출만 개별 삭제할 수 있다(`isDeletable`)
- `Tags`: 최대 10개, 중복은 합친다. `Tag` 는 공백이 아니고 20자 이하이며 앞의 `#` 을 뗀 값이다
- `FixedExpense`: `amount > 0`, 납부일 1~31, 종료월 >= 시작월. 없는 납부일은 말일로 보정한다
- `InstallmentPlan`: 개월 수 2~60, 총액 >= 개월 수, 회차 금액 합계 == 총액(나머지는 1회차)
- `Category`: 이름 20자 이하·유일(유일성은 애플리케이션 서비스가 검증), 색은 `#RRGGBB`
- `MonthRange`: 종료월 >= 시작월, 최대 36개월

### 4.4 비즈니스 로직의 위치 (애너믹 도메인 모델 금지)
- **규칙과 계산은 `domain`에 둡니다.** `application/service`는 오케스트레이션(리포지토리 호출,
  트랜잭션 경계, DTO 변환)만 담당합니다.
- 애플리케이션 서비스에 비즈니스 규칙 분기(`if`)가 등장하면 도메인으로 이동시킵니다.
- 특정 애그리거트 하나에 속하지 않는 규칙은 `domain/service`의 **도메인 서비스**(순수 클래스)로 둡니다.
  - `FixedExpenseRecorder`: 고정비 목록 + 그 달의 기록 현황 → 새로 기록할 거래 (멱등)
  - `CommitmentForecaster`: 고정비 + 할부 → 앞으로 n개월의 약정 지출
  - `CategoryTrendAnalyzer`: 카테고리별 월 합계 → 빈 칸 채움, 전월 대비 증감률, 정렬 확정
  - `SpendingInsightAnalyzer`: 월별 합계 → 직전 3개월 평균 대비, 가장 늘어난 카테고리, 약정 비중
  - `CardStatementImporter`: 카드 이용내역 + 기존 거래·할부 → 새로 만들 거래·할부 계획 (내용 비교로 중복 제외)
  - `MerchantCategoryClassifier`: 사용처 → 분류 규칙의 카테고리 이름 (규칙 순서대로, 없으면 미분류)
- 카드 이용내역 **파일 형식**(엑셀 열 이름)은 도메인이 모른다. `presentation/statement` 의 읽기 어댑터가
  해석해 `CardStatement` 로 넘긴다. 인프라는 다른 레이어가 참조할 수 없으므로(ArchUnit) 입력 어댑터는 프레젠테이션에 둔다.
  - `StatementDocument`: 엑셀(xls·xlsx)과 HTML 표를 같은 "표" 로 연다. 확장자가 아니라 **내용**(파일 서명·`<table>`)으로 판단한다
  - `TabularCardStatementReader`: 머리글 찾기, 줄 단위 실패 수집. 카드사별 리더(삼성·우리·현대)는 열 이름과 한 줄 해석만 갖는다
  - `CardStatementFileReader`: 머리글을 보고 카드사를 고른다. 카드사를 추가하면 리더 빈 하나만 늘리면 된다
- 할부 회차 생성(`InstallmentPlan.scheduleParts`)과 고정비 한 달 분(`FixedExpense.occurrenceIn`)은
  각 애그리거트의 팩토리 메서드가 담당합니다. 둘 다 한 애그리거트의 정보만으로 결정되기 때문입니다.
- **판정 기준값(임계치)은 도메인 서비스가 갖고, 응답에 함께 담아 화면이 설명할 수 있게 합니다.**
  기준이 화면과 서버 두 곳에 있으면 반드시 어긋납니다.
- 도메인 모델이 getter/setter만 가진 상태가 되면 설계가 잘못된 신호입니다.

### 4.5 유비쿼터스 언어
- `../JIPSA_PRD.md`의 용어를 코드 식별자에 그대로 사용합니다.
  `spentDate`(소비일), `merchant`(사용처), `ExpenseKind`(GENERAL/FIXED/INSTALLMENT), `Tag`,
  `FixedExpense`, `dayOfMonth`, `InstallmentPlan`, `installmentMonths`, `monthlyAmount`, `commitment`(약정 지출).
- 동의어를 임의로 만들지 않습니다. (`usedDate`, `store`, `type` 대신 `kind` 등)
- 이전 버전(Beetle)의 `billDate`·`isSettled`·`Budget`·`PaymentMethod` 애그리거트는 이 버전에 없습니다.
  결제 수단은 표시용 문자열(`paymentMethod`)입니다.

---

## 5. 테스트 정책 (필수)

### 5.1 원칙
- **비즈니스 로직에는 반드시 테스트가 동반됩니다.** 도메인 규칙이나 계산 코드를 테스트 없이
  추가하거나 변경하지 않습니다.
- 도메인 규칙은 **테스트를 먼저 또는 함께** 작성합니다. "나중에 추가"는 허용하지 않습니다.
- 작업 완료를 보고하기 전에 `./gradlew test`가 통과해야 합니다. 실패하면 실패 사실과 출력을
  그대로 보고합니다.

### 5.2 계층별 테스트 전략
| 대상 | 테스트 종류 | 도구 | 요구 수준 |
|---|---|---|---|
| `domain/model`, `domain/service` | 순수 단위 테스트 (Spring 미사용) | JUnit5 + AssertJ | **불변식·계산 규칙 전수 검증. 분기 커버리지 90% 이상** |
| `application/service` | 단위 테스트 (리포지토리는 mock/fake) | MockK | 유스케이스 정상 경로 + 예외 경로 |
| `infrastructure/persistence` | 통합 테스트 | Testcontainers(MySQL) + `@DataJpaTest` | 매퍼 왕복(round-trip) 동등성, 커스텀 쿼리 |
| `presentation/controller` | 슬라이스 테스트 | `@WebMvcTest` | 요청 검증, 에러 응답 포맷, 직렬화 |
| 아키텍처 규칙 (3절) | 적합성 테스트 | ArchUnit | 규칙 전부 |

- **도메인 단위 테스트에는 Spring 컨텍스트를 띄우지 않습니다.** 도메인 테스트에 `@SpringBootTest`가
  필요해졌다면 도메인이 프레임워크에 오염된 신호입니다.
- 통합 테스트 DB는 H2가 아니라 **Testcontainers의 실제 MySQL**을 사용합니다. (방언 차이로 인한
  거짓 통과/거짓 실패 방지)
- `@SpringBootTest`는 전체 컨텍스트 확인 용도로 최소 개수만 유지합니다.

### 5.3 반드시 검증해야 할 경계 조건
정상 케이스만으로는 불충분합니다. 아래는 누락 시 미완료로 간주합니다.
- **말일 보정:** 납부일 31일의 2월(평년 28일/윤년 29일)·4월(30일), 윤년 29일 납부의 평년 2월
- **할부 분할:** 나누어떨어지지 않는 금액(1,000,000원 / 3개월)에서 **회차 합계 == 총액**,
  `installmentMonths` 가 0·1·61 인 입력의 거부, 해를 넘는 회차 일정
- **고정비 기록:** 이미 기록된 고정비를 다시 만들지 않는지(멱등), 기간 밖의 달
- **증감률·비율:** 이전 값/전체가 0원이면 `null`, 반올림 경계(50.05% 등)
- **통계 집계:** 거래가 없는 달을 0원으로 채우는지, 태그 중복 집계, 실제 MySQL 로 검증
- **금액:** 0원 및 음수 거부, 원 단위 정수 처리(부동소수점 사용 금지)

### 5.4 테스트 작성 규칙
- 테스트 이름은 백틱을 사용한 한국어 서술형으로 작성합니다.
  예: ``fun `신용카드 결제일이 31일이면 2월 청구일은 말일로 보정된다`()``
- `// given` / `// when` / `// then` 주석으로 구조를 구분합니다.
- 경계값이 여러 개인 계산 로직은 `@ParameterizedTest`로 표 형태로 검증합니다.
- 테스트 픽스처는 `src/test/kotlin/com/example/jipsa/fixture`에 팩토리 함수로 모아 재사용합니다.
- 테스트 하나는 하나의 규칙만 검증합니다. 단정(assertion)을 여러 규칙에 걸쳐 뭉치지 않습니다.
- **MockK 의 `any()` 를 값 객체(`@JvmInline value class`) 파라미터에 사용하지 않습니다.** MockK 는
  매처 서명을 만들 때 값 객체를 난수로 생성하는데, 이 값이 불변식(예: ID 는 양수)을 위반하면
  테스트가 간헐적으로 실패합니다. 구체적인 값을 넘기거나 `confirmVerified` 로 검증합니다.

---

## 6. 핵심 도메인 개념
- **Transaction (거래):** 지출 한 건. 지출 종류(`ExpenseKind`), 카테고리 하나, 태그 여러 개. 소비일 기준으로만 집계한다.
- **Category (카테고리):** 평면 분류. 화면이 같은 색으로 그리도록 아이콘·색을 갖는다.
- **FixedExpense (고정비):** 매월 같은 날 나가는 지출의 정의. 기동 시·매월 1일·등록 직후 그 달 분을 기록한다
  (`presentation/scheduler/FixedExpenseRecordingScheduler`).
- **InstallmentPlan (할부 계획):** 총액을 회차로 나눈 정의. 등록 시 모든 회차의 거래를 만든다. 해지는 계획 단위다.

---

## 7. 환경별 설정 (프로필)

설정 파일은 세 개이며 역할이 다릅니다.

| 파일 | 역할 |
|---|---|
| `application.yml` | 프로필과 무관한 공통 설정. `spring.profiles.default: dev` |
| `application-dev.yml` | 로컬. 환경 변수 없이도 뜨도록 기본값을 둔다 |
| `application-prod.yml` | 배포. 접속 정보에 **기본값을 두지 않는다** |

- **접속 URL 은 `application.yml` 에만 둡니다.** 프로필마다 URL 을 따로 쓰면 타임존·인코딩
  옵션이 환경별로 어긋납니다. 대신 `jipsa.db.*` 로 값만 프로필이 정합니다. 기본값을 둘지
  말지가 프로필의 정책입니다.
- **배포 프로필은 환경 변수가 없으면 기동에 실패합니다.** 이것이 요구사항입니다. 기본값이
  있으면 설정을 빠뜨린 채로 떠서 엉뚱한 DB 에 붙거나, 인증이 실패하는 시점까지 문제가
  드러나지 않습니다.
- 시드 데이터(`db/seed`)는 **dev 에만** 적용합니다. 공통 설정은 스키마(`db/migration`)만
  적용하므로, 프로필을 추가할 때 시드가 딸려 들어가지 않습니다.
- 환경별 정책(시드 적용, 기본값 금지, 문서·관리 엔드포인트 차단 등)은 `ProfileConfigurationTest`
  가 설정 파일을 직접 읽어 검증합니다. 설정을 고치다 규칙을 깨면 `./gradlew check` 가 실패합니다.
- **테스트는 프로필을 명시합니다.** `@ActiveProfiles("dev")` 를 붙이지 않으면 쉘의
  `SPRING_PROFILES_ACTIVE` 에 따라 테스트 설정이 바뀝니다.

---

## 8. 빌드 및 테스트 명령어 (`backend` 디렉토리 기준)
- **빌드:** `./gradlew build`
- **테스트:** `./gradlew test`
- **아키텍처 규칙만 검증:** `./gradlew test --tests '*ArchitectureTest'`
- **로컬 실행:** `./gradlew bootRun` (프로필 미지정 = dev)
- **배포 프로필로 실행:** `SPRING_PROFILES_ACTIVE=prod DB_HOST=... DB_NAME=... DB_USER=... DB_PASSWORD=... ./gradlew bootRun`

### 8.1 테스트 의존성
5절 정책을 실행하는 의존성입니다. 모두 `build.gradle.kts` 에 반영되어 있습니다.
- `com.tngtech.archunit:archunit-junit5` — 3절 규칙 강제
- `io.mockk:mockk` — 애플리케이션 서비스 단위 테스트
- `org.assertj:assertj-core` — 단정문
- `org.springframework.boot:spring-boot-testcontainers` +
  `org.testcontainers:testcontainers-mysql` + `org.testcontainers:testcontainers-junit-jupiter`
  — 영속성 통합 테스트. **Testcontainers 2.x 는 모듈 좌표가 `testcontainers-*` 로 바뀌었고**
  MySQL 컨테이너 클래스도 `org.testcontainers.mysql.MySQLContainer` 로 이동했습니다
