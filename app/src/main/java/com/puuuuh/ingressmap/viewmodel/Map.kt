package com.puuuuh.ingressmap.viewmodel

import android.content.Context
import android.net.Uri
import android.os.Handler
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.PolylineOptions
import com.google.common.geometry.S2CellId
import com.puuuuh.ingressmap.MainApplication
import com.puuuuh.ingressmap.model.*
import com.puuuuh.ingressmap.repository.*
import com.puuuuh.ingressmap.settings.FullPosition
import com.puuuuh.ingressmap.settings.Settings
import com.puuuuh.ingressmap.utils.area
import com.puuuuh.ingressmap.utils.intersects
import com.puuuuh.ingressmap.utils.throttleLatest
import com.puuuuh.ingressmap.utils.toLatLng
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.nio.charset.Charset
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.collections.HashMap
import kotlin.math.*


data class Status(val requestsInProgress: Int)

private fun getXYTile(lat : Double, lng: Double, zoom : Int) : Pair<Int, Int> {
    val tileCounts = arrayOf(1,1,1,40,40,80,80,320,1000,2000,2000,4000,8000,16000,16000,32000)
    val latRad = Math.toRadians(lat)

    val cnt = if (zoom >= tileCounts.size) {tileCounts[tileCounts.size - 1]} else {tileCounts[zoom]}
    val x = (((lng + 180.0) / 360.0) * cnt).toInt()
    val y = ((1.0 - log(tan(latRad) + (1 / cos(latRad)), E) / PI) / 2.0 * cnt).toInt()
    return Pair(x, y)
}

class MapViewModel(val context: Context) : ViewModel(), OnDataReadyCallback, OnCellsReadyCallback {
    private val ingressRepo = IngressApiRepo()
    private val customPointRepo = CustomPointsRepo()
    private val cellsRepo = S2CellsRepo()
    private val handler = Handler(context.mainLooper)
    private val localPortals = ConcurrentHashMap<String, GameEntity.Portal>()
    private val localLinks = ConcurrentHashMap<String, GameEntity.Link>()
    private val localFields = ConcurrentHashMap<String, GameEntity.Field>()
    private val seq = AtomicInteger(0)
    private val throttleUpdate = throttleLatest<Unit>(200, GlobalScope) {
        _portals.postValue(localPortals)

        _links.postValue(localLinks)

        _fields.postValue(localFields)
    }

    // Current viewport
    private var viewport = LatLngBounds(LatLng(0.0, 0.0), LatLng(0.0, 0.0))
    private var zoom = 21

    private val _targetPosition = MutableLiveData<FullPosition>()
    val targetPosition: LiveData<FullPosition> = _targetPosition

    private val _portals = MutableLiveData<Map<String, GameEntity.Portal>>()
    val portals: LiveData<Map<String, GameEntity.Portal>> = _portals

    private val _cellLines = MutableLiveData<Map<S2CellId, PolylineOptions>>()
    val cellLines: LiveData<Map<S2CellId, PolylineOptions>> = _cellLines

    private val _links = MutableLiveData<Map<String, GameEntity.Link>>()
    val links: LiveData<Map<String, GameEntity.Link>> = _links

    private val _fields = MutableLiveData<Map<String, GameEntity.Field>>()
    val fields: LiveData<Map<String, GameEntity.Field>> = _fields

    private val _customFields = MutableLiveData<Map<String, List<LatLng>>>()
    val customFields: LiveData<Map<String, List<LatLng>>> = _customFields

    private val _status = MutableLiveData<Status>()
    val status: LiveData<Status> = _status

    private val _customPointLinks = mutableMapOf<LatLng, MutableList<LatLng>>()

    private val _customLines = MutableLiveData<Map<String, PolylineOptions>>()
    val customLines: LiveData<Map<String, PolylineOptions>> = _customLines

    private val _selectedPoint = MutableLiveData<LatLng?>()
    val selectedPoint: LiveData<LatLng?> = _selectedPoint

    private val _selectedPortal = MutableLiveData<GameEntity.Portal?>()
    val selectedPortal: LiveData<GameEntity.Portal?> = _selectedPortal

    private val _playerInfo = MutableLiveData<PlayerInfo>()
    val playerInfo: LiveData<PlayerInfo> = _playerInfo

    private val zoomToLevel = arrayOf(8, 8, 8, 8, 7, 7, 6, 6, 5, 4, 4, 3, 2, 1, 1, 1, 1, 1, 1, 1)

