package ru.bratusev.custommapview

import android.annotation.SuppressLint
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import org.json.JSONTokener
import ovh.plrapps.mapview.core.TileStreamProvider
import ru.bratusev.custommapview.mapView.CustomMap
import ru.bratusev.custommapview.mapView.CustomViewListener
import ru.bratusev.custommapview.mapView.Dot
import ru.bratusev.custommapview.mapView.MapData
import ru.bratusev.custommapview.mapView.MapMarker

class MainActivity : AppCompatActivity(), CustomViewListener {

    private var levelNumber = "1"
    private lateinit var mapView: CustomMap
    private lateinit var mapData: MapData

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        mapView = findViewById(R.id.customMap)
        mapView.setListener(this)

        val list = loadFromString()

        mapData = MapData(
            tileStreamProvider = getTileStream(),
            levelArray = ArrayList<String>().also {
                it.add("2")
                it.add("1")
            },
            fullWidth = 3840,
            fullHeight = 2160,
            tileSize = 256,
            setMaxScale = 4f,
            markerList = parseFromDot(list),
            dotList = list,
            minPathWidth = 10f,
            maxPathWidth = 50f,
            minScale = 0f,
            maxScale = 2f,
            zoomLevelCount = 5
        )
        mapView.setMap(mapData = mapData)

        findViewById<Button>(R.id.finishBtn).setOnClickListener {
            mapView.drawPath(10, 138)
        }
        findViewById<Button>(R.id.removeBtn).setOnClickListener {
            mapView.removePath(true)
        }
    }

    private fun parseFromDot(list: ArrayList<Dot>): ArrayList<MapMarker> {
        val mapMarkerList = ArrayList<MapMarker>()

        for (dot in list) {
            mapMarkerList.add(
                MapMarker(
                    applicationContext, dot.getX().toDouble(),
                    dot.getY().toDouble(), dot.getId().toString()
                )
            )
        }

        return mapMarkerList;
    }

    private fun loadFromString(): ArrayList<Dot> {
        val jsonString = assets.open("map.json").bufferedReader().use { it.readText() }
        val dotList = ArrayList<Dot>()
        val map = JSONTokener(jsonString).nextValue() as JSONObject
        val jsonDots = map.getJSONArray("dots")
        var i = -1
        while (++i < jsonDots.length()) {
            val jsonDot = jsonDots.getJSONObject(i)
            val dot = Dot(jsonDot.getDouble("x").toFloat(), jsonDot.getDouble("y").toFloat())
            dot.setLevel(jsonDot.getInt("floor"))
            dot.setMac(jsonDot.getString("mac"))
            dot.setName(jsonDot.getString("name"))
            dot.setDescription(jsonDot.getString("description"))
            dot.setType(jsonDot.getString("type"))
            dot.setPhotos(jsonDot.getJSONArray("photoUrls"))
            dot.setId(jsonDot.getInt("id"))
            dot.setConnected(jsonDot.getJSONArray("connected"))
            dotList.add(dot)
        }
        return dotList
    }

    private fun configureMap() {
        mapData.tileStreamProvider = getTileStream()
        mapView.setMap(mapData = mapData, true, levelNumber = levelNumber)
    }

    private fun getTileStream(): TileStreamProvider {
        return TileStreamProvider { row, col, zoomLvl ->
            try {
                assets.open("tiles$levelNumber/$zoomLvl/$row/$col.jpg")
            } catch (e: Exception) {
                assets.open("tiles$levelNumber/blank.png")
            }
        }
    }

    override fun onLevelChanged(newValue: String) {
        levelNumber = newValue
        configureMap()
    }
}