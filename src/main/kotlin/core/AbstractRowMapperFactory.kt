package core

import org.jdbi.v3.core.config.ConfigRegistry
import org.jdbi.v3.core.mapper.RowMapper
import org.jdbi.v3.core.mapper.RowMapperFactory
import org.jdbi.v3.core.mapper.reflect.ConstructorMapper
import java.lang.reflect.Type
import java.util.*

class AbstractRowMapperFactory : RowMapperFactory {
    override fun build(
        type: Type?,
        config: ConfigRegistry?
    ): Optional<RowMapper<*>> {
        if (type is Class<*>) {
            val isRecord = type.isRecord
            val isKotlinDataClass = type.kotlin.isData

            if (isRecord || isKotlinDataClass) {
                return ConstructorMapper.factory(type).build(type, config)
            }
        }
        return Optional.empty()
    }
}