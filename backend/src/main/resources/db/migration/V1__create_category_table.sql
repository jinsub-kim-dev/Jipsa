-- 카테고리 (PRD 3.4)
-- 평면(flat) 분류. 화면이 같은 카테고리를 같은 색으로 그리도록 아이콘과 색을 함께 저장한다.

CREATE TABLE category
(
    id         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '카테고리 식별자',
    name       VARCHAR(20) NOT NULL COMMENT '이름',
    icon       VARCHAR(8)  NOT NULL COMMENT '아이콘(이모지)',
    color      VARCHAR(7)  NOT NULL COMMENT '색 (#RRGGBB)',
    created_at DATETIME(6) NOT NULL COMMENT '생성 시각',
    updated_at DATETIME(6) NOT NULL COMMENT '수정 시각',
    PRIMARY KEY (id),
    CONSTRAINT uk_category_name UNIQUE (name)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT '카테고리';
