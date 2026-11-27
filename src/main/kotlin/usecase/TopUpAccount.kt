package usecase

import databaseConnector.DatabaseFactory
import databaseConnector.TransactionRepository
import databaseConnector.WalletRepository
import model.NewTransaction
import model.OperationResult
import model.PLN
import model.TransactionType

/** UC-04 Zasil konto symulowanym przelewem (FR-S-06). Zasilenie jest możliwe tylko w PLN (Z-06). */
object TopUpAccount {

    fun topUpAccountUseCase(userId: Int, amount: Long): OperationResult {
        return DatabaseFactory.transaction { handle ->
            val wallet = WalletRepository.findByUserForUpdate(handle, userId)
            wallet.credit(PLN, amount)
            WalletRepository.save(handle, wallet)

            val transaction = TransactionRepository.add(
                handle,
                NewTransaction(
                    userId = userId,
                    type = TransactionType.TOP_UP,
                    toCurrency = PLN,
                    toAmount = amount
                )
            )

            OperationResult(transaction, WalletRepository.findByUser(handle, userId))
        }
    }
}
