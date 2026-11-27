package usecase

import model.ExchangeQuote
import model.TransactionType
import service.ExchangeService

/** Wycena przed transakcją (FR-M-09): kurs i kwota w PLN, bez zapisu w bazie. */
object QuoteExchange {

    fun quoteExchangeUseCase(type: TransactionType, currency: String, amount: Long): ExchangeQuote {
        val table = ReadOrFetchRateTable.readOrFetchRateTableUseCase()
        return ExchangeService.createQuote(type, table, currency, amount)
    }
}
