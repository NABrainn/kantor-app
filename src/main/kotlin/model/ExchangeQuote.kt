package model

import java.math.BigDecimal
import java.time.LocalDate

/** Wycena wymiany: kurs i kwota w PLN. Pokazywana przed potwierdzeniem i używana przy samej transakcji. */
data class ExchangeQuote(
    val type: TransactionType,
    val currency: String,
    val amount: Long,     // kwota w walucie obcej (w centach)
    val rate: BigDecimal,
    val rateType: RateType,
    val plnAmount: Long,  // kwota w PLN (w groszach)
    val tableNo: String,
    val effectiveDate: LocalDate
)
