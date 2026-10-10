package core

import io.javalin.config.Key
import org.jdbi.v3.core.Jdbi

fun jdbi(): Key<Jdbi> {
    return Key("jdbi")
}
