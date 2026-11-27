package utils

import io.javalin.http.Context
import model.PLN
import model.TransactionType
import model.ValidationException
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

/**
 * Sprawdzanie danych przysyłanych przez aplikację mobilną (FR-S-04).
 * Każda funkcja zwraca poprawną wartość albo rzuca ValidationException (→ 400 Bad Request z opisem).
 */
object Validation {

    // Ten sam wzorzec, którego pilnuje domena `email` w bazie danych (V1__INIT_SCHEMA.sql)
    private val EMAIL_PATTERN = Regex(
        "^[a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*$"
    )
    private val CURRENCY_CODE_PATTERN = Regex("^[A-Z]{3}$")

    private const val MIN_PASSWORD_LENGTH = 8
    private const val MAX_PASSWORD_BYTES = 72          // ograniczenie algorytmu bcrypt
    private const val MAX_AMOUNT = 100_000_000L        // 1 000 000,00 w jednostkach groszowych
    private const val MAX_HISTORY_DAYS = 93            // limit API NBP dla jednego zapytania
    private val FIRST_NBP_DATE = LocalDate.of(2002, 1, 2)
    private const val DEFAULT_HISTORY_LIMIT = 100
    private const val MAX_HISTORY_LIMIT = 500

    /** Odczytuje treść żądania (JSON) jako obiekt podanej klasy. Pusty lub błędny JSON → 400. */
    fun <T : Any> requireBody(ctx: Context, type: Class<T>): T {
        val body: T? = try {
            ctx.bodyAsClass(type)
        } catch (e: Exception) {
            null
        }
        return body ?: throw ValidationException(
            "Nieprawidłowy lub pusty JSON w treści żądania (kwoty podaje się jako liczby całkowite w groszach)"
        )
    }

    fun requireEmail(email: String?): String {
        val value = email?.trim()?.lowercase()
        if (value.isNullOrEmpty()) {
            throw ValidationException("Podaj adres e-mail")
        }
        if (value.length > 254 || !EMAIL_PATTERN.matches(value)) {
            throw ValidationException("Nieprawidłowy format adresu e-mail")
        }
        return value
    }

    /** Reguły dla nowego hasła (rejestracja). */
    fun requireNewPassword(password: String?): String {
        if (password.isNullOrEmpty()) {
            throw ValidationException("Podaj hasło")
        }
        if (password.length < MIN_PASSWORD_LENGTH) {
            throw ValidationException("Hasło musi mieć co najmniej $MIN_PASSWORD_LENGTH znaków")
        }
        if (password.toByteArray().size > MAX_PASSWORD_BYTES) {
            throw ValidationException("Hasło jest za długie")
        }
        return password
    }

    /** Przy logowaniu sprawdzamy tylko, czy hasło podano – nie zdradzamy reguł ani powodu odmowy. */
    fun requirePassword(password: String?): String {
        if (password.isNullOrEmpty()) {
            throw ValidationException("Podaj hasło")
        }
        return password
    }

    /** Kwota w jednostkach groszowych: dodatnia liczba całkowita (np. 10000 = 100,00). */
    fun requireAmount(amount: Long?): Long {
        if (amount == null) {
            throw ValidationException("Podaj kwotę w jednostkach groszowych, np. 10000 = 100,00")
        }
        if (amount <= 0) {
            throw ValidationException("Kwota musi być większa od zera")
        }
        if (amount > MAX_AMOUNT) {
            throw ValidationException("Kwota nie może przekraczać 1 000 000,00")
        }
        return amount
    }

    /** Kod waluty obcej, np. USD. Czy waluta jest w tabeli C, sprawdza ExchangeService / RateService. */
    fun requireCurrencyCode(code: String?): String {
        val value = code?.trim()?.uppercase()
        if (value == null || !CURRENCY_CODE_PATTERN.matches(value)) {
            throw ValidationException("Nieprawidłowy kod waluty – podaj 3 litery, np. USD")
        }
        if (value == PLN) {
            throw ValidationException("Wymiana dotyczy walut obcych – PLN jest walutą rozliczeniową")
        }
        return value
    }

    fun requireExchangeType(type: String?): TransactionType {
        return when (type?.trim()?.uppercase()) {
            "BUY" -> TransactionType.BUY
            "SELL" -> TransactionType.SELL
            else -> throw ValidationException("Pole type musi mieć wartość BUY albo SELL")
        }
    }

    fun requireDate(value: String?, paramName: String): LocalDate {
        if (value.isNullOrBlank()) {
            throw ValidationException("Podaj parametr $paramName w formacie RRRR-MM-DD")
        }
        return try {
            LocalDate.parse(value)
        } catch (e: DateTimeParseException) {
            throw ValidationException("Nieprawidłowa data w parametrze $paramName – użyj formatu RRRR-MM-DD")
        }
    }

    fun requireDateRange(from: LocalDate, to: LocalDate) {
        if (from.isAfter(to)) {
            throw ValidationException("Data początkowa nie może być późniejsza niż końcowa")
        }
        if (to.isAfter(todayInPoland())) {
            throw ValidationException("Data końcowa nie może być z przyszłości")
        }
        if (from.isBefore(FIRST_NBP_DATE)) {
            throw ValidationException("NBP udostępnia kursy od $FIRST_NBP_DATE")
        }
        if (ChronoUnit.DAYS.between(from, to) >= MAX_HISTORY_DAYS) {
            throw ValidationException("Zakres dat może obejmować najwyżej $MAX_HISTORY_DAYS dni")
        }
    }

    /** Parametr ?limit= dla historii transakcji (opcjonalny). */
    fun requireLimit(value: String?): Int {
        if (value.isNullOrBlank()) {
            return DEFAULT_HISTORY_LIMIT
        }
        val limit = value.toIntOrNull()
        if (limit == null || limit !in 1..MAX_HISTORY_LIMIT) {
            throw ValidationException("Parametr limit musi być liczbą od 1 do $MAX_HISTORY_LIMIT")
        }
        return limit
    }
}
