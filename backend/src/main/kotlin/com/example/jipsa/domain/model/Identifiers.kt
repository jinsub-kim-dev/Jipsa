package com.example.jipsa.domain.model

import com.example.jipsa.domain.exception.checkInvariant

/*
 * 애그리거트 식별자. 애그리거트 간 참조는 식별자로만 한다 (backend/CLAUDE.md 4.1).
 * 단일 값 값 객체이므로 `@JvmInline value class` 로 둔다 (4.2).
 */

@JvmInline
value class CategoryId(val value: Long) {
    init {
        checkInvariant(value > 0) { "카테고리 ID 는 양수여야 합니다. 입력값: $value" }
    }

    override fun toString(): String = value.toString()
}

@JvmInline
value class TransactionId(val value: Long) {
    init {
        checkInvariant(value > 0) { "거래 ID 는 양수여야 합니다. 입력값: $value" }
    }

    override fun toString(): String = value.toString()
}

@JvmInline
value class FixedExpenseId(val value: Long) {
    init {
        checkInvariant(value > 0) { "고정비 ID 는 양수여야 합니다. 입력값: $value" }
    }

    override fun toString(): String = value.toString()
}

@JvmInline
value class InstallmentPlanId(val value: Long) {
    init {
        checkInvariant(value > 0) { "할부 계획 ID 는 양수여야 합니다. 입력값: $value" }
    }

    override fun toString(): String = value.toString()
}
