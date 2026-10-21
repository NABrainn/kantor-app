package core

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jdbi.v3.core.Jdbi
import org.jdbi.v3.core.statement.SqlStatements
import org.jdbi.v3.core.kotlin.KotlinPlugin

object DB {

    private lateinit var conn: Jdbi

    fun init(host: String, name: String, user: String, password: String) {
        val url = "jdbc:postgresql://$host/$name?user=$user&password=$password"
        val config = HikariConfig().apply {
            jdbcUrl = url
            maximumPoolSize = 10
            minimumIdle = 2
            isAutoCommit = false
        }
        val dataSource = HikariDataSource(config)
        this.conn = Jdbi.create(dataSource).apply {
            installPlugin(KotlinPlugin())
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
