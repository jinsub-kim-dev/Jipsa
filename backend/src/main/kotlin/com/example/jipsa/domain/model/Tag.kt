package com.example.jipsa.domain.model

import com.example.jipsa.domain.exception.checkInvariant

/**
 * 태그. 거래에 붙이는 자유 분류다 (PRD 3.1).
 *
 * 앞의 `#` 은 표시용이므로 떼어 저장한다. "#점심" 과 "점심" 은 같은 태그다.
 * 값 객체이므로 생성 시점에 정규화된 값만 존재할 수 있다. 정규화는 [of] 가 한다.
 */
@JvmInline
value class Tag(val name: String) {
    init {
        checkInvariant(name.isNotBlank()) { "태그는 비어 있을 수 없습니다." }
        checkInvariant(name == name.trim() && !name.startsWith(PREFIX)) {
            "태그는 정규화된 값이어야 합니다. Tag.of 로 만드세요. 입력값: '$name'"
        }
        checkInvariant(name.length <= MAX_LENGTH) {
            "태그는 ${MAX_LENGTH}자 이하여야 합니다. 입력값: '$name'"
        }
    }

    override fun toString(): String = "$PREFIX$name"

    companion object {
        const val MAX_LENGTH: Int = 20
        private const val PREFIX: String = "#"

        /** 사용자 입력을 정규화해 태그를 만든다. 앞뒤 공백과 앞의 `#` 을 뗀다. */
        fun of(raw: String): Tag = Tag(raw.trim().removePrefix(PREFIX).trim())
    }
}

/**
 * 거래 하나에 붙은 태그 묶음.
 *
 * 순서를 보존하고 중복은 하나로 합친다. 최대 [MAX_COUNT] 개다.
 */
data class Tags private constructor(val values: Set<Tag>) {

    init {
        checkInvariant(values.size <= MAX_COUNT) {
            "태그는 최대 ${MAX_COUNT}개까지 붙일 수 있습니다. 입력 개수: ${values.size}"
        }
    }

    val names: List<String> get() = values.map(Tag::name)

    operator fun contains(tag: Tag): Boolean = tag in values

    companion object {
        const val MAX_COUNT: Int = 10

        val EMPTY: Tags = Tags(emptySet())

        /** 사용자 입력을 정규화해 만든다. 정규화 후 같은 태그는 하나로 합친다. */
        fun of(raw: Collection<String>): Tags = Tags(raw.map(Tag::of).toCollection(LinkedHashSet()))

        fun of(vararg raw: String): Tags = of(raw.toList())
    }
}
