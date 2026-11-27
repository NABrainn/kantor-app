package model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WalletTest {

    private fun walletWith100Pln() = Wallet(userId = 1, balances = mutableListOf(Balance(currency = PLN, amount = 10000)))

    @Test
    fun `credit zwieksza saldo istniejacej waluty`() {
        val wallet = walletWith100Pln()
        wallet.credit(PLN, 2550)
        assertEquals(12550, wallet.getBalance(PLN))
    }

    @Test
    fun `credit dodaje nowa walute`() {
        val wallet = walletWith100Pln()
        wallet.credit("USD", 1000)
        assertEquals(1000, wallet.getBalance("USD"))
        assertEquals(2, wallet.balances.size)
    }

    @Test
    fun `debit zmniejsza saldo`() {
        val wallet = walletWith100Pln()
        wallet.debit(PLN, 10000)
        assertEquals(0, wallet.getBalance(PLN))
    }

    @Test
    fun `debit przy braku srodkow rzuca wyjatek i nie zmienia salda`() {
        val wallet = walletWith100Pln()
        assertFailsWith<InsufficientFundsException> { wallet.debit(PLN, 10001) }
        assertEquals(10000, wallet.getBalance(PLN))
    }

    @Test
    fun `debit waluty, ktorej uzytkownik nie ma, rzuca wyjatek`() {
        val wallet = walletWith100Pln()
        assertFailsWith<InsufficientFundsException> { wallet.debit("EUR", 1) }
    }

    @Test
    fun `hasFunds sprawdza pokrycie`() {
        val wallet = walletWith100Pln()
        assertTrue(wallet.hasFunds(PLN, 10000))
        assertFalse(wallet.hasFunds(PLN, 10001))
        assertFalse(wallet.hasFunds("USD", 1))
    }
}
