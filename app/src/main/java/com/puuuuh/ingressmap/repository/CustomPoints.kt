package com.puuuuh.ingressmap.repository

import androidx.lifecycle.LiveData
import androidx.lifecycle.Transformations
import androidx.room.*
import com.google.android.gms.maps.model.LatLng
import com.puuuuh.ingressmap.MainApplication
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
    fun insertAll(vararg users: CustomLink)

    @Query("DELETE FROM customlink WHERE id = :id")
    fun delete(id: String)
}

@Database(entities = [CustomLink::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun linksDao(): LinksDao
}

class CustomPointsRepo {
    companion object {
        val db = Room.databaseBuilder(
            MainApplication.applicationContext(),
            AppDatabase::class.java, "custom-points"
        ).build()
    }

    fun getAll(): LiveData<List<Link>> {
        return Transformations.map(db.linksDao().getAll()) {
            it.map {
                Link(
                    it.id,
                    "C",
                    arrayOf(Point(LatLng(it.lat1, it.lng1)), Point(LatLng(it.lat2, it.lng2)))
                )
            }

        }
    }

    fun add(l: Link) {
        GlobalScope.launch {
            val dto = CustomLink(
                l.id,
                l.points[0].LatLng.latitude,
                l.points[0].LatLng.longitude,
                l.points[1].LatLng.latitude,
                l.points[1].LatLng.longitude
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