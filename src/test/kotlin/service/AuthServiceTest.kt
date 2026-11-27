package service

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuthServiceTest {

    @BeforeTest
    fun setUp() {
        AuthService.init(jwtSecret = "test_secret_used_only_in_unit_tests_123456", tokenValidityMinutes = 60)
    }

    @Test
    fun `haslo jest hashowane, a nie zapisywane jawnie`() {
        val hash = AuthService.hashPassword("tajnehaslo1")

        assertNotEquals("tajnehaslo1", hash)
        assertTrue(hash.startsWith("$2a$")) // format bcrypt
        assertTrue(AuthService.verifyPassword("tajnehaslo1", hash))
        assertFalse(AuthService.verifyPassword("innehaslo1", hash))
    }

    @Test
    fun `brak konta daje false tak samo jak zle haslo`() {
        val hash = AuthService.hashPassword("tajnehaslo1")

        assertTrue(AuthService.verifyPasswordConstantTime("tajnehaslo1", hash))
        assertFalse(AuthService.verifyPasswordConstantTime("innehaslo1", hash))
        assertFalse(AuthService.verifyPasswordConstantTime("tajnehaslo1", null))
        assertFalse(AuthService.verifyPasswordConstantTime("konto-nie-istnieje", null))
    }

    @Test
    fun `token zawiera id uzytkownika`() {
        val tokens = AuthService.createToken(userId = 42)

        assertEquals("Bearer", tokens.tokenType)
        assertEquals(3600, tokens.expiresIn)
        assertEquals(42, AuthService.verifyToken(tokens.accessToken))
    }

    @Test
    fun `zmieniony lub obcy token jest odrzucany`() {
        val token = AuthService.createToken(userId = 42).accessToken

        assertNull(AuthService.verifyToken(token.dropLast(2) + "xx"))
        assertNull(AuthService.verifyToken("nie-jest-tokenem"))
    }

    @Test
    fun `token podpisany innym sekretem jest odrzucany`() {
        val token = AuthService.createToken(userId = 42).accessToken
        AuthService.init(jwtSecret = "zupelnie_inny_sekret_takze_dlugi_1234567890", tokenValidityMinutes = 60)

        assertNull(AuthService.verifyToken(token))
    }

    @Test
    fun `zbyt krotki sekret jest odrzucany przy starcie`() {
        assertFailsWith<IllegalArgumentException> {
            AuthService.init(jwtSecret = "krotki", tokenValidityMinutes = 60)
        }
    }
}
