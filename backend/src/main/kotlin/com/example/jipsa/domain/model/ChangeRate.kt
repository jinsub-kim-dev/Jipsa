package com.example.jipsa.domain.model

/**
 * 이전 값 대비 증감률. 감소면 음수다. 소수 첫째 자리까지 반올림한다 (PRD 4절).
 *
 * [Ratio] 는 0 이상만 다루지만 증감률은 음수가 될 수 있어 타입을 분리한다.
 */
@JvmInline
value class ChangeRate private constructor(val permille: Int) {

    /** 백분율(%). 예: +12.5 / -30.0 */
    val percentage: Double get() = permille / PERMILLE_PER_PERCENT

    val isIncrease: Boolean get() = permille > 0

    override fun toString(): String =
        if (permille > 0) "+%.1f%%".format(percentage) else "%.1f%%".format(percentage)

    companion object {
        /**
         * [previous] 대비 [current] 의 증감률.
         *
         * **[previous] 가 0원이면 `null` 이다.** 0에서 늘어난 변화의 비율은 정의할 수 없다.
         * 0% 로 내려주면 "변동 없음" 으로 정반대로 읽힌다. 화면은 "신규" 로 표기한다.
         */
        fun between(current: Money, previous: Money): ChangeRate? {
            if (previous.isZero) return null
            return ChangeRate(permilleOf(current.amount - previous.amount, previous.amount))
        }
    }
}
