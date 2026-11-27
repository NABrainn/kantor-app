package model

/** Wynik operacji zmieniającej saldo: zapisana transakcja i portfel po zmianie. */
data class OperationResult(
    val transaction: Transaction,
    val wallet: Wallet
)

/** Treść odpowiedzi z błędem, np. {"error": "Brak wystarczających środków w walucie PLN"}. */
data class ErrorResponse(
    val error: String
)
