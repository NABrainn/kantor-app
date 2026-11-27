package service

import model.Rate
import model.RateTable
import model.RateType
import model.TransactionType
import model.ValidationException
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ExchangeServiceTest {

    private val table = RateTable(
        tableNo = "216/C/NBP/2026",
        tradingDate = LocalDate.of(2026, 11, 4),
        effectiveDate = LocalDate.of(2026, 11, 5),
        fetchedAt = Instant.now(),
        rates = listOf(
            Rate(code = "USD", currency = "dolar amerykański", bid = BigDecimal("3.8720"), ask = BigDecimal("3.9502")),
            Rate(code = "HUF", currency = "forint (Węgry)", bid = BigDecimal("0.012008"), ask = BigDecimal("0.01225"))
        )
    )

    @Test
    fun `kupno uzywa kursu sprzedazy ASK`() {
        val quote = ExchangeService.createQuote(TransactionType.BUY, table, "USD", 10000)

        assertEquals(RateType.ASK, quote.rateType)
        assertEquals(BigDecimal("3.9502"), quote.rate)
        assertEquals(39502, quote.plnAmount) // 100,00 USD × 3,9502 = 395,02 PLN
        assertEquals("216/C/NBP/2026", quote.tableNo)
    }

    @Test
    fun `sprzedaz uzywa kursu kupna BID`() {
        val quote = ExchangeService.createQuote(TransactionType.SELL, table, "USD", 10000)

        assertEquals(RateType.BID, quote.rateType)
        assertEquals(38720, quote.plnAmount) // 100,00 USD × 3,8720 = 387,20 PLN
    }

    @Test
    fun `przy kupnie zaokraglamy w gore na korzysc kantoru`() {
        // 10,01 USD × 3,9502 = 39,541502 PLN → klient płaci 39,55 PLN
        assertEquals(3955, ExchangeService.calculatePlnAmount(TransactionType.BUY, 1001, BigDecimal("3.9502")))
    }

    @Test
    fun `przy sprzedazy zaokraglamy w dol na korzysc kantoru`() {
        // 50,55 USD × 3,8720 = 195,7296 PLN → klient dostaje 195,72 PLN
        assertEquals(19572, ExchangeService.calculatePlnAmount(TransactionType.SELL, 5055, BigDecimal("3.8720")))
    }

    @Test
    fun `waluta spoza tabeli C jest odrzucana`() {
        assertFailsWith<ValidationException> {
            ExchangeService.createQuote(TransactionType.BUY, table, "XYZ", 100)
        }
    }

    @Test
    fun `sprzedaz kwoty wartej mniej niz grosz jest odrzucana`() {
        // 0,01 HUF × 0,012008 = 0,00012 PLN → po zaokrągleniu w dół 0 groszy
        assertFailsWith<ValidationException> {
            ExchangeService.createQuote(TransactionType.SELL, table, "HUF", 1)
        }
    }
}
