package usecase

import databaseConnector.DatabaseFactory
import databaseConnector.TransactionRepository
import model.Transaction

/** UC-11 Przeglądaj historię transakcji (od najnowszej). */
object ReadTransactionHistory {

    fun readTransactionHistoryUseCase(userId: Int, limit: Int): List<Transaction> {
        return DatabaseFactory.transaction { handle ->
            TransactionRepository.findByUser(handle, userId, limit)
        }
    }
}
