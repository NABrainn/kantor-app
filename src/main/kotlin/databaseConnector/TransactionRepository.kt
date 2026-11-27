package databaseConnector

import model.NewTransaction
import model.Transaction
import org.jdbi.v3.core.Handle
import org.jdbi.v3.core.kotlin.bindKotlin
import org.jdbi.v3.core.kotlin.mapTo

object TransactionRepository {

    /** Zapisuje transakcję i zwraca ją razem z id i czasem nadanym przez bazę. */
    fun add(handle: Handle, transaction: NewTransaction): Transaction {
        return handle.createQuery(
            """
            INSERT INTO transactions (
                user_id, type, from_currency, to_currency, from_amount, to_amount,
                rate, rate_type, nbp_table_no, nbp_effective_date
            )
            VALUES (
                :userId, CAST(:type AS transaction_type), :fromCurrency, :toCurrency, :fromAmount, :toAmount,
                :rate, CAST(:rateType AS rate_type), :nbpTableNo, :nbpEffectiveDate
            )
            RETURNING *
            """
        )
            .bindKotlin(transaction)
            .mapTo<Transaction>()
            .one()
    }

    /** Historia transakcji użytkownika – od najnowszej. */
    fun findByUser(handle: Handle, userId: Int, limit: Int): List<Transaction> {
        return handle.createQuery(
            """
            SELECT * FROM transactions
            WHERE user_id = :userId
            ORDER BY created_at DESC, id DESC
            LIMIT :limit
            """
        )
            .bind("userId", userId)
            .bind("limit", limit)
            .mapTo<Transaction>()
            .list()
    }
}
