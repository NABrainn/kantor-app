package core

import java.net.http.HttpClient
import java.util.concurrent.Executors

object Http {
    val client: HttpClient = HttpClient
        .newBuilder()
        .executor(Executors.newVirtualThreadPerTaskExecutor())
        .build()
}