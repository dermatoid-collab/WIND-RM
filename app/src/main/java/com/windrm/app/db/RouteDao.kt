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

    @Query("SELECT * FROM routes WHERE stravaRouteId = :stravaRouteId LIMIT 1")
    suspend fun getByStravaRouteId(stravaRouteId: Long): RouteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(route: RouteEntity): Long

    @Delete
    suspend fun delete(route: RouteEntity)

    @Query("UPDATE routes SET isFavorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    @Query("UPDATE routes SET stopsJson = :stopsJson WHERE id = :id")
    suspend fun setStops(id: Long, stopsJson: String)

    @Query("UPDATE routes SET activity = :activity WHERE id = :id")
    suspend fun setActivity(id: Long, activity: String)

    @Query("DELETE FROM routes WHERE id = :id")
    suspend fun deleteById(id: Long)
}
