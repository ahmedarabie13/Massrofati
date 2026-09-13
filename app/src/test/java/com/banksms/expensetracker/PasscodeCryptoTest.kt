package com.banksms.expensetracker

import com.banksms.expensetracker.data.auth.PasscodeCrypto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PasscodeCryptoTest {

    @Test
    fun `valid format accepts exactly four digits`() {
        assertTrue(PasscodeCrypto.isValidFormat("1234"))
        assertTrue(PasscodeCrypto.isValidFormat("0000"))
    }

    @Test
    fun `valid format rejects anything else`() {
        assertFalse(PasscodeCrypto.isValidFormat(""))
        assertFalse(PasscodeCrypto.isValidFormat("123"))
        assertFalse(PasscodeCrypto.isValidFormat("12345"))
        assertFalse(PasscodeCrypto.isValidFormat("12a4"))
        assertFalse(PasscodeCrypto.isValidFormat(" 123"))
    }

    @Test
    fun `hash is deterministic for same salt and code`() {
        assertEquals(
            PasscodeCrypto.hash("salt", "1234"),
            PasscodeCrypto.hash("salt", "1234")
        )
    }

    @Test
    fun `hash differs for different codes`() {
        assertNotEquals(
            PasscodeCrypto.hash("salt", "1234"),
            PasscodeCrypto.hash("salt", "4321")
        )
    }

    @Test
    fun `hash differs for different salts`() {
        assertNotEquals(
            PasscodeCrypto.hash("salt-a", "1234"),
            PasscodeCrypto.hash("salt-b", "1234")
        )
    }

    @Test
    fun `generated salts are unique`() {
        assertNotEquals(PasscodeCrypto.generateSalt(), PasscodeCrypto.generateSalt())
    }
}
