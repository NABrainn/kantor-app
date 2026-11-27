package usecase

import model.NbpUnavailableException
import model.RateTable
import service.RateService

/**
 * UC-09 Pobierz aktualny kurs (wykorzystywany przez UC-05, UC-07 i UC-08).
 * Najpierw sprawdzamy tabelę zapisaną w bazie, a gdy jest nieaktualna – pobieramy nową z NBP i zapisujemy.
 */
object ReadOrFetchRateTable {

    fun readOrFetchRateTableUseCase(): RateTable {
        val cached = RateService.getLatestTableFromDb()

        if (cached != null && RateService.isUpToDate(cached)) {
            return cached
        }

        return try {
            val fetched = RateService.fetchCurrentTableFromNbp()
            RateService.saveTable(fetched)
            fetched
        } catch (e: NbpUnavailableException) {
            // NFR-10: NBP nie odpowiada – korzystamy z ostatniej zapisanej tabeli (jej data jest w odpowiedzi)
            cached ?: throw e
        }
    }
}
