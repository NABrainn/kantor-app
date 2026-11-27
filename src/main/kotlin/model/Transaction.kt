package model

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

enum class TransactionType {
    TOP_UP, // zasilenie konta (symulowany przelew)
    BUY,    // kupno waluty za PLN
    SELL    // sprzedaż waluty za PLN
}

enum class RateType {
    BID, // kurs kupna – po nim kantor kupuje walutę od klienta
    ASK  // kurs sprzedaży – po nim kantor sprzedaje walutę klientowi
}

/**
 * Zapisana transakcja. Obiekt niezmienny – po zapisie nie jest modyfikowany ani usuwany.
 * Kurs i dane tabeli NBP są kopią z chwili transakcji.
 */
data class Transaction(
    val id: Long,
    val userId: Int,
    val type: TransactionType,
    val fromCurrency: String?,
    val toCurrency: String,
    val fromAmount: Long?,
    val toAmount: Long,
    val rate: BigDecimal?,
    val rateType: RateType?,
    val nbpTableNo: String?,
    val nbpEffectiveDate: LocalDate?,
    val createdAt: Instant
)

/** Transakcja do zapisania – id i datę utworzenia nadaje baza danych. */
data class NewTransaction(
    val userId: Int,
    val type: TransactionType,
    val toCurrency: String,
    val toAmount: Long,
    val fromCurrency: String? = null,
    val fromAmount: Long? = null,
    val rate: BigDecimal? = null,
    val rateType: RateType? = null,
    val nbpTableNo: String? = null,
    val nbpEffectiveDate: LocalDate? = null
)
