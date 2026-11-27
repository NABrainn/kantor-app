package usecase

import databaseConnector.DatabaseFactory
import databaseConnector.WalletRepository
import model.Wallet

/** UC-10 Przeglądaj portfel. */
object ReadWallet {

    fun readWalletUseCase(userId: Int): Wallet {
        return DatabaseFactory.transaction { handle ->
            WalletRepository.findByUser(handle, userId)
        }
    }
}
