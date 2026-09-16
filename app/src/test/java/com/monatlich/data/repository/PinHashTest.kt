package com.monatlich.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PinHashTest {

    @Test
    fun `hashing the same pin and salt twice is deterministic`() {
        val salt = PinHash.newSalt()
        assertEquals(PinHash.hash("1234", salt), PinHash.hash("1234", salt))
    }

    @Test
    fun `different pins hash differently with the same salt`() {
        val salt = PinHash.newSalt()
        assertNotEquals(PinHash.hash("1234", salt), PinHash.hash("4321", salt))
    }

    @Test
    fun `the same pin hashes differently with different salts`() {
        val saltA = PinHash.newSalt()
        val saltB = PinHash.newSalt()
        assertNotEquals(saltA, saltB)
        assertNotEquals(PinHash.hash("1234", saltA), PinHash.hash("1234", saltB))
    }

    @Test
    fun `salts are not trivially predictable`() {
        val salts = (1..20).map { PinHash.newSalt() }
        assertEquals(salts.size, salts.toSet().size)
    }
}
