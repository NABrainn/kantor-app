package utils

import model.TransactionType
import model.ValidationException
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ValidationTest {

    @Test
    fun `e-mail jest normalizowany do malych liter`() {
        assertEquals("jan.kowalski@example.com", Validation.requireEmail("  Jan.Kowalski@Example.COM "))
    }

    @Test
    fun `nieprawidlowy e-mail jest odrzucany`() {
        assertFailsWith<ValidationException> { Validation.requireEmail("brak-malpy") }
        assertFailsWith<ValidationException> { Validation.requireEmail(null) }
    }

    @Test
    fun `nowe haslo musi miec co najmniej 8 znakow`() {
        assertFailsWith<ValidationException> { Validation.requireNewPassword("1234567") }
        assertEquals("12345678", Validation.requireNewPassword("12345678"))
    }

    @Test
    fun `kwota musi byc dodatnia i nie wieksza niz milion`() {
        assertEquals(1, Validation.requireAmount(1))
        assertFailsWith<ValidationException> { Validation.requireAmount(0) }
        assertFailsWith<ValidationException> { Validation.requireAmount(-100) }
        assertFailsWith<ValidationException> { Validation.requireAmount(100_000_001) }
        assertFailsWith<ValidationException> { Validation.requireAmount(null) }
    }

    @Test
    fun `kod waluty to 3 litery i nie PLN`() {
        assertEquals("USD", Validation.requireCurrencyCode(" usd "))
        assertFailsWith<ValidationException> { Validation.requireCurrencyCode("US") }
        assertFailsWith<ValidationException> { Validation.requireCurrencyCode("PLN") }
    }

    @Test
    fun `typ wymiany to BUY albo SELL`() {
        assertEquals(TransactionType.BUY, Validation.requireExchangeType("buy"))
        assertEquals(TransactionType.SELL, Validation.requireExchangeType("SELL"))
        assertFailsWith<ValidationException> { Validation.requireExchangeType("TOP_UP") }
    }

    @Test
    fun `zakres dat jest sprawdzany`() {
        val today = todayInPoland()
        Validation.requireDateRange(today.minusDays(30), today)

        assertFailsWith<ValidationException> { Validation.requireDateRange(today, today.minusDays(1)) }
        assertFailsWith<ValidationException> { Validation.requireDateRange(today, today.plusDays(1)) }
        assertFailsWith<ValidationException> { Validation.requireDateRange(today.minusDays(100), today) }
        assertFailsWith<ValidationException> { Validation.requireDateRange(LocalDate.of(2001, 12, 1), LocalDate.of(2002, 1, 10)) }
    }
}
