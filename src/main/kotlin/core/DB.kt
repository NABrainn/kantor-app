package core

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.github.cdimascio.dotenv.Dotenv
import org.jdbi.v3.core.Jdbi
import org.jdbi.v3.core.statement.SqlStatements

object DB {
    private val dotenv = Dotenv.load()

    private val dataSource: HikariDataSource by lazy {
        val config = HikariConfig().apply {
            jdbcUrl = dotenv["DB_USER_PASS_URL"]
            maximumPoolSize = 10
            minimumIdle = 2
            isAutoCommit = false
        }
        HikariDataSource(config)
    }

    private val conn: Jdbi = Jdbi.create(dataSource).apply {
        registerRowMapper(AbstractRowMapperFactory())
        getConfig(SqlStatements::class.java)
            .addExceptionHandler { ex, _ ->
                throw RuntimeException(ex.message)
            }
    }

    fun jdbi(): Jdbi {
        return conn
    }
}