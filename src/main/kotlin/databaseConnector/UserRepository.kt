package databaseConnector

import model.User
import model.UserCredentials
import org.jdbi.v3.core.Handle
import org.jdbi.v3.core.kotlin.mapTo

object UserRepository {

    /** Tworzy użytkownika. Zwraca null, jeśli konto z tym e-mailem już istnieje. */
    fun create(handle: Handle, email: String, passwordHash: String): User? {
        return handle.createQuery(
            """
            INSERT INTO users (email, password_hash)
            VALUES (:email, :passwordHash)
            ON CONFLICT (email) DO NOTHING
            RETURNING id, email, created_at
            """
        )
            .bind("email", email)
            .bind("passwordHash", passwordHash)
            .mapTo<User>()
            .findOne()
            .orElse(null)
    }

    fun findCredentialsByEmail(handle: Handle, email: String): UserCredentials? {
        return handle.createQuery("SELECT id, password_hash FROM users WHERE email = :email")
            .bind("email", email)
            .mapTo<UserCredentials>()
            .findOne()
            .orElse(null)
    }
}
