package databaseConnector

import model.Rate
import model.RateTable
import org.jdbi.v3.core.Handle
import org.jdbi.v3.core.kotlin.mapTo

object RateTableRepository {

    /** Najnowsza zapisana tabela kursów (albo null, gdy jeszcze żadnej nie pobraliśmy). */
    fun findLatest(handle: Handle): RateTable? {
        val table = handle.createQuery(
            """
            SELECT table_no, trading_date, effective_date, fetched_at
            FROM rate_tables
            ORDER BY effective_date DESC
            LIMIT 1
            """
        )
            .mapTo<RateTable>()
            .findOne()
            .orElse(null)
            ?: return null

        val rates = handle.createQuery(
            """
            SELECT r.currency_code AS code, c.name AS currency, r.bid, r.ask
            FROM rates r
            JOIN currencies c ON c.code = r.currency_code
            WHERE r.table_no = :tableNo
            ORDER BY r.currency_code
            """
        )
            .bind("tableNo", table.tableNo)
            .mapTo<Rate>()
            .list()

        return table.copy(rates = rates)
    }

    /**
     * Zapisuje tabelę i jej kursy. Jeśli tabela już jest w bazie, aktualizujemy tylko czas pobrania,
     * żeby wiedzieć, kiedy ostatnio sprawdzaliśmy NBP. Waluty muszą już być w słowniku (CurrencyRepository).
     */
    fun save(handle: Handle, table: RateTable) {
        handle.createUpdate(
            """
            INSERT INTO rate_tables (table_no, trading_date, effective_date, fetched_at)
            VALUES (:tableNo, :tradingDate, :effectiveDate, :fetchedAt)
            ON CONFLICT (table_no) DO UPDATE SET fetched_at = EXCLUDED.fetched_at
            """
        )
            .bind("tableNo", table.tableNo)
            .bind("tradingDate", table.tradingDate)
            .bind("effectiveDate", table.effectiveDate)
            .bind("fetchedAt", table.fetchedAt)
            .execute()

        for (rate in table.rates) {
            handle.createUpdate(
                """
                INSERT INTO rates (table_no, currency_code, bid, ask)
                VALUES (:tableNo, :code, :bid, :ask)
                ON CONFLICT (table_no, currency_code) DO NOTHING
                """
            )
                .bind("tableNo", table.tableNo)
                .bind("code", rate.code)
                .bind("bid", rate.bid)
                .bind("ask", rate.ask)
                .execute()
        }
    }
}
