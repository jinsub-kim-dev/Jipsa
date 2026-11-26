package com.example.jipsa.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * API 문서 설정. 문서 UI: `/swagger-ui.html`, 스펙(JSON): `/v3/api-docs` (dev 프로필에서만 노출)
 */
@Configuration(proxyBeanMethods = false)
class OpenApiConfiguration {

    @Bean
    fun jipsaOpenApi(): OpenAPI = OpenAPI().info(
        Info()
            .title("Jipsa 가계부 API")
            .version("v0.0.1")
            .description(
                """
                월별 소비 패턴 분석 가계부 API. 용어는 JIPSA_PRD.md 를 따른다.

                - 지출 종류(kind): GENERAL(일반 지출) / FIXED(고정비) / INSTALLMENT(할부)
                - 직접 등록하는 거래는 일반 지출뿐이다. 고정비·할부 거래는 각 정의를 등록하면 만들어진다.
                - 모든 분석은 소비일(spentDate) 기준이다.
                - 증감률이 null 이면 이전 값이 0원이라 정의할 수 없다는 뜻이다(화면은 "신규").
                """.trimIndent(),
            ),
    )
}
