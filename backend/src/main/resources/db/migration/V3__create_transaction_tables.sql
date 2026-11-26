-- 거래 (PRD 3.1)
-- 모든 분석은 소비일(spent_date) 기준이다.
-- 지출 종류(kind)와 출처(fixed_expense_id / installment_plan_id)의 일치는 도메인이 보장한다.

CREATE TABLE transaction
(
    id                   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '거래 식별자',
    kind                 VARCHAR(20)  NOT NULL COMMENT '지출 종류: GENERAL / FIXED / INSTALLMENT',
    category_id          BIGINT       NOT NULL COMMENT '카테고리 식별자',
    amount               BIGINT       NOT NULL COMMENT '금액(원)',
    merchant             VARCHAR(100) NOT NULL COMMENT '사용처',
    spent_date           DATE         NOT NULL COMMENT '소비일',
    memo                 VARCHAR(200) NULL COMMENT '메모',
    payment_method       VARCHAR(30)  NULL COMMENT '결제 수단(표시용)',
    fixed_expense_id     BIGINT       NULL COMMENT '고정비 식별자. FIXED 일 때만',
    installment_plan_id  BIGINT       NULL COMMENT '할부 계획 식별자. INSTALLMENT 일 때만',
    installment_sequence INT          NULL COMMENT '할부 회차(1부터). INSTALLMENT 일 때만',
    created_at           DATETIME(6)  NOT NULL COMMENT '생성 시각',
    updated_at           DATETIME(6)  NOT NULL COMMENT '수정 시각',
    PRIMARY KEY (id),
    CONSTRAINT fk_transaction_category FOREIGN KEY (category_id) REFERENCES category (id),
    CONSTRAINT fk_transaction_fixed_expense FOREIGN KEY (fixed_expense_id) REFERENCES fixed_expense (id),
    CONSTRAINT fk_transaction_installment_plan FOREIGN KEY (installment_plan_id) REFERENCES installment_plan (id),
    -- 같은 할부의 같은 회차가 두 번 생기면 회차 합계가 총액을 넘는다
    CONSTRAINT uk_transaction_installment_sequence UNIQUE (installment_plan_id, installment_sequence)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT '거래';

-- 월별 조회·집계
CREATE INDEX idx_transaction_spent_date ON transaction (spent_date);
CREATE INDEX idx_transaction_kind_spent_date ON transaction (kind, spent_date);
CREATE INDEX idx_transaction_category_spent_date ON transaction (category_id, spent_date);
-- 고정비 기록의 멱등성 판단
CREATE INDEX idx_transaction_fixed_expense_spent_date ON transaction (fixed_expense_id, spent_date);

CREATE TABLE transaction_tag
(
    transaction_id BIGINT      NOT NULL COMMENT '거래 식별자',
    position       INT         NOT NULL COMMENT '태그 순서',
    name           VARCHAR(20) NOT NULL COMMENT '태그 이름(# 제외)',
    PRIMARY KEY (transaction_id, position),
    CONSTRAINT fk_transaction_tag FOREIGN KEY (transaction_id) REFERENCES transaction (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT '거래 태그';

-- 태그 순위 집계와 태그 필터
CREATE INDEX idx_transaction_tag_name ON transaction_tag (name);
