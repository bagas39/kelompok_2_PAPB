package com.example.cloble.domain.util

object PasswordPolicy {
    const val MIN_LENGTH = 8

    private val EMAIL_REGEX = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

    fun isValidEmail(email: String): Boolean {
        val trimmed = email.trim()
        return trimmed.isNotEmpty() && trimmed.length <= 254 && EMAIL_REGEX.matches(trimmed)
    }

    fun validateNewPassword(password: String): String? {
        if (password.length < MIN_LENGTH) {
            return "Password minimal $MIN_LENGTH karakter"
        }
        val hasLetter = password.any { it.isLetter() }
        val hasDigit = password.any { it.isDigit() }
        if (!hasLetter || !hasDigit) {
            return "Password harus mengandung huruf dan angka"
        }
        return null
    }
}
