package com.example.jipsa.config

import com.example.jipsa.domain.service.CardStatementImporter
import com.example.jipsa.domain.service.CategoryTrendAnalyzer
import com.example.jipsa.domain.service.CommitmentForecaster
import com.example.jipsa.domain.service.FixedExpenseConverter
import com.example.jipsa.domain.service.FixedExpenseRecorder
import com.example.jipsa.domain.service.MerchantCategoryClassifier
import com.example.jipsa.domain.service.SpendingInsightAnalyzer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock
import java.time.ZoneId

/**
 * 도메인 서비스와 시계를 스프링 컨텍스트에 등록하는 구성(composition root).
 *
 * 도메인 서비스는 프레임워크에 독립적인 순수 클래스여야 하므로 `@Component` 를 붙이지 않는다.
 * 대신 4개 레이어 밖의 이 구성 클래스에서 빈으로 등록한다.
 */
@Configuration(proxyBeanMethods = false)
class DomainServiceConfiguration {

    /** "이번 달" 판단은 한국 시간 기준이다. 테스트는 고정된 시계를 주입한다. */
    @Bean
    fun clock(): Clock = Clock.system(ZoneId.of("Asia/Seoul"))

    @Bean
    fun fixedExpenseRecorder(): FixedExpenseRecorder = FixedExpenseRecorder()

    @Bean
    fun fixedExpenseConverter(): FixedExpenseConverter = FixedExpenseConverter()

    @Bean
    fun commitmentForecaster(): CommitmentForecaster = CommitmentForecaster()

    @Bean
    fun categoryTrendAnalyzer(): CategoryTrendAnalyzer = CategoryTrendAnalyzer()

    @Bean
    fun spendingInsightAnalyzer(): SpendingInsightAnalyzer = SpendingInsightAnalyzer()

    @Bean
    fun cardStatementImporter(): CardStatementImporter = CardStatementImporter(MerchantCategoryClassifier())
}
