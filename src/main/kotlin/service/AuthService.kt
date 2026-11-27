package service

import at.favre.lib.crypto.bcrypt.BCrypt
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import model.AuthTokens
import java.time.Duration
import java.time.Instant

/**
 * Hasła i tokeny sesji (zamiast Firebase Authentication).
 * - hasła: hash bcrypt z solą – w bazie nigdy nie ma hasła w postaci jawnej (NFR-01),
 * - sesja: token JWT podpisany sekretem z pliku .env, ważny ograniczony czas (NFR-04).
 */
object AuthService {

    private const val BCRYPT_COST = 10 // koszt obliczeniowy hashowania (każde +1 = 2× wolniej)
    private const val ISSUER = "kantor-backend"

    private lateinit var algorithm: Algorithm
    private lateinit var tokenValidity: Duration

    fun init(jwtSecret: String, tokenValidityMinutes: Long) {
        require(jwtSecret.length >= 32) { "JWT_SECRET musi mieć co najmniej 32 znaki" }
        algorithm = Algorithm.HMAC256(jwtSecret)
        tokenValidity = Duration.ofMinutes(tokenValidityMinutes)
    }

    fun hashPassword(password: String): String {
        return BCrypt.withDefaults().hashToString(BCRYPT_COST, password.toCharArray())
    }

    fun verifyPassword(password: String, passwordHash: String): Boolean {
        return BCrypt.verifyer().verify(password.toCharArray(), passwordHash).verified
    }

    // Hash bez konta – sprawdzamy go, gdy e-maila nie ma w bazie, żeby odpowiedź trwała tyle samo
    private val dummyHash: String by lazy { hashPassword("konto-nie-istnieje") }

    /**
     * Sprawdza hasło użytkownika albo – gdy konta nie ma (passwordHash = null) – wykonuje to samo
     * obliczenie bcrypt na fikcyjnym hashu i zwraca false. Czas odpowiedzi nie zdradza więc,
     * czy konto z danym e-mailem istnieje (FR-S-02).
     */
    fun verifyPasswordConstantTime(password: String, passwordHash: String?): Boolean {
        val matches = verifyPassword(password, passwordHash ?: dummyHash)
        return passwordHash != null && matches
    }

    fun createToken(userId: Int): AuthTokens {
        val now = Instant.now()
        val token = JWT.create()
            .withIssuer(ISSUER)
            .withSubject(userId.toString())
            .withIssuedAt(now)
            .withExpiresAt(now.plus(tokenValidity))
            .sign(algorithm)

        return AuthTokens(
            accessToken = token,
            tokenType = "Bearer",
            expiresIn = tokenValidity.seconds
        )
    }

    /** Zwraca id użytkownika z tokenu albo null, gdy token jest nieprawidłowy lub wygasł. */
    fun verifyToken(token: String): Int? {
        return try {
            val decoded = JWT.require(algorithm)
                .withIssuer(ISSUER)
                .build()
                .verify(token)
            decoded.subject.toIntOrNull()
        } catch (e: JWTVerificationException) {
            null
        }
    }
}
