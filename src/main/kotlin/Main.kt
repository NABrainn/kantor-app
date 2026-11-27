import controller.AuthController
import controller.AuthMiddleware
import controller.ExchangeController
import controller.RatesController
import controller.TransactionController
import controller.WalletController
import databaseConnector.DatabaseFactory
import io.github.cdimascio.dotenv.Dotenv
import io.javalin.Javalin
import io.javalin.util.JavalinLogger
import model.ApiException
import model.ErrorResponse
import service.AuthService
import utils.GsonMapper

fun main(args: Array<String>) {
    // ENV – lokalnie z pliku .env.dev (inny plik: --env-file=.env.prod),
    // w Dockerze ze zmiennych środowiskowych (mają pierwszeństwo przed plikiem)
    val envFileName = args.find { it.startsWith("--env-file=") }?.substringAfter("=") ?: ".env.dev"
    val env = Dotenv.configure().filename(envFileName).ignoreIfMissing().load()

    fun required(key: String): String {
        return env[key] ?: throw IllegalArgumentException("Brak zmiennej $key – uzupełnij plik $envFileName (wzór: .env.example)")
    }

    // BAZA DANYCH – połączenie i migracje Flyway
    DatabaseFactory.init(
        host = required("POSTGRES_HOST"),
        port = env["POSTGRES_PORT"] ?: "5432",
        name = required("POSTGRES_DB"),
        user = required("POSTGRES_USER"),
        password = required("POSTGRES_PASSWORD")
    )

    // UWIERZYTELNIANIE – sekret do podpisywania tokenów JWT
    AuthService.init(
        jwtSecret = required("JWT_SECRET"),
        tokenValidityMinutes = env["JWT_EXPIRATION_MINUTES"]?.toLongOrNull() ?: 60
    )

    // SERWER HTTP
    val port = env["SERVER_PORT"]?.toIntOrNull() ?: 2137
    Javalin.create { config ->
        config.concurrency.useVirtualThreads = true
        config.jsonMapper(GsonMapper)
        config.startup.showJavalinBanner = false
        config.startup.showOldJavalinVersionWarning = false

        // CORS – potrzebny tylko dla wersji przeglądarkowej aplikacji Expo (aplikacja na Androidzie go nie wymaga)
        config.bundledPlugins.enableCors { cors ->
            cors.addRule { rule ->
                rule.allowHost("http://localhost:8081", "http://localhost:19006")
            }
        }

        // LOGI – każde żądanie w konsoli, np. "POST /api/exchange/buy -> 201 (35 ms)"
        config.requestLogger.http { ctx, timeMs ->
            println("${ctx.method()} ${ctx.path()} -> ${ctx.status().code} (${timeMs.toInt()} ms)")
        }

        // BŁĘDY – wyjątki zamieniamy na odpowiedź JSON {"error": "..."}
        config.routes.exception(ApiException::class.java) { e, ctx ->
            ctx.status(e.status).json(ErrorResponse(e.message))
        }
        config.routes.exception(Exception::class.java) { e, ctx ->
            JavalinLogger.error("Nieobsłużony błąd: ${e.message}", e)
            ctx.status(500).json(ErrorResponse("Wewnętrzny błąd serwera"))
        }

        // ENDPOINTY
        AuthMiddleware.register(config.routes)
        AuthController.register(config.routes)
        RatesController.register(config.routes)
        WalletController.register(config.routes)
        ExchangeController.register(config.routes)
        TransactionController.register(config.routes)
    }.start(port)
}
