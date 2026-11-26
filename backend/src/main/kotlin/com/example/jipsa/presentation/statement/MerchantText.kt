package com.example.jipsa.presentation.statement

import java.text.Normalizer

/** 카드사 파일의 사용처 표기를 고른다. */
object MerchantText {
    private val MULTIPLE_SPACES = Regex("\\s+")

    /**
     * 전각 영문·숫자·괄호·공백을 반각으로 바꾸고(NFKC), 이어진 공백을 하나로 줄인다.
     * 그대로 두면 화면에서 어색하고, 같은 가게가 다른 사용처로 보여 중복 판단도 어긋난다.
     *
     * 삼성카드에는 적용하지 않는다. 이미 반각이고, 바꾸면 전에 가져온 사용처와 달라져 중복을 못 알아본다.
     */
    fun normalize(merchant: String): String =
        Normalizer.normalize(merchant, Normalizer.Form.NFKC).replace(MULTIPLE_SPACES, " ").trim()
}
