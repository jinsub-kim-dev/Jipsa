package com.example.jipsa.domain.model

import com.example.jipsa.domain.exception.checkInvariant

/**
 * 카테고리 애그리거트 루트 (PRD 3.4).
 *
 * 거래 하나에 정확히 하나 붙는 단일 분류다. 평면(flat)이며 상위·하위를 두지 않는다.
 * 화면 표시용 아이콘(이모지)과 색을 함께 갖는다. 색이 서버에 있어야 대시보드·캘린더·
 * 히트맵이 같은 카테고리를 같은 색으로 그린다.
 *
 * 불변식:
 * - 이름은 공백이 아니며 [NAME_MAX_LENGTH] 자 이하다.
 * - 아이콘은 공백이 아니며 [ICON_MAX_LENGTH] 자 이하다. (이모지는 여러 코드 유닛일 수 있다)
 * - 색은 `#RRGGBB` 형식이다.
 */
class Category private constructor(
    override val id: CategoryId?,
    val name: String,
    val icon: String,
    val color: String,
) : AggregateRoot<CategoryId> {

    init {
        checkInvariant(name.isNotBlank()) { "카테고리 이름은 비어 있을 수 없습니다." }
        checkInvariant(name.length <= NAME_MAX_LENGTH) {
            "카테고리 이름은 ${NAME_MAX_LENGTH}자 이하여야 합니다. 입력값 길이: ${name.length}"
        }
        checkInvariant(icon.isNotBlank()) { "카테고리 아이콘은 비어 있을 수 없습니다." }
        checkInvariant(icon.length <= ICON_MAX_LENGTH) {
            "카테고리 아이콘은 ${ICON_MAX_LENGTH}자 이하여야 합니다. 입력값: $icon"
        }
        checkInvariant(COLOR_PATTERN.matches(color)) {
            "카테고리 색은 #RRGGBB 형식이어야 합니다. 입력값: $color"
        }
    }

    fun rename(newName: String): Category = Category(id, newName.trim(), icon, color)

    fun changeAppearance(newIcon: String, newColor: String): Category =
        Category(id, name, newIcon.trim(), newColor.uppercase())

    fun assignId(assignedId: CategoryId): Category = Category(assignedId, name, icon, color)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Category) return false
        val thisId = id ?: return false
        return thisId == other.id
    }

    override fun hashCode(): Int = id?.hashCode() ?: 0

    override fun toString(): String = "Category(id=$id, name='$name', icon=$icon, color=$color)"

    companion object {
        const val NAME_MAX_LENGTH: Int = 20
        const val ICON_MAX_LENGTH: Int = 8
        private val COLOR_PATTERN = Regex("^#[0-9A-F]{6}$")

        fun create(name: String, icon: String, color: String): Category =
            Category(id = null, name = name.trim(), icon = icon.trim(), color = color.uppercase())

        /** 저장된 데이터로부터 복원한다. 매퍼에서만 사용한다. */
        fun reconstitute(id: CategoryId, name: String, icon: String, color: String): Category =
            Category(id = id, name = name, icon = icon, color = color)
    }
}
