-- 고정비 정의 (PRD 3.2)
-- 연월은 그 달 1일의 DATE 로 저장한다.

CREATE TABLE fixed_expense
(
    id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '고정비 식별자',
    category_id    BIGINT       NOT NULL COMMENT '카테고리 식별자',
    merchant       VARCHAR(100) NOT NULL COMMENT '사용처',
    amount         BIGINT       NOT NULL COMMENT '금액(원)',
    day_of_month   INT          NOT NULL COMMENT '납부일(1~31). 없는 날은 말일로 보정',
    payment_method VARCHAR(30)  NULL COMMENT '결제 수단(표시용)',
    start_month    DATE         NOT NULL COMMENT '시작월(1일)',
    end_month      DATE         NULL COMMENT '종료월(1일). NULL 이면 계속',
    created_at     DATETIME(6)  NOT NULL COMMENT '생성 시각',
    updated_at     DATETIME(6)  NOT NULL COMMENT '수정 시각',
    PRIMARY KEY (id),
    CONSTRAINT fk_fixed_expense_category FOREIGN KEY (category_id) REFERENCES category (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT '고정비';

CREATE TABLE fixed_expense_tag
(
    fixed_expense_id BIGINT      NOT NULL COMMENT '고정비 식별자',
    position         INT         NOT NULL COMMENT '태그 순서',
    name             VARCHAR(20) NOT NULL COMMENT '태그 이름(# 제외)',
    PRIMARY KEY (fixed_expense_id, position),
    CONSTRAINT fk_fixed_expense_tag FOREIGN KEY (fixed_expense_id) REFERENCES fixed_expense (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT '고정비 태그';

-- 할부 계획 (PRD 3.3)

CREATE TABLE installment_plan
(
    id                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '할부 계획 식별자',
    category_id         BIGINT       NOT NULL COMMENT '카테고리 식별자',
    merchant            VARCHAR(100) NOT NULL COMMENT '사용처',
    total_amount        BIGINT       NOT NULL COMMENT '총액(원)',
    installment_months  INT          NOT NULL COMMENT '할부 개월 수(2~60)',
    first_billing_month DATE         NOT NULL COMMENT '첫 납부월(1일)',
    billing_day         INT          NOT NULL COMMENT '납부일(1~31)',
    payment_method      VARCHAR(30)  NULL COMMENT '결제 수단(표시용)',
    created_at          DATETIME(6)  NOT NULL COMMENT '생성 시각',
    updated_at          DATETIME(6)  NOT NULL COMMENT '수정 시각',
    PRIMARY KEY (id),
    CONSTRAINT fk_installment_plan_category FOREIGN KEY (category_id) REFERENCES category (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT '할부 계획';

CREATE TABLE installment_plan_tag
(
    installment_plan_id BIGINT      NOT NULL COMMENT '할부 계획 식별자',
    position            INT         NOT NULL COMMENT '태그 순서',
    name                VARCHAR(20) NOT NULL COMMENT '태그 이름(# 제외)',
    PRIMARY KEY (installment_plan_id, position),
    CONSTRAINT fk_installment_plan_tag FOREIGN KEY (installment_plan_id) REFERENCES installment_plan (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT '할부 계획 태그';
