package model

import java.time.Instant

/** Saldo w jednej walucie. Kwota w jednostkach groszowych (np. 12345 = 123,45). */
data class Balance(
    val currency: String,
    var amount: Long,
    val updatedAt: Instant? = null
)

/**
 * Portfel walutowy użytkownika – zbiór jego sald.
 * Zmiany robimy w pamięci (debit / credit), a potem zapisujemy portfel w bazie.
 */
class Wallet(
    val userId: Int,
    val balances: MutableList<Balance>
) {
    fun getBalance(currency: String): Long {
        return findBalance(currency)?.amount ?: 0
    }

    fun hasFunds(currency: String, amount: Long): Boolean {
        return getBalance(currency) >= amount
    }

    /** Zmniejsza saldo. Rzuca InsufficientFundsException, gdy środków jest za mało. */
    fun debit(currency: String, amount: Long) {
        val balance = findBalance(currency)
        if (balance == null || balance.amount < amount) {
            throw InsufficientFundsException(currency)
        }
        balance.amount -= amount
    }

    /** Zwiększa saldo. Jeśli użytkownik nie miał jeszcze tej waluty, dodaje nowe saldo. */
    fun credit(currency: String, amount: Long) {
        val balance = findBalance(currency)
        if (balance == null) {
            balances.add(Balance(currency = currency, amount = amount))
        } else {
            balance.amount += amount
        }
    }

    private fun findBalance(currency: String): Balance? {
        return balances.find { it.currency == currency }
    }
}
