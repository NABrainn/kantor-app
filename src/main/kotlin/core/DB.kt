package core

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jdbi.v3.core.Jdbi
import org.jdbi.v3.core.statement.SqlStatements
import kotlin.text.get

object DB {

    private lateinit var conn: Jdbi

    fun init(host: String, user: String, password: String) {
        val config = HikariConfig().apply {
            jdbcUrl = "jdbc:postgresql://$host:$user:$password"
            maximumPoolSize = 10
            minimumIdle = 2
            isAutoCommit = false
        }
        val dataSource = HikariDataSource(config)
        this.conn = Jdbi.create(dataSource).apply {
            registerRowMapper(AbstractRowMapperFactory())
            getConfig(SqlStatements::class.java)
                .addExceptionHandler { ex, _ ->
                    throw RuntimeException(ex.message)
                }
        }
    }

    fun jdbi(): Jdbi {
        return conn
    }
}