    private val zoomToArea = arrayOf(
        10000000000000.0,
        10000000000000.0,
        10000000000000.0,
        10000000000000.0,
        10000000000000.0,
        10000000000000.0,
        10000000000000.0,
        1000000000000.0,
        100000000000.0,
        10000000000.0,
        1000000000.0,
        100000000.0,
        10000000.0,
        1000000.0,
        100000.0,
    )

    init {
        _customLines.value = emptyMap()
        val links = customPointRepo.getAll()
        links.observeForever { list ->
            list.map {
                addCustomLink(
                    it.id,
                    Pair(it.data.points.first.toLatLng(), it.data.points.second.toLatLng())
                )
            }
        }
    }

    fun updatePlayerInfo() {
        ingressRepo.getPlayerInfo(object : OnPlayerInfoReadyCallback {
            override fun onPlayerInfoReady(info: PlayerInfo) {
                _playerInfo.postValue(info)
            }
        })
    }

    fun updatePosition(pos: LatLng, r: LatLngBounds, z: Int) {
        Settings.lastPosition = FullPosition(pos.latitude, pos.longitude, z.toFloat())

        viewport = r
        zoom = z

        GlobalScope.launch {
            seq.incrementAndGet()
            localPortals.clear()
            localLinks.clear()
            localFields.clear()
            updateCellsInRegion(r, z)
            if (Settings.showCells) {
                cellsRepo.getCells(r, z, this@MapViewModel)
            } else if (cellLines.value?.isNotEmpty() != false) {
                _cellLines.postValue(emptyMap())
            }
        }
    }

    fun moveCamera(r: FullPosition) {
        _targetPosition.postValue(r)
    }

    fun selectPortal(value: GameEntity.Portal?) {
        if (Settings.drawMode && value != null) {
            addCustomPoint(LatLng(value.data.lat, value.data.lng))
        } else {
            _selectedPortal.value = value
        }
    }

    private fun addCustomPoint(point: LatLng) {
        val prev = this._selectedPoint.value
        if (prev == null) {
            this._selectedPoint.value = point
        } else {
            this._selectedPoint.value = null
            val link = addCustomLink(UUID.randomUUID().toString(), Pair(prev, point))
            if (link != null) {
                customPointRepo.add(link)
            }
        }
    }

    private fun addCustomLink(id: String, points: Pair<LatLng, LatLng>): GameEntity.Link? {
        if (points.first == points.second || _customPointLinks[points.first]?.contains(points.second) == true) {
            return null
        }
        var prevLines = this._customLines.value
        if (prevLines == null) {
            prevLines = emptyMap()
        }
        val poly = PolylineOptions().add(points.first).add(points.second)
        val newLines = HashMap(prevLines)
        newLines[id] = poly
        this._customLines.value = newLines
        findNewFields(points.first, points.second)
        if (_customPointLinks[points.first] == null) {
            _customPointLinks[points.first] = mutableListOf(points.second)
        } else {
            _customPointLinks[points.first]!!.add(points.second)
        }
        if (_customPointLinks[points.second] == null) {
            _customPointLinks[points.second] = mutableListOf(points.first)
        } else {
            _customPointLinks[points.second]!!.add(points.first)
        }
        val pointList = poly.points.map {
            Point(it.latitude, it.longitude)
        }
        return GameEntity.Link(id, LinkData("C", Pair(pointList[0], pointList[1])))
    }

    private fun addCustomField(points: List<LatLng>) {
        val prev = _customFields.value
        _customFields.value = (if (prev == null) {
            mapOf(UUID.randomUUID().toString() to points)
        } else {
            prev + (UUID.randomUUID().toString() to points)
        })
    }

    private fun findNewFields(p1: LatLng, p2: LatLng) {
        val p1Links = _customPointLinks[p1]
        val p2Links = _customPointLinks[p2]
        if (p1Links != null && p2Links != null) {
            val p3List = p1Links.intersect(p2Links)
            p3List.map {
                addCustomField(listOf(p1, p2, it))
            }
        }
    }

    fun removeCustomLine(id: String) {
        val l = this._customLines.value?.get(id) ?: return
        customPointRepo.delete(id)
        var prevLines = this._customLines.value
        if (prevLines == null) {
            prevLines = emptyMap()
        }
        this._customLines.value = prevLines.filter {
            it.key != id
        }
        this._customPointLinks[l.points[0]]?.removeIf {
            it == l.points[1]
        }
        this._customPointLinks[l.points[1]]?.removeIf {
            it == l.points[0]
        }
        this._customFields.value = this._customFields.value?.filter {
            !it.value.containsAll(l.points)
        }.orEmpty()
    }

