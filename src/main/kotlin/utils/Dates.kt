package utils

import java.time.LocalDate
import java.time.ZoneId

/** Dzisiejsza data w Polsce – NBP publikuje tabele według czasu polskiego, a serwer może działać w UTC. */
fun todayInPoland(): LocalDate {
    return LocalDate.now(ZoneId.of("Europe/Warsaw"))
}
