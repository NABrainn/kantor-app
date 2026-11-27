package model

// Treści żądań wysyłanych przez aplikację mobilną.
// Wszystkie pola są nullable, bo klient może ich nie przysłać – sprawdzamy je w kontrolerach (utils/Validation).
// Kwoty (amount) są w jednostkach groszowych, np. 10000 = 100,00.

data class RegisterRequest(
    val email: String?,
    val password: String?
)

data class LoginRequest(
    val email: String?,
    val password: String?
)

data class TopUpRequest(
    val amount: Long?
)

data class QuoteRequest(
    val type: String?, // "BUY" albo "SELL"
    val currency: String?,
    val amount: Long?
)

data class ExchangeRequest(
    val currency: String?,
    val amount: Long?
)