    override fun onCellDataReceived(
        seq: Int,
        cellId: String,
        portals: Map<String, GameEntity.Portal>,
        links: Map<String, GameEntity.Link>,
        fields: Map<String, GameEntity.Field>
    ) {
        if (seq != this.seq.get()) {
            return
        }
        var changes = 0
        val level = if (zoom >= zoomToLevel.size) {
            1
        } else {
            zoomToLevel[zoom]
        }

        val area: Double = if (zoom >= zoomToArea.size) {
            0.0
        } else {
            zoomToArea[zoom]
        }

        if (Settings.showPortals) {
            val uniquePortals = portals.filter {
                viewport.contains(
                    LatLng(
                        it.value.data.lat,
                        it.value.data.lng
                    )
                ) && !localPortals.containsKey(it.key) && it.value.data.lvl >= level
            }
            changes += uniquePortals.count()
            localPortals.putAll(uniquePortals)
        }
        if (Settings.showLinks) {
            val uniqueLinks = links.filter {
                it.value.data.bounds.intersects(viewport) && !localLinks.containsKey(it.key)
            }
            changes += uniqueLinks.count()
            localLinks.putAll(uniqueLinks)
        }

        if (Settings.showFields) {
            val uniqueFields = fields.filter {
                val curArea = it.value.data.bounds.area()
                it.value.data.bounds.intersects(viewport) &&
                        !localFields.containsKey(it.key) &&
                        curArea >= area
            }
            changes += uniqueFields.count()
            localFields.putAll(uniqueFields)
        }

        throttleUpdate(Unit)
    }

    override fun onRequestStart() {
        handler.post {
            _status.value = Status((_status.value?.requestsInProgress ?: 0) + 1)
        }
    }

    override fun onRequestEnd() {
        handler.post {
            _status.value = Status((_status.value?.requestsInProgress ?: 0) - 1 )
        }
    }

    override fun onCellsReady(data: Map<S2CellId, PolylineOptions>) {
        synchronized(_cellLines) {
            _cellLines.postValue(
                if (Settings.showCells) {
                    data
                } else {
                    mapOf()
                }
            )
        }
    }

    private fun updateCellsInRegion(region: LatLngBounds, zoom: Int) {
        val targetZoom = zoom
        val level = if (zoom >= zoomToLevel.size) {
            0
        } else {
            zoomToLevel[zoom]
        }
        val ne = getXYTile(
            region.northeast.latitude,
            region.northeast.longitude,
            targetZoom
        )
        val sw = getXYTile(
            region.southwest.latitude,
            region.southwest.longitude,
            targetZoom
        )
        val tiles = mutableListOf<String>()
        for (y in ne.second..sw.second) {
            for (x in sw.first..ne.first) {
                tiles.add("${targetZoom}_${x}_${y}_${level}_8_100")
            }
        }
        val seq = seq.get()
        tiles.withIndex()
            .groupBy { it.index / 20 }
            .map { it ->
                ingressRepo.getTilesInfo(seq, it.value.map { it.value }, this)
            }
    }

    fun saveLinks(path: Uri) {
        val data = _customLines.value
        val file = LinkFile()
        data?.forEach {
            file.add(
                LinkFileItem(
                    String.format("#%06x", it.value.color.and(0xFFFFFF)),
                    it.value.points.map {
                        com.puuuuh.ingressmap.model.LatLng(it.latitude, it.longitude)
                    }, "polyline"
                )
            )
        }

        val out = context.contentResolver.openOutputStream(path)
        out?.write(MainApplication.gson.toJson(file).toByteArray())
        out?.close()
    }

    fun loadLinks(path: Uri) {
        val input = context.contentResolver.openInputStream(path)

        if (input != null) {
            val data = MainApplication.gson.fromJson(
                input.readBytes().toString(Charset.defaultCharset()),
                LinkFile::class.java
            )
            data.forEach {
                var prev: LatLng? = null
                it.latLngs.forEach {
                    val p = LatLng(it.lat, it.lng)

                    if (prev != null) {
                        val link = addCustomLink(UUID.randomUUID().toString(), Pair(prev!!, p))
                        if (link != null) {
                            customPointRepo.add(link)
                        }
                    }

                    prev = p
                }
            }

        }
        input?.close()
    }
}
