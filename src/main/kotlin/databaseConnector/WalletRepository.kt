package databaseConnector

import model.Balance
import model.PLN
import model.Wallet
import org.jdbi.v3.core.Handle
import org.jdbi.v3.core.kotlin.mapTo

object WalletRepository {

    /** Nowy portfel z saldem 0 PLN (Z-08). */
    fun createEmpty(handle: Handle, userId: Int) {
        handle.createUpdate("INSERT INTO balances (user_id, currency_code, amount) VALUES (:userId, :currency, 0)")
            .bind("userId", userId)
            .bind("currency", PLN)
            .execute()
    }

    /** Portfel do odczytu. Salda posortowane: najpierw PLN, potem alfabetycznie. */
    fun findByUser(handle: Handle, userId: Int): Wallet {
        val balances = handle.createQuery(
            """
            SELECT currency_code AS currency, amount, updated_at
            FROM balances
            WHERE user_id = :userId
            ORDER BY currency_code = 'PLN' DESC, currency_code
            """
        )
            .bind("userId", userId)
            .mapTo<Balance>()
            .list()

        return Wallet(userId, balances.toMutableList())
    }

    /**
     * Portfel do zmiany salda. Najpierw blokujemy wiersz użytkownika (SELECT ... FOR UPDATE),
     * więc kolejna operacja tego samego użytkownika poczeka, aż obecna transakcja się zakończy.
     * Dzięki temu równoczesne żądania nie nadpiszą sobie sald (NFR-05).
     */
    fun findByUserForUpdate(handle: Handle, userId: Int): Wallet {
        handle.createQuery("SELECT id FROM users WHERE id = :userId FOR UPDATE")
            .bind("userId", userId)
            .mapTo<Int>()
            .one()

        return findByUser(handle, userId)
    }

    /** Zapisuje salda portfela. Aktualizuje tylko te, które się zmieniły; nowe waluty dodaje. */
    fun save(handle: Handle, wallet: Wallet) {
        for (balance in wallet.balances) {
            handle.createUpdate(
                """
                INSERT INTO balances (user_id, currency_code, amount)
                VALUES (:userId, :currency, :amount)
                ON CONFLICT (user_id, currency_code)
                DO UPDATE SET amount = EXCLUDED.amount, updated_at = now()
                WHERE balances.amount <> EXCLUDED.amount
                """
            )
                .bind("userId", wallet.userId)
                .bind("currency", balance.currency)
                .bind("amount", balance.amount)
                .execute()
        }
    }
}
