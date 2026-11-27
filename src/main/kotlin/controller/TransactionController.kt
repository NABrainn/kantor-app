package controller

import io.javalin.config.RoutesConfig
import io.javalin.http.Context
import usecase.ReadTransactionHistory
import utils.Validation

object TransactionController {

    fun register(routes: RoutesConfig) {
        routes.get("/api/transactions", ::getHistory)
    }

    /** GET /api/transactions?limit=100  →  200 + lista transakcji od najnowszej */
    private fun getHistory(ctx: Context) {
        val userId = AuthMiddleware.userId(ctx)
        val limit = Validation.requireLimit(ctx.queryParam("limit"))

        val transactions = ReadTransactionHistory.readTransactionHistoryUseCase(userId, limit)
        ctx.status(200).json(transactions)
    }
}
