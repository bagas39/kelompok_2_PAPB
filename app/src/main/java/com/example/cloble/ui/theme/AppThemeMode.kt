package com.example.cloble.ui.theme

enum class AppThemeMode(val storageKey: String, val label: String) {
    SYSTEM("system", "Sistem"),
    LIGHT("light", "Terang"),
    DARK("dark", "Gelap");

    companion object {
        fun fromStorageKey(key: String?): AppThemeMode =
            entries.firstOrNull { it.storageKey.equals(key, ignoreCase = true) } ?: SYSTEM
    }
}
