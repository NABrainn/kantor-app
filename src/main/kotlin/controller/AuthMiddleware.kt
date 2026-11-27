package controller

import io.javalin.config.RoutesConfig
import io.javalin.http.Context
import io.javalin.http.HandlerType
import model.UnauthorizedException
import service.AuthService

/**
 * Autoryzacja (FR-S-03): każde żądanie pod adresem /api (poza /api/auth) musi mieć nagłówek
 * "Authorization: Bearer <token>". Id użytkownika bierzemy wyłącznie z tokenu – klient nigdy go nie przysyła,
 * więc ma dostęp tylko do własnych danych.
 */
object AuthMiddleware {

    private const val USER_ID_ATTRIBUTE = "userId"

    fun register(routes: RoutesConfig) {
        routes.before("/api/*", ::verifyToken)
    }

    /** Id zalogowanego użytkownika – do użycia w kontrolerach chronionych endpointów. */
    fun userId(ctx: Context): Int {
        return ctx.attribute<Int>(USER_ID_ATTRIBUTE)
            ?: throw UnauthorizedException("Brak zalogowanego użytkownika")
    }

    private fun verifyToken(ctx: Context) {
        val isCorsPreflight = ctx.method() == HandlerType.OPTIONS
        val isPublicPath = ctx.path().startsWith("/api/auth/")
        if (isCorsPreflight || isPublicPath) {
            return
        }

        val token = ctx.header("Authorization")?.removePrefix("Bearer ")?.trim()
        val userId = token?.let { AuthService.verifyToken(it) }
            ?: throw UnauthorizedException("Sesja wygasła lub token jest nieprawidłowy – zaloguj się ponownie")

        ctx.attribute(USER_ID_ATTRIBUTE, userId)
    }
}
