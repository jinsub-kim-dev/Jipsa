package com.example.jipsa.domain.model

import com.example.jipsa.domain.exception.InvariantViolationException
import com.example.jipsa.fixture.category
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@DisplayName("Category - 카테고리")
class CategoryTest {

    @Test
    fun `이름과 아이콘의 공백을 다듬고 색은 대문자로 맞춘다`() {
        val category = Category.create("  식비 ", " 🍚 ", "#ef4444")

        assertThat(category.name).isEqualTo("식비")
        assertThat(category.icon).isEqualTo("🍚")
        assertThat(category.color).isEqualTo("#EF4444")
    }

    @Test
    fun `이름이 비어 있으면 거부한다`() {
        assertThatThrownBy { Category.create(" ", "🍚", "#EF4444") }
            .isInstanceOf(InvariantViolationException::class.java)
    }

    @Test
    fun `이름은 20자까지다`() {
        assertThat(Category.create("가".repeat(20), "🍚", "#EF4444").name).hasSize(20)
        assertThatThrownBy { Category.create("가".repeat(21), "🍚", "#EF4444") }
            .isInstanceOf(InvariantViolationException::class.java)
    }

    @Test
    fun `아이콘이 비어 있거나 너무 길면 거부한다`() {
        assertThatThrownBy { Category.create("식비", " ", "#EF4444") }
            .isInstanceOf(InvariantViolationException::class.java)
        assertThatThrownBy { Category.create("식비", "가".repeat(9), "#EF4444") }
            .isInstanceOf(InvariantViolationException::class.java)
    }

    @ParameterizedTest
    @ValueSource(strings = ["EF4444", "#EF444", "#EF44445", "#GG4444", "red"])
    fun `색이 #RRGGBB 형식이 아니면 거부한다`(color: String) {
        assertThatThrownBy { Category.create("식비", "🍚", color) }
            .isInstanceOf(InvariantViolationException::class.java)
    }

    @Test
    fun `이름과 표시를 바꾼다`() {
        val renamed = category().rename(" 외식 ").changeAppearance("🍝", "#00ff00")

        assertThat(renamed.name).isEqualTo("외식")
        assertThat(renamed.icon).isEqualTo("🍝")
        assertThat(renamed.color).isEqualTo("#00FF00")
        assertThat(renamed.id).isEqualTo(CategoryId(1L))
    }

    @Test
    fun `동일성은 식별자로 판단한다`() {
        assertThat(category(name = "식비", id = 1L)).isEqualTo(category(name = "외식", id = 1L))
        assertThat(category(id = 1L)).isNotEqualTo(category(id = 2L))
        assertThat(category(id = null)).isNotEqualTo(category(id = null))
        assertThat(category(id = 1L)).isNotEqualTo("식비")
        assertThat(category(id = 1L).hashCode()).isEqualTo(category(id = 1L).hashCode())
        assertThat(category(id = null).hashCode()).isZero()
        val same = category()
        assertThat(same).isEqualTo(same)
        assertThat(same.toString()).contains("식비")
    }

    @Test
    fun `식별자는 양수여야 한다`() {
        assertThatThrownBy { CategoryId(0) }.isInstanceOf(InvariantViolationException::class.java)
        assertThatThrownBy { TransactionId(0) }.isInstanceOf(InvariantViolationException::class.java)
        assertThatThrownBy { FixedExpenseId(0) }.isInstanceOf(InvariantViolationException::class.java)
        assertThatThrownBy { InstallmentPlanId(0) }.isInstanceOf(InvariantViolationException::class.java)
        assertThat(listOf(CategoryId(1), TransactionId(2), FixedExpenseId(3), InstallmentPlanId(4)).map { it.toString() })
            .containsExactly("1", "2", "3", "4")
    }
}
