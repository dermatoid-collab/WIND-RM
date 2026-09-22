package com.windrm.app.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RouteDao {
    @Query("SELECT * FROM routes ORDER BY createdAtEpochMs DESC")
    fun observeAll(): Flow<List<RouteEntity>>

    @Query("SELECT * FROM routes WHERE id = :id")
    suspend fun getById(id: Long): RouteEntity?

    @Query("SELECT * FROM routes WHERE stravaActivityId = :stravaActivityId LIMIT 1")
    suspend fun getByStravaActivityId(stravaActivityId: Long): RouteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(route: RouteEntity): Long

    @Delete
    suspend fun delete(route: RouteEntity)

    @Query("DELETE FROM routes WHERE id = :id")
    suspend fun deleteById(id: Long)
}
