package controller

import io.javalin.config.RoutesConfig
import io.javalin.http.Context
import model.LoginRequest
import model.RegisterRequest
import usecase.LoginUser
import usecase.RegisterUser
import utils.Validation

object AuthController {

    fun register(routes: RoutesConfig) {
        routes.post("/api/auth/register", ::registerUser)
        routes.post("/api/auth/login", ::login)
    }

    /** POST /api/auth/register  {email, password}  →  201 + dane użytkownika */
    private fun registerUser(ctx: Context) {
        val request = Validation.requireBody(ctx, RegisterRequest::class.java)
        val email = Validation.requireEmail(request.email)
        val password = Validation.requireNewPassword(request.password)

        val user = RegisterUser.registerUserUseCase(email, password)
        ctx.status(201).json(user)
    }

    /** POST /api/auth/login  {email, password}  →  200 + token sesji */
    private fun login(ctx: Context) {
        val request = Validation.requireBody(ctx, LoginRequest::class.java)
        val email = Validation.requireEmail(request.email)
        val password = Validation.requirePassword(request.password)

        val tokens = LoginUser.loginUserUseCase(email, password)
        ctx.status(200).json(tokens)
    }
}
