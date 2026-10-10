import com.google.gson.GsonBuilder
import core.AbstractRowMapperFactory
import io.github.cdimascio.dotenv.Dotenv
import io.javalin.Javalin
import io.javalin.apibuilder.ApiBuilder.get
import io.javalin.apibuilder.ApiBuilder.path
import io.javalin.http.staticfiles.Location
import io.javalin.json.JsonMapper
import io.javalin.util.JavalinLogger
import org.flywaydb.core.Flyway
import org.jdbi.v3.core.Jdbi
import java.lang.reflect.Type
import java.nio.file.Path
import org.jdbi.v3.core.statement.SqlStatements


fun main() {
    //ENV
    val dotenv = Dotenv.load()
    val mode = dotenv["MODE"]
    val DB_USER = dotenv["DB_USER"]
    val DB_PASS = dotenv["DB_PASS"]
    val DB_URL = dotenv["DB_URL"]
    //STATIC FILES
    val baseDirectory = when (mode) {
        "DEV" -> Path.of(System.getProperty("user.dir"))
            .resolve("src/main/resources")
            .toAbsolutePath()
            .normalize()
            .toString()

        "PROD" -> "/"
        else -> Path.of(System.getProperty("user.dir"))
            .resolve("src/main/resources")
            .toAbsolutePath()
            .normalize()
            .toString()
    }

    val location = when (mode) {
        "DEV" -> Location.EXTERNAL
        "PROD" -> Location.CLASSPATH
        else -> Location.EXTERNAL
    }
    //FLYWAY
    val flyway = Flyway
        .configure()
        .dataSource(DB_URL, DB_USER, DB_PASS)
        .load()
    flyway.migrate()
    //JSON MAPPER
    val gson = GsonBuilder().create()
    val jsonMapper = object : JsonMapper {
        override fun <T : Any> fromJsonString(json: String, targetType: Type): T =
            gson.fromJson(json, targetType)

        override fun toJsonString(obj: Any, type: Type) =
            gson.toJson(obj)
    }
    //WHITELIST
    val protectedPaths = listOf("/")
    //ERROR 500 EXCEPTIONS
    val serverErrorExceptions = listOf(
        NullPointerException::class.java,
        RuntimeException::class.java,
        IllegalArgumentException::class.java,
        IllegalStateException::class.java
    )

    Javalin.create{ config ->
        config.concurrency.useVirtualThreads = true
        config.jsonMapper(jsonMapper)
        config.startup.showJavalinBanner = false
        config.startup.showOldJavalinVersionWarning = false
        config.staticFiles.add { staticFiles ->
            staticFiles.hostedPath = "/public/css"
            staticFiles.directory = "$baseDirectory/public/css"
            staticFiles.location = location
        }

        config.staticFiles.add { staticFiles ->
            staticFiles.hostedPath = "/public/js"
            staticFiles.directory = "$baseDirectory/public/js"
            staticFiles.location = location
        }

        config.staticFiles.add { staticFiles ->
            staticFiles.hostedPath = "/public/img"
            staticFiles.directory = "$baseDirectory/public/img"
            staticFiles.location = location
        }

        serverErrorExceptions.forEach { exceptionClass ->
            config.routes.exception(exceptionClass) { e, ctx ->
                if (e.cause != null) {
                    JavalinLogger.error(e.cause?.message.orEmpty())
                }
                if (e.message != null) {
                    JavalinLogger.error(e.message.orEmpty())
                }
                ctx.status(500)
            }
        }

        config.routes.apiBuilder {
            path("/") {
                get("/users") {}
            }
        }
    }.start(2137)
}