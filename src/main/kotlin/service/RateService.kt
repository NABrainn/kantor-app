package service

import databaseConnector.CurrencyRepository
import databaseConnector.DatabaseFactory
import databaseConnector.RateTableRepository
import model.Currency
import model.HistoricalRate
import model.NbpUnavailableException
import model.RateTable
import utils.NbpApiMapper
import utils.todayInPoland
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/**
 * Kursy walut: pobieranie z API NBP (tabela C) i pamięć podręczna w bazie danych.
 * Dokumentacja API: https://api.nbp.pl
 */
object RateService {

    private const val NBP_API_URL = "https://api.nbp.pl/api"

    // Jak często najwyżej pytamy NBP o nową tabelę, gdy w bazie nie ma dzisiejszej (np. w weekend)
    private val CHECK_INTERVAL = Duration.ofMinutes(15)

    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build()

    fun fetchCurrentTableFromNbp(): RateTable {
        val body = get("$NBP_API_URL/exchangerates/tables/C/")
            ?: throw NbpUnavailableException("NBP nie zwrócił aktualnej tabeli kursów")

        return NbpApiMapper.parseRateTable(body)
    }

    /** Kursy archiwalne jednej waluty. Pusta lista, gdy w tym okresie NBP nie publikował tabel (np. weekend). */
    fun fetchHistoryFromNbp(code: String, from: LocalDate, to: LocalDate): List<HistoricalRate> {
        val body = get("$NBP_API_URL/exchangerates/rates/C/$code/$from/$to/")
            ?: return emptyList()

        return NbpApiMapper.parseHistory(body)
    }

    fun getLatestTableFromDb(): RateTable? {
        return DatabaseFactory.transaction { handle ->
            RateTableRepository.findLatest(handle)
        }
    }

    fun saveTable(table: RateTable) {
        DatabaseFactory.transaction { handle ->
            val currencies = table.rates.map { Currency(code = it.code, name = it.currency) }
            CurrencyRepository.saveAll(handle, currencies)
            RateTableRepository.save(handle, table)
        }
    }

    /**
     * Tabela jest aktualna, jeśli NBP opublikował ją dzisiaj albo niedawno sprawdzaliśmy, że nowszej nie ma.
     * NBP publikuje tabelę C raz dziennie w dni robocze, więc nie ma sensu pytać go przy każdym żądaniu.
     */
    fun isUpToDate(table: RateTable): Boolean {
        val publishedToday = table.effectiveDate == todayInPoland()
        val checkedRecently = table.fetchedAt.isAfter(Instant.now().minus(CHECK_INTERVAL))
        return publishedToday || checkedRecently
    }

    /** GET do API NBP. Zwraca treść odpowiedzi albo null dla 404 (NBP tak sygnalizuje brak danych). */
    private fun get(url: String): String? {
        val request = HttpRequest.newBuilder(URI.create(url))
            .header("Accept", "application/json")
            .timeout(Duration.ofSeconds(10))
            .GET()
            .build()

        val response = try {
            client.send(request, HttpResponse.BodyHandlers.ofString())
        } catch (e: IOException) {
            throw NbpUnavailableException("Brak połączenia z API NBP")
        }

        return when (response.statusCode()) {
            200 -> response.body()
            404 -> null
            else -> throw NbpUnavailableException("API NBP odpowiedziało kodem ${response.statusCode()}")
        }
    }
}
