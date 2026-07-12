package com.torecastop.ledger.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {

    @Insert
    suspend fun insert(session: Session): Long

    @Update
    suspend fun update(session: Session)

    /** The single active session, or null if none is open. */
    @Query("SELECT * FROM sessions WHERE status = :status LIMIT 1")
    suspend fun getActiveSession(status: String = Session.STATUS_ACTIVE): Session?

    /** Observe the active session so the UI reacts to open/close. */
    @Query("SELECT * FROM sessions WHERE status = :status LIMIT 1")
    fun observeActiveSession(status: String = Session.STATUS_ACTIVE): Flow<Session?>

    /** All sessions, newest first — for a future history screen. */
    @Query("SELECT * FROM sessions ORDER BY startTime DESC")
    fun observeAllSessions(): Flow<List<Session>>

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun getById(id: Long): Session?
}
