package usecase

import databaseConnector.DatabaseFactory
import databaseConnector.TransactionRepository
import databaseConnector.WalletRepository
import model.NewTransaction
import model.OperationResult
import model.PLN
import model.TransactionType
import service.ExchangeService

/** UC-07 Kup walutę (FR-S-07) – przebieg zgodny z diagramem sekwencji sekwencja_kupno.puml. */
object BuyCurrency {

    fun buyCurrencyUseCase(userId: Int, currency: String, amount: Long): OperationResult {
        // 1. Kurs sprzedaży (ASK) z aktualnej tabeli C i koszt w PLN
        val table = ReadOrFetchRateTable.readOrFetchRateTableUseCase()
        val quote = ExchangeService.createQuote(TransactionType.BUY, table, currency, amount)

        // 2. Zmiana obu sald i zapis transakcji w jednej transakcji bazodanowej – wszystko albo nic
        return DatabaseFactory.transaction { handle ->
            val wallet = WalletRepository.findByUserForUpdate(handle, userId)
            wallet.debit(PLN, quote.plnAmount) // brak środków → wyjątek → rollback, nic się nie zmienia
            wallet.credit(currency, amount)
            WalletRepository.save(handle, wallet)

            val transaction = TransactionRepository.add(
                handle,
                NewTransaction(
                    userId = userId,
                    type = TransactionType.BUY,
                    fromCurrency = PLN,
                    fromAmount = quote.plnAmount,
                    toCurrency = currency,
                    toAmount = amount,
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
