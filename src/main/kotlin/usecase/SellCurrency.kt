package usecase

import databaseConnector.DatabaseFactory
import databaseConnector.TransactionRepository
import databaseConnector.WalletRepository
import model.NewTransaction
import model.OperationResult
import model.PLN
import model.TransactionType
import service.ExchangeService

/** UC-08 Sprzedaj walutę (FR-S-08) – lustrzane odbicie kupna. */
object SellCurrency {

    fun sellCurrencyUseCase(userId: Int, currency: String, amount: Long): OperationResult {
        // 1. Kurs kupna (BID) z aktualnej tabeli C i kwota do wypłaty w PLN
        val table = ReadOrFetchRateTable.readOrFetchRateTableUseCase()
        val quote = ExchangeService.createQuote(TransactionType.SELL, table, currency, amount)

        // 2. Zmiana obu sald i zapis transakcji w jednej transakcji bazodanowej – wszystko albo nic
        return DatabaseFactory.transaction { handle ->
            val wallet = WalletRepository.findByUserForUpdate(handle, userId)
            wallet.debit(currency, amount) // brak środków → wyjątek → rollback, nic się nie zmienia
            wallet.credit(PLN, quote.plnAmount)
            WalletRepository.save(handle, wallet)

            val transaction = TransactionRepository.add(
                handle,
                NewTransaction(
                    userId = userId,
                    type = TransactionType.SELL,
                    fromCurrency = currency,
                    fromAmount = amount,
                    toCurrency = PLN,
                    toAmount = quote.plnAmount,
                    rate = quote.rate,
                    rateType = quote.rateType,
                    nbpTableNo = quote.tableNo,
                    nbpEffectiveDate = quote.effectiveDate
                )
            )

            OperationResult(transaction, WalletRepository.findByUser(handle, userId))
        }
    }
}
