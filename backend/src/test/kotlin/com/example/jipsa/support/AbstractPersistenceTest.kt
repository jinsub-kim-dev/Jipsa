package com.example.jipsa.support

import com.example.jipsa.domain.model.Category
import com.example.jipsa.domain.model.CategoryId
import com.example.jipsa.domain.query.StatisticsQuery
import com.example.jipsa.domain.repository.CategoryRepository
import com.example.jipsa.domain.repository.FixedExpenseRepository
import com.example.jipsa.domain.repository.InstallmentPlanRepository
import com.example.jipsa.domain.repository.TransactionRepository
import com.example.jipsa.infrastructure.persistence.adapter.CategoryRepositoryAdapter
import com.example.jipsa.infrastructure.persistence.adapter.FixedExpenseRepositoryAdapter
import com.example.jipsa.infrastructure.persistence.adapter.InstallmentPlanRepositoryAdapter
import com.example.jipsa.infrastructure.persistence.adapter.TransactionRepositoryAdapter
import com.example.jipsa.infrastructure.persistence.query.StatisticsQueryAdapter
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles

/**
 * 영속성 통합 테스트 기반 클래스.
 *
 * 실제 MySQL 컨테이너를 쓰므로 이 테스트가 통과하면 다음이 함께 검증된다.
 * - Flyway 마이그레이션이 정상 적용된다
 * - `ddl-auto=validate` 로 JPA 매핑과 실제 스키마가 일치한다
 * - 매퍼 왕복(도메인 -> 엔티티 -> DB -> 도메인)에서 정보가 유실되지 않는다
 *
 * 빈 DB 를 전제로 검증하므로 시드 데이터(db/seed)를 적용하지 않는다.
 * 시드의 정합성은 JipsaApplicationTests 가 전체 컨텍스트를 띄우며 검증한다.
 * 프로필을 고정한다. 쉘의 `SPRING_PROFILES_ACTIVE` 에 따라 테스트 설정이 바뀌면 안 된다.
 */
@ActiveProfiles("dev")
@DataJpaTest(properties = ["spring.flyway.locations=classpath:db/migration"])
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
// @DataJpaTest 슬라이스는 Flyway 자동 구성을 포함하지 않는다. 명시해야 마이그레이션이 적용된다.
@ImportAutoConfiguration(FlywayAutoConfiguration::class)
@Import(
    TestcontainersConfiguration::class,
    CategoryRepositoryAdapter::class,
    TransactionRepositoryAdapter::class,
    FixedExpenseRepositoryAdapter::class,
    InstallmentPlanRepositoryAdapter::class,
    StatisticsQueryAdapter::class,
)
abstract class AbstractPersistenceTest {

    @field:Autowired
    protected lateinit var categoryRepository: CategoryRepository

    @field:Autowired
    protected lateinit var transactionRepository: TransactionRepository

    @field:Autowired
    protected lateinit var fixedExpenseRepository: FixedExpenseRepository

    @field:Autowired
    protected lateinit var installmentPlanRepository: InstallmentPlanRepository

    @field:Autowired
    protected lateinit var statisticsQuery: StatisticsQuery

    @field:Autowired
    protected lateinit var testEntityManager: TestEntityManager

    /** 영속성 컨텍스트를 비워, 이후 조회가 실제 DB 를 다시 읽도록 만든다. */
    protected fun flushAndClear() {
        testEntityManager.flush()
        testEntityManager.clear()
    }

    /**
     * 테스트용 카테고리를 만든다. 기본 카테고리(V4 마이그레이션)와 이름이 겹치지 않도록 접두어를 붙인다.
     */
    protected fun savedCategory(name: String = "식비"): CategoryId =
        requireNotNull(categoryRepository.save(Category.create("$TEST_PREFIX$name", "🍚", "#EF4444")).id)

    companion object {
        const val TEST_PREFIX: String = "T-"
    }
}
