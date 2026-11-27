package model

/**
 * Błąd, który serwer zamienia na odpowiedź HTTP z podanym kodem i komunikatem (zob. Main.kt).
 * Komunikaty są po polsku, bo aplikacja mobilna pokazuje je użytkownikowi.
 */
open class ApiException(val status: Int, override val message: String) : RuntimeException(message)

/** 400 – nieprawidłowe dane wejściowe. */
class ValidationException(message: String) : ApiException(400, message)

/** 401 – brak, nieprawidłowy lub wygasły token albo błędne dane logowania. */
class UnauthorizedException(message: String) : ApiException(401, message)

/** 409 – np. e-mail jest już zajęty. */
class ConflictException(message: String) : ApiException(409, message)

/** 422 – za mało środków na wykonanie operacji. */
class InsufficientFundsException(currency: String) :
    ApiException(422, "Brak wystarczających środków w walucie $currency")

/** 503 – API NBP nie odpowiada, a w bazie nie ma żadnej zapisanej tabeli kursów. */
class NbpUnavailableException(message: String) : ApiException(503, message)
