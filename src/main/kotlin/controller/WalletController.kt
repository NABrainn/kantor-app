package controller

import io.javalin.config.RoutesConfig
import io.javalin.http.Context
import model.TopUpRequest
import usecase.ReadWallet
import usecase.TopUpAccount
import utils.Validation

object WalletController {

    fun register(routes: RoutesConfig) {
        routes.get("/api/wallet", ::getWallet)
        routes.post("/api/wallet/top-up", ::topUp)
    }

    /** GET /api/wallet  →  200 + salda we wszystkich walutach */
    private fun getWallet(ctx: Context) {
        val userId = AuthMiddleware.userId(ctx)

        val wallet = ReadWallet.readWalletUseCase(userId)
        ctx.status(200).json(wallet)
    }

    /** POST /api/wallet/top-up  {amount}  →  201 + transakcja i portfel po zmianie */
    private fun topUp(ctx: Context) {
        val userId = AuthMiddleware.userId(ctx)
        val request = Validation.requireBody(ctx, TopUpRequest::class.java)
        val amount = Validation.requireAmount(request.amount)

        val result = TopUpAccount.topUpAccountUseCase(userId, amount)
        ctx.status(201).json(result)
    }
}
