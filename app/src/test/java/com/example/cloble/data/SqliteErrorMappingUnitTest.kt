package com.example.cloble.data

import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabaseLockedException
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteTableLockedException
import com.example.cloble.data.repository.EntityMappers
import com.example.cloble.domain.result.DbFailure
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SqliteErrorMappingUnitTest {

    @Test
    fun databaseLockedException_mapsToBusyOrLocked() {
        val result = EntityMappers.runCatchingSqlite(
            onBusyOrLocked = { DbFailure.DatabaseBusyOrLocked(it) },
            onConstraintViolation = { DbFailure.DuplicateOrConstraintViolation(it) },
            onUnexpectedDatabaseError = { DbFailure.UnexpectedDatabaseError(it) }
        ) {
            throw SQLiteDatabaseLockedException("Database is locked")
        }
        assertTrue(result is DbFailure.DatabaseBusyOrLocked)
    }

    @Test
    fun tableLockedException_mapsToBusyOrLocked() {
        val result = EntityMappers.runCatchingSqlite(
            onBusyOrLocked = { DbFailure.DatabaseBusyOrLocked(it) },
            onConstraintViolation = { DbFailure.DuplicateOrConstraintViolation(it) },
            onUnexpectedDatabaseError = { DbFailure.UnexpectedDatabaseError(it) }
        ) {
            throw SQLiteTableLockedException("Table is locked")
        }
        assertTrue(result is DbFailure.DatabaseBusyOrLocked)
    }

    @Test
    fun constraintException_mapsToConstraintViolation() {
        val result = EntityMappers.runCatchingSqlite(
            onBusyOrLocked = { DbFailure.DatabaseBusyOrLocked(it) },
            onConstraintViolation = { DbFailure.DuplicateOrConstraintViolation(it) },
            onUnexpectedDatabaseError = { DbFailure.UnexpectedDatabaseError(it) }
        ) {
            throw SQLiteConstraintException("UNIQUE constraint failed")
        }
        assertTrue(result is DbFailure.DuplicateOrConstraintViolation)
    }

    @Test
    fun genericSqliteException_mapsToUnexpectedDatabaseError() {
        val result = EntityMappers.runCatchingSqlite(
            onBusyOrLocked = { DbFailure.DatabaseBusyOrLocked(it) },
            onConstraintViolation = { DbFailure.DuplicateOrConstraintViolation(it) },
            onUnexpectedDatabaseError = { DbFailure.UnexpectedDatabaseError(it) }
        ) {
            throw SQLiteException("Disk I/O error")
        }
        assertTrue(result is DbFailure.UnexpectedDatabaseError)
    }

    @Test
    fun cancellationException_isRethrownDirectly() {
        try {
            EntityMappers.runCatchingSqlite(
                onBusyOrLocked = { DbFailure.DatabaseBusyOrLocked(it) },
                onConstraintViolation = { DbFailure.DuplicateOrConstraintViolation(it) },
                onUnexpectedDatabaseError = { DbFailure.UnexpectedDatabaseError(it) }
            ) {
                throw CancellationException("Coroutine cancelled")
            }
            fail("CancellationException harus di-rethrow dan tidak boleh ditangkap")
        } catch (e: CancellationException) {
            assertEquals("Coroutine cancelled", e.message)
        }
    }
}
