package com.example.jipsa.support

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.mysql.MySQLContainer

/**
 * 영속성 통합 테스트용 MySQL 컨테이너 설정.
 *
 * H2 가 아닌 실제 MySQL 을 사용한다 (backend/CLAUDE.md 5.2). 방언 차이로 인한 거짓 통과/실패를 막는다.
 */
@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    fun mysqlContainer(): MySQLContainer =
        MySQLContainer(MYSQL_IMAGE)
            .withDatabaseName("jipsa")
            .withUsername("jipsa")
            .withPassword("jipsapassword")
            .withReuse(true)

    companion object {
        /** docker-compose.yml 의 MySQL 버전과 일치시킨다. */
        const val MYSQL_IMAGE: String = "mysql:8.4"
    }
}
