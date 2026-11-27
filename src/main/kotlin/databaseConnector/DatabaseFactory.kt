package databaseConnector

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.jdbi.v3.core.Handle
import org.jdbi.v3.core.Jdbi
import org.jdbi.v3.core.kotlin.KotlinPlugin
import org.jdbi.v3.core.kotlin.inTransactionUnchecked

object DatabaseFactory {

    private lateinit var jdbi: Jdbi

    fun init(host: String, port: String, name: String, user: String, password: String) {
        val jdbcUrl = "jdbc:postgresql://$host:$port/$name"

        // 1. Migracje – Flyway tworzy / aktualizuje tabele na podstawie plików z resources/db/migration
        Flyway.configure()
            .dataSource(jdbcUrl, user, password)
            .load()
            .migrate()

        // 2. Pula połączeń (Hikari) i JDBI, przez które wykonujemy zapytania SQL
        val config = HikariConfig().apply {
            this.jdbcUrl = jdbcUrl
            this.username = user
            this.password = password
            maximumPoolSize = 10
            minimumIdle = 2
        }
        jdbi = Jdbi.create(HikariDataSource(config))
        jdbi.installPlugin(KotlinPlugin())

        println("Database connected at $jdbcUrl")
    }

    /**
     * Wykonuje blok w jednej transakcji bazodanowej – wszystko albo nic.
     * Jeśli w bloku poleci wyjątek, wszystkie zmiany są wycofywane (rollback).
     */
    fun <T> transaction(block: (Handle) -> T): T {
        return jdbi.inTransactionUnchecked(block)
    }
}
