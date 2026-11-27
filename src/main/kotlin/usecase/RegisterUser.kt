package usecase

import databaseConnector.DatabaseFactory
import databaseConnector.UserRepository
import databaseConnector.WalletRepository
import model.ConflictException
import model.User
import service.AuthService

/** UC-01 Zarejestruj się (FR-S-01). */
object RegisterUser {

    fun registerUserUseCase(email: String, password: String): User {
        val passwordHash = AuthService.hashPassword(password)

        // Konto i pusty portfel tworzymy w jednej transakcji – nie powstanie konto bez portfela
        return DatabaseFactory.transaction { handle ->
            val user = UserRepository.create(handle, email, passwordHash)
                ?: throw ConflictException("Konto z tym adresem e-mail już istnieje")

            WalletRepository.createEmpty(handle, user.id)
            user
        }
    }
}
