package model

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/** Tabela kursów C opublikowana przez NBP. */
data class RateTable(
    val tableNo: String,
    val tradingDate: LocalDate,
    val effectiveDate: LocalDate,
    val fetchedAt: Instant,
    val rates: List<Rate> = emptyList()
) {
    fun getRate(code: String): Rate? {
        return rates.find { it.code == code }
    }
}

/** Kurs jednej waluty w tabeli C (za 1 jednostkę waluty, w PLN). */
data class Rate(
    val code: String,
    val currency: String,
    val bid: BigDecimal,
    val ask: BigDecimal
) {
    fun valueFor(type: RateType): BigDecimal {
        return when (type) {
            RateType.BID -> bid
            RateType.ASK -> ask
        }
    }
}

/** Kurs archiwalny wybranej waluty z jednego dnia. */
data class HistoricalRate(
    val effectiveDate: LocalDate,
    val tableNo: String,
    val bid: BigDecimal,
    val ask: BigDecimal
)
