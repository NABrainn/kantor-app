package controller

import io.javalin.config.RoutesConfig
import io.javalin.http.Context
import usecase.ReadHistoricalRates
import usecase.ReadOrFetchRateTable
import utils.Validation

object RatesController {

    fun register(routes: RoutesConfig) {
        routes.get("/api/rates", ::getCurrent)
        routes.get("/api/rates/{code}/history", ::getHistory)
    }

    /** GET /api/rates  →  200 + aktualna tabela C (numer, data i kursy kupna/sprzedaży) */
    private fun getCurrent(ctx: Context) {
        val table = ReadOrFetchRateTable.readOrFetchRateTableUseCase()
        ctx.status(200).json(table)
    }

    /** GET /api/rates/{code}/history?from=RRRR-MM-DD&to=RRRR-MM-DD  →  200 + kursy archiwalne */
    private fun getHistory(ctx: Context) {
        val code = Validation.requireCurrencyCode(ctx.pathParam("code"))
        val from = Validation.requireDate(ctx.queryParam("from"), "from")
        val to = Validation.requireDate(ctx.queryParam("to"), "to")
        Validation.requireDateRange(from, to)

        val history = ReadHistoricalRates.readHistoricalRatesUseCase(code, from, to)
        ctx.status(200).json(history)
    }
}
