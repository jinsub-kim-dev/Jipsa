package com.example.jipsa.domain.model

import kotlin.math.abs
import kotlin.math.sign

/**
 * 비율을 **천분율(0.1% 단위) 정수**로 계산한다.
 *
 * 부동소수점으로 나누면 50.05% 같은 값의 반올림이 실행 환경마다 어긋날 수 있다.
 * 정수 나눗셈의 나머지로 반올림을 판정해 결과를 고정한다. 반올림은 0에서 멀어지는
 * 방향(HALF_UP)이다. 예: 1/3 = 33.3%, 2/3 = 66.7%, -1/8 = -12.5%
 *
 * @param denominator 0보다 커야 한다. 호출부([Ratio.of], [ChangeRate.between])가 보장한다.
 */
internal fun permilleOf(numerator: Long, denominator: Long): Int {
    val scaled = numerator * PERMILLE_PER_UNIT
    val quotient = scaled / denominator
    val remainder = scaled % denominator
    val roundsAway = abs(remainder) * 2 >= denominator
    val rounded = if (roundsAway) quotient + scaled.sign else quotient
    return rounded.toInt()
}

private const val PERMILLE_PER_UNIT = 1_000L

internal const val PERMILLE_PER_PERCENT = 10.0
