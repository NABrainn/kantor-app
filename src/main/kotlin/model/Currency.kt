package model

/** Waluta bazowa kantoru – wszystkie wymiany odbywają się w parze PLN ↔ waluta obca (Z-03). */
const val PLN = "PLN"

/** Waluta ze słownika walut (kod ISO 4217 i nazwa z tabeli NBP). */
data class Currency(
    val code: String,
    val name: String
)
