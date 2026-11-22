package com.example.cloble.domain

import com.example.cloble.domain.util.PasswordPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordPolicyUnitTest {

    @Test
    fun isValidEmail_acceptsStandardFormatsAndRejectsMalformed() {
        assertTrue(PasswordPolicy.isValidEmail("user@cloble.id"))
        assertTrue(PasswordPolicy.isValidEmail("  user.name+tag@sub.domain.com  "))

        assertFalse(PasswordPolicy.isValidEmail(""))
        assertFalse(PasswordPolicy.isValidEmail("user"))
        assertFalse(PasswordPolicy.isValidEmail("user@"))
        assertFalse(PasswordPolicy.isValidEmail("user@domain"))
        assertFalse(PasswordPolicy.isValidEmail("user @domain.com"))
        assertFalse(PasswordPolicy.isValidEmail("@domain.com"))
    }

    @Test
    fun validateNewPassword_enforcesMinLengthAndAlphanumericMix() {
        assertEquals("Password minimal 8 karakter", PasswordPolicy.validateNewPassword("Abc1234"))
        assertEquals("Password harus mengandung huruf dan angka", PasswordPolicy.validateNewPassword("abcdefgh"))
        assertEquals("Password harus mengandung huruf dan angka", PasswordPolicy.validateNewPassword("12345678"))
        assertNotNull(PasswordPolicy.validateNewPassword("        "))

        assertNull(PasswordPolicy.validateNewPassword("CloBle123!"))
        assertNull(PasswordPolicy.validateNewPassword("pass1234"))
    }
}
