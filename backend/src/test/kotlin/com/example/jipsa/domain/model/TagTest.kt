package com.example.jipsa.domain.model

import com.example.jipsa.domain.exception.InvariantViolationException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@DisplayName("Tag / Tags - 태그")
class TagTest {

    @ParameterizedTest
    @ValueSource(strings = ["점심", "#점심", "  #점심  ", "# 점심"])
    fun `앞의 샵과 공백을 떼어 정규화한다`(raw: String) {
        assertThat(Tag.of(raw).name).isEqualTo("점심")
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "   ", "#", " # "])
    fun `비어 있는 태그는 거부한다`(raw: String) {
        assertThatThrownBy { Tag.of(raw) }.isInstanceOf(InvariantViolationException::class.java)
    }

    @Test
    fun `20자까지 허용하고 21자는 거부한다`() {
        assertThat(Tag.of("가".repeat(20)).name).hasSize(20)
        assertThatThrownBy { Tag.of("가".repeat(21)) }.isInstanceOf(InvariantViolationException::class.java)
    }

    @Test
    fun `정규화되지 않은 값으로 직접 만들 수 없다`() {
        assertThatThrownBy { Tag("#점심") }.isInstanceOf(InvariantViolationException::class.java)
        assertThatThrownBy { Tag(" 점심") }.isInstanceOf(InvariantViolationException::class.java)
    }

    @Test
    fun `표기할 때는 샵을 붙인다`() {
        assertThat(Tag.of("점심").toString()).isEqualTo("#점심")
    }

    @Test
    fun `정규화 후 같은 태그는 하나로 합치고 순서를 보존한다`() {
        // when
        val tags = Tags.of("점심", "#혼밥", "#점심", "회식")

        // then
        assertThat(tags.names).containsExactly("점심", "혼밥", "회식")
        assertThat(Tag.of("혼밥") in tags).isTrue()
    }

    @Test
    fun `태그는 10개까지 붙일 수 있다`() {
        assertThat(Tags.of((1..10).map { "태그$it" }).values).hasSize(10)
        assertThatThrownBy { Tags.of((1..11).map { "태그$it" }) }
            .isInstanceOf(InvariantViolationException::class.java)
    }

    @Test
    fun `중복을 합친 뒤 개수를 센다`() {
        // 같은 태그 11번은 1개다
        assertThat(Tags.of(List(11) { "점심" }).values).hasSize(1)
    }
}
