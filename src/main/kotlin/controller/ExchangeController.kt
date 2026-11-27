package controller

import io.javalin.config.RoutesConfig
import io.javalin.http.Context
import model.ExchangeRequest
import model.QuoteRequest
import usecase.BuyCurrency
import usecase.QuoteExchange
import usecase.SellCurrency
import utils.Validation

object ExchangeController {

    fun register(routes: RoutesConfig) {
        routes.post("/api/exchange/quote", ::quote)
        routes.post("/api/exchange/buy", ::buy)
        routes.post("/api/exchange/sell", ::sell)
    }

    /** POST /api/exchange/quote  {type, currency, amount}  →  200 + wycena (bez zapisu) */
    private fun quote(ctx: Context) {
        val request = Validation.requireBody(ctx, QuoteRequest::class.java)
        val type = Validation.requireExchangeType(request.type)
        val currency = Validation.requireCurrencyCode(request.currency)
        val amount = Validation.requireAmount(request.amount)

        val quote = QuoteExchange.quoteExchangeUseCase(type, currency, amount)
        ctx.status(200).json(quote)
    }

    /** POST /api/exchange/buy  {currency, amount}  →  201 + transakcja i portfel po zmianie */
    private fun buy(ctx: Context) {
        val userId = AuthMiddleware.userId(ctx)
        val request = Validation.requireBody(ctx, ExchangeRequest::class.java)
        val currency = Validation.requireCurrencyCode(request.currency)
        val amount = Validation.requireAmount(request.amount)

        val result = BuyCurrency.buyCurrencyUseCase(userId, currency, amount)
        ctx.status(201).json(result)
    }

    /** POST /api/exchange/sell  {currency, amount}  →  201 + transakcja i portfel po zmianie */
    private fun sell(ctx: Context) {
        val userId = AuthMiddleware.userId(ctx)
        val request = Validation.requireBody(ctx, ExchangeRequest::class.java)
        val currency = Validation.requireCurrencyCode(request.currency)
        val amount = Validation.requireAmount(request.amount)

        val result = SellCurrency.sellCurrencyUseCase(userId, currency, amount)
        ctx.status(201).json(result)
    }
}
