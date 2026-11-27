package service

import model.ExchangeQuote
import model.RateTable
import model.RateType
import model.TransactionType
import model.ValidationException
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Zasady wyceny wymiany walut:
 * - Z-02: klient kupuje walutę po kursie sprzedaży (ASK), a sprzedaje po kursie kupna (BID),
 * - Z-07: kwoty są w jednostkach groszowych, wynik zaokrąglamy do pełnego grosza na korzyść kantoru.
 */
object ExchangeService {

    fun createQuote(type: TransactionType, table: RateTable, currency: String, amount: Long): ExchangeQuote {
        val rate = table.getRate(currency)
            ?: throw ValidationException("Waluta $currency nie występuje w tabeli kursów NBP")

        val rateType = when (type) {
            TransactionType.BUY -> RateType.ASK
            TransactionType.SELL -> RateType.BID
            TransactionType.TOP_UP -> throw IllegalArgumentException("Zasilenie konta nie jest wymianą walut")
        }
        val rateValue = rate.valueFor(rateType)
        val plnAmount = calculatePlnAmount(type, amount, rateValue)

        if (plnAmount <= 0) {
            throw ValidationException("Kwota jest zbyt mała – jej równowartość to mniej niż 1 grosz")
        }

        return ExchangeQuote(
            type = type,
            currency = currency,
            amount = amount,
            rate = rateValue,
            rateType = rateType,
            plnAmount = plnAmount,
            tableNo = table.tableNo,
            effectiveDate = table.effectiveDate
        )
    }

    /**
     * Kwota w PLN (w groszach) za podaną kwotę waluty (w centach).
     * Obie kwoty są w setnych częściach, więc wystarczy pomnożyć przez kurs.
     * Kupno: klient płaci – zaokrąglamy w górę. Sprzedaż: klient dostaje – zaokrąglamy w dół.
     */
    fun calculatePlnAmount(type: TransactionType, amount: Long, rate: BigDecimal): Long {
        val exactAmount = BigDecimal.valueOf(amount).multiply(rate)
        val rounding = if (type == TransactionType.BUY) RoundingMode.CEILING else RoundingMode.FLOOR
        return exactAmount.setScale(0, rounding).longValueExact()
    }
}
