package com.example.jipsa.domain.model

/**
 * 전체에서 부분이 차지하는 비율(점유율). 0% 이상이다.
 *
 * 지출 종류별 비중, 약정 지출 비중에 쓴다. 소수 첫째 자리까지 반올림한다.
 */
@JvmInline
value class Ratio private constructor(val permille: Int) {

    /** 백분율(%). 예: 52.3 */
    val percentage: Double get() = permille / PERMILLE_PER_PERCENT

    override fun toString(): String = "%.1f%%".format(percentage)

    companion object {
        /**
         * [whole] 에서 [part] 가 차지하는 비율.
         *
         * **[whole] 이 0원이면 `null` 이다.** 아무것도 쓰지 않은 달의 비중은 0% 가 아니라
         * 정의되지 않는다. 0% 로 내려주면 "고정비가 하나도 없다" 로 읽힌다.
         */
        fun of(part: Money, whole: Money): Ratio? {
            if (whole.isZero) return null
            return Ratio(permilleOf(part.amount, whole.amount))
        }
    }
}
