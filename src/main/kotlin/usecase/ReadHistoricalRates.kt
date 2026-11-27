package usecase

import model.HistoricalRate
import model.ValidationException
import service.RateService
import java.time.LocalDate

/** UC-06 Przeglądaj kursy archiwalne (dane do wykresu – UC-12). */
object ReadHistoricalRates {

    fun readHistoricalRatesUseCase(code: String, from: LocalDate, to: LocalDate): List<HistoricalRate> {
        // Obsługujemy tylko waluty z tabeli C (Z-04)
        val currentTable = ReadOrFetchRateTable.readOrFetchRateTableUseCase()
        if (currentTable.getRate(code) == null) {
            throw ValidationException("Waluta $code nie występuje w tabeli kursów NBP")
        }

        return RateService.fetchHistoryFromNbp(code, from, to)
    }
}
