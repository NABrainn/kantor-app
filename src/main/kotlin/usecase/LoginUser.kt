package usecase

import databaseConnector.DatabaseFactory
import databaseConnector.UserRepository
import model.AuthTokens
import model.UnauthorizedException
import service.AuthService

/** UC-02 Zaloguj się (FR-S-02). */
object LoginUser {

    fun loginUserUseCase(email: String, password: String): AuthTokens {
        val credentials = DatabaseFactory.transaction { handle ->
            UserRepository.findCredentialsByEmail(handle, email)
        }

        // Jeden komunikat i ten sam czas odpowiedzi dla złego e-maila i złego hasła –
        // nie zdradzamy, które pole jest błędne ani czy konto istnieje
        if (!AuthService.verifyPasswordConstantTime(password, credentials?.passwordHash) || credentials == null) {
            throw UnauthorizedException("Nieprawidłowy e-mail lub hasło")
        }

        return AuthService.createToken(credentials.id)
    }
}
