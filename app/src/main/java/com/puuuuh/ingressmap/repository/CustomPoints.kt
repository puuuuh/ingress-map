package com.puuuuh.ingressmap.repository

import androidx.lifecycle.LiveData
import androidx.lifecycle.Transformations
import androidx.room.*
import com.puuuuh.ingressmap.MainApplication
import com.puuuuh.ingressmap.model.GameEntity
import com.puuuuh.ingressmap.model.LinkData
import com.puuuuh.ingressmap.model.Point
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

@Entity
data class CustomLink(
    @PrimaryKey val id: String,
    val lat1: Double,
    val lng1: Double,
    val lat2: Double,
    val lng2: Double
)

@Dao
interface LinksDao {
    @Query("SELECT * FROM customlink")
    fun getAll(): LiveData<List<CustomLink>>

    @Insert
    fun insertAll(vararg links: CustomLink)

    @Query("DELETE FROM customlink WHERE id = :id")
    fun delete(id: String)
}

@Database(entities = [CustomLink::class], version = 1)
abstract class CustomLinksDatabase : RoomDatabase() {
    abstract fun linksDao(): LinksDao
}

class CustomPointsRepo {
    companion object {
        val db = Room.databaseBuilder(
            MainApplication.applicationContext(),
            CustomLinksDatabase::class.java, "custom-points"
        ).build()
    }

    fun getAll(): LiveData<List<GameEntity.Link>> {
        return Transformations.map(db.linksDao().getAll()) {
            it.map {
                GameEntity.Link(
                    it.id,
                    LinkData(
                        "C",
                        Pair(Point(it.lat1, it.lng1), Point(it.lat2, it.lng2))
                    )
                )
            }

        }
    }

    fun add(l: GameEntity.Link) {
        GlobalScope.launch {
            val dto = CustomLink(
                l.id,
                l.data.points.first.lat,
                l.data.points.first.lng,
                l.data.points.second.lat,
                l.data.points.second.lng,
            )
            db.linksDao().insertAll(dto)
        }
    }

    fun delete(l: String) {
        GlobalScope.launch {
            db.linksDao().delete(l)
        }
    }
}