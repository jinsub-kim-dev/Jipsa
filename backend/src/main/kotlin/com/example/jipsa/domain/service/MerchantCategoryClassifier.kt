package com.example.jipsa.domain.service

import com.example.jipsa.domain.exception.checkInvariant
import java.text.Normalizer

/**
 * 분류 규칙 (PRD 3.5). 사용처에 [keywords] 중 하나가 들어 있으면 [categoryName] 카테고리다.
 *
 * 카테고리를 식별자가 아니라 이름으로 가리킨다. 기본 카테고리(V4 마이그레이션)의 식별자는
 * 환경마다 같다는 보장이 없고, 사용자가 이름을 바꾸면 규칙이 조용히 다른 카테고리를 가리키는 것보다
 * 미분류로 떨어지는 편이 알아차리기 쉽다.
 */
data class CategoryRule(
    val categoryName: String,
    val keywords: List<String>,
) {
    init {
        checkInvariant(categoryName.isNotBlank()) { "분류 규칙의 카테고리 이름은 비어 있을 수 없습니다." }
        checkInvariant(keywords.isNotEmpty()) { "분류 규칙에는 키워드가 하나 이상 있어야 합니다. 카테고리: $categoryName" }
        checkInvariant(keywords.none(String::isBlank)) { "분류 규칙의 키워드는 비어 있을 수 없습니다. 카테고리: $categoryName" }
    }

    private val normalizedKeywords: List<String> = keywords.map(::normalize)

    fun matches(merchant: String): Boolean {
        val target = normalize(merchant)
        return normalizedKeywords.any(target::contains)
    }

    private companion object {
        /**
         * 공백·대소문자·전각을 무시한다. 카드사마다 "CU OO점" / "씨유(CU)OO점" 처럼 띄어쓰기가 다르고,
         * "ＡＫ　ＰＬＡＺＡ" 처럼 전각 문자를 쓰는 곳도 있다.
         */
        fun normalize(text: String): String =
            Normalizer.normalize(text, Normalizer.Form.NFKC).filterNot(Char::isWhitespace).lowercase()
    }
}

/**
 * 사용처로 카테고리를 정하는 도메인 서비스 (PRD 3.5).
 *
 * 규칙은 **순서대로** 보고 처음 맞는 것을 쓴다. 그래서 구체적인 규칙을 앞에 둔다.
 * 예: "카카오T택시" 는 교통이지 다른 "카카오" 서비스가 아니고, "아이스크림할인점" 은 쇼핑이 아니라 카페·간식이다.
 */
class MerchantCategoryClassifier(private val rules: List<CategoryRule> = DEFAULT_RULES) {

    /** [merchant] 에 맞는 규칙의 카테고리 이름. 맞는 규칙이 없으면 `null` 이다. */
    fun categoryNameOf(merchant: String): String? = rules.firstOrNull { it.matches(merchant) }?.categoryName

    companion object {
        /** 규칙에 맞지 않는 구매가 들어가는 카테고리 (PRD 3.5). 없으면 가져오기가 만든다. */
        const val UNCATEGORIZED_NAME: String = "미분류"
        const val UNCATEGORIZED_ICON: String = "❔"
        const val UNCATEGORIZED_COLOR: String = "#9CA3AF"

        /** 기본 카테고리(V4 마이그레이션) 이름을 가리키는 기본 규칙. 위에서부터 본다. */
        val DEFAULT_RULES: List<CategoryRule> = listOf(
            // "가스"·"수도"·"마트" 같은 짧은 키워드는 넣지 않는다. "돈가스", "스마트스토어" 까지 잡는다.
            CategoryRule("주거·통신", listOf("관리비", "휴대폰", "알림서비스", "이용알림", "통신", "인터넷", "브로드밴드", "도시가스", "전기요금")),
            CategoryRule("보험·건강", listOf("약국", "의원", "병원", "치과", "한의원", "의료", "보험", "안경")),
            CategoryRule(
                "교통",
                listOf(
                    "택시", "카카오T", "주유소", "칼텍스", "에쓰오일", "S-OIL", "충전소", "주차", "하이패스", "도로공사",
                    "코레일", "철도", "버스", "지하철", "티머니",
                ),
            ),
            CategoryRule(
                "카페·간식",
                listOf("커피", "카페", "스타벅스", "메가엠지씨", "이디야", "투썸", "빽다방", "컴포즈", "아이스크림", "베이커리", "파리바게뜨", "뚜레쥬르", "도넛", "제과"),
            ),
            CategoryRule(
                "식비",
                listOf(
                    // 편의점·마트·식료품
                    "세븐일레븐", "코리아세븐", "씨유", "CU(", "GS25", "지에스", "이마트24", "미니스톱",
                    "트레이더스", "이마트", "홈플러스", "롯데마트", "코스트코", "프레시", "식자재",
                    // 음식점
                    "김밥", "돈가스", "맘스터치", "맥도날드", "버거", "롯데리아", "본죽", "비빔밥", "감자탕", "찌개",
                    "국밥", "치킨", "피자", "식당", "푸드", "에프앤비", "휴게소", "배달의민족", "요기요", "쿠팡이츠",
                ),
            ),
            CategoryRule("문화·여가", listOf("CGV", "메가박스", "롯데시네마", "놀이", "티켓", "서점", "교보문고", "yes24")),
            // 구독을 쇼핑보다 앞에 둔다. "쿠팡와우" 가 쇼핑의 "쿠팡" 에 먼저 걸리지 않게 한다.
            CategoryRule("구독", listOf("넷플릭스", "유튜브", "멜론", "스포티파이", "디즈니", "티빙", "웨이브", "쿠팡와우")),
            CategoryRule(
                "쇼핑",
                listOf(
                    "다이소", "스타필드", "에프알엘코리아", "유니클로", "무신사", "쿠팡", "올리브영", "토이랜드", "아울렛", "백화점",
                    "AK PLAZA", "AK플라자",
                ),
            ),
        )
    }
}
