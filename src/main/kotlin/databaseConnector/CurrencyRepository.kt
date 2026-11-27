package databaseConnector

import model.Currency
import org.jdbi.v3.core.Handle

object CurrencyRepository {

    /** Dodaje waluty do słownika albo aktualizuje ich nazwy (waluty pochodzą z tabeli C NBP – Z-04). */
    fun saveAll(handle: Handle, currencies: List<Currency>) {
        for (currency in currencies) {
            handle.createUpdate(
                """
                INSERT INTO currencies (code, name)
                VALUES (:code, :name)
                ON CONFLICT (code) DO UPDATE SET name = EXCLUDED.name
                """
            )
                .bind("code", currency.code)
                .bind("name", currency.name)
                .execute()
        }
    }
}
