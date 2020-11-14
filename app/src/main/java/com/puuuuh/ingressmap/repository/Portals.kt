package com.puuuuh.ingressmap.repository

import androidx.room.*
import com.puuuuh.ingressmap.MainApplication

@Fts4(contentEntity = PortalDto::class, tokenizer = FtsOptions.TOKENIZER_UNICODE61)
@Entity(tableName = "portalFTS")
class PortalsFts(val id: String, val title: String)

@Entity
data class PortalDto(
    @PrimaryKey val id: String,
    val title: String,
    val lat: Double,
    val lng: Double,
)

@Dao
interface PortalsDao {
    @Query("SELECT * FROM portalDTO JOIN portalFTS ON portalFTS.id = portalDTO.id WHERE portalFTS.title MATCH :text GROUP BY portalFTS.id")
    fun searchPortals(text: String): List<PortalDto>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(vararg portals: PortalDto)

    @Query("DELETE FROM portaldto WHERE id = :id")
    fun delete(id: String)
}

@Database(entities = [PortalDto::class, PortalsFts::class], version = 1)
abstract class PortalsDatabase : RoomDatabase() {
    abstract fun portalsDao(): PortalsDao
}

class PortalsRepo {
    companion object {
        val db = Room.databaseBuilder(
            MainApplication.applicationContext(),
            PortalsDatabase::class.java, "portals"
        ).build()
    }

    fun find(query: String): List<PortalDto> {
        return db.portalsDao().searchPortals(query)
    }

    @Transaction
    fun add(query: PortalDto) {
        db.portalsDao().insert(query)
    }

    @Transaction
    fun delete(id: String) {
        db.portalsDao().delete(id)
    }
}