package utils

import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializer
import com.google.gson.JsonParseException
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializer
import io.javalin.json.JsonMapper
import java.lang.reflect.Type
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/**
 * Zamiana obiektów na JSON i z powrotem (dla ctx.json() i ctx.bodyAsClass()).
 * Daty wysyłamy w formacie ISO 8601, a kursy jako tekst (np. "3.9502"), żeby klient nie tracił dokładności.
 */
object GsonMapper : JsonMapper {

    /** Liczba całkowita zapisana w JSON jako liczba. Tekst, ułamek albo wartość spoza zakresu Long → błąd (400). */
    private val strictLong = JsonDeserializer<Long> { json, _, _ ->
        val value = json as? JsonPrimitive
        if (value == null || !value.isNumber) {
            throw JsonParseException("Oczekiwano liczby całkowitej")
        }
        value.asBigDecimal.longValueExact()
    }

    private val gson = GsonBuilder()
        .serializeNulls()
        .registerTypeAdapter(Instant::class.java, JsonSerializer<Instant> { value, _, _ -> JsonPrimitive(value.toString()) })
        .registerTypeAdapter(LocalDate::class.java, JsonSerializer<LocalDate> { value, _, _ -> JsonPrimitive(value.toString()) })
        .registerTypeAdapter(BigDecimal::class.java, JsonSerializer<BigDecimal> { value, _, _ -> JsonPrimitive(value.toPlainString()) })
        // Kwoty muszą być liczbami całkowitymi w JSON – bez tego Gson przyjąłby też tekst "100" (FR-S-04)
        .registerTypeAdapter(Long::class.javaObjectType, strictLong)
        .registerTypeAdapter(Long::class.javaPrimitiveType, strictLong)
        .create()

    override fun <T : Any> fromJsonString(json: String, targetType: Type): T {
        return gson.fromJson(json, targetType)
    }

    override fun toJsonString(obj: Any, type: Type): String {
        return gson.toJson(obj)
    }
}
