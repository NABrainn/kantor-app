package model

import java.time.Instant

/** Użytkownik zwracany przez API (bez hasła). */
data class User(
    val id: Int,
    val email: String,
    val createdAt: Instant
)

/** Dane potrzebne tylko przy logowaniu – nigdy nie wysyłamy ich do klienta. */
data class UserCredentials(
    val id: Int,
    val passwordHash: String
)

/** Token sesji zwracany po zalogowaniu. */
data class AuthTokens(
    val accessToken: String,
    val tokenType: String,
    val expiresIn: Long // w sekundach
)
