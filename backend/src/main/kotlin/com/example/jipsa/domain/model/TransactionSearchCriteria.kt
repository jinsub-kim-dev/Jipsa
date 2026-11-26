package com.example.jipsa.domain.model

import java.time.LocalDate

/**
 * 거래 검색 조건. 기간은 필수이고 나머지는 선택이다.
 *
 * [keyword] 는 사용처·메모·태그 이름에 부분 일치한다.
 */
data class TransactionSearchCriteria(
    val from: LocalDate,
    val to: LocalDate,
    val kind: ExpenseKind? = null,
    val categoryId: CategoryId? = null,
    val tag: Tag? = null,
    val keyword: String? = null,
)
