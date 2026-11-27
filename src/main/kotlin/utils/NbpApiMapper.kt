package utils

import com.google.gson.Gson
import model.HistoricalRate
import model.Rate
import model.RateTable
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/** Zamienia odpowiedzi JSON z API NBP na obiekty modelu. */
object NbpApiMapper {

    private val gson = Gson()

    /** Odpowiedź z /exchangerates/tables/C/ – lista z jedną tabelą. */
    fun parseRateTable(json: String): RateTable {
        val table = gson.fromJson(json, Array<NbpTableResponse>::class.java).first()

        return RateTable(
            tableNo = table.no,
            tradingDate = LocalDate.parse(table.tradingDate),
            effectiveDate = LocalDate.parse(table.effectiveDate),
            fetchedAt = Instant.now(),
            rates = table.rates.map { rate ->
                Rate(code = rate.code, currency = rate.currency, bid = rate.bid, ask = rate.ask)
            }
        )
    }

    /** Odpowiedź z /exchangerates/rates/C/{code}/{from}/{to}/ – kursy jednej waluty z kolejnych dni. */
    fun parseHistory(json: String): List<HistoricalRate> {
        val history = gson.fromJson(json, NbpHistoryResponse::class.java)

        return history.rates.map { rate ->
            HistoricalRate(
                effectiveDate = LocalDate.parse(rate.effectiveDate),
                tableNo = rate.no,
                bid = rate.bid,
                ask = rate.ask
            )
        }
    }
}

// Kształt odpowiedzi API NBP (tylko pola, których używamy).
// Kursy czytamy jako BigDecimal, żeby nie stracić dokładności (NFR-06).

private data class NbpTableResponse(
    val no: String,
    val tradingDate: String,
    val effectiveDate: String,
    val rates: List<NbpRateResponse>
)

private data class NbpRateResponse(
    val currency: String,
    val code: String,
    val bid: BigDecimal,
    val ask: BigDecimal
)

private data class NbpHistoryResponse(
    val code: String,
    val rates: List<NbpHistoricalRateResponse>
)

private data class NbpHistoricalRateResponse(
    val no: String,
    val effectiveDate: String,
    val bid: BigDecimal,
    val ask: BigDecimal
)
