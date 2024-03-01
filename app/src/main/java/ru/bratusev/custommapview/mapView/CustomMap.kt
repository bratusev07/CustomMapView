package ru.bratusev.custommapview.mapView

import android.content.Context
import android.graphics.Paint
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnClickListener
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.NumberPicker
import android.widget.NumberPicker.OnValueChangeListener
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.ContextCompat
import ovh.plrapps.mapview.MapView
import ovh.plrapps.mapview.MapViewConfiguration
import ovh.plrapps.mapview.ReferentialData
import ovh.plrapps.mapview.ReferentialListener
import ovh.plrapps.mapview.api.addMarker
import ovh.plrapps.mapview.api.moveMarker
import ovh.plrapps.mapview.api.moveToMarker
import ovh.plrapps.mapview.api.removeMarker
import ovh.plrapps.mapview.paths.PathView
import ovh.plrapps.mapview.paths.addPathView
import ovh.plrapps.mapview.paths.removePathView
import ru.bratusev.custommapview.R
import kotlin.math.atan

class CustomMap(private val context: Context, attrs: AttributeSet) :
    FrameLayout(context, attrs),
    OnValueChangeListener, OnClickListener {

    private val numberPicker: NumberPicker
    private val plusButton: ImageView
    private val minusButton: ImageView
    private val positionButton: ImageView
    private var mapView: MapView
    private var listener: CustomViewListener? = null

    private val finishMarker = AppCompatImageView(context).apply {
        setImageResource(R.drawable.finish_marker)
    }
    private val startMarker = AppCompatImageView(context).apply {
        setImageResource(R.drawable.position_marker)
    }
    private val centerMarker = AppCompatImageView(context).apply {
        setImageResource(R.drawable.position_marker)
    }
    private val strokePaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.brand)
        strokeCap = Paint.Cap.ROUND
    }

    private var pathView: PathView
    private var newScale = 0f
    private var positionRotation = 0f
    private var markerList = ArrayList<MapMarker>()
    private var dotList = ArrayList<Dot>()
    private var levelNumber: Int = 1
    private var startNode = 0
    private var finishNode = 0
    private lateinit var mapData: MapData
    private var minPathWidth = 0f
    private var maxScale = 0f
    private var minScale = 0f
    private var maxPathWidth = 0f

    init {
        val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        inflater.inflate(R.layout.map_layout, this, true)

        numberPicker = findViewById(R.id.picker)
        plusButton = findViewById(R.id.btn_zoomIn)
        minusButton = findViewById(R.id.btn_zoomOut)
        positionButton = findViewById(R.id.btn_position)
        mapView = findViewById(R.id.mapView)
        pathView = PathView(context)

        setChangeListeners()
    }

    private fun setLevelNumber(level: String) {
        numberPicker.value = mapData.levelArray.size + 1 - level.toInt()
        levelNumber = level.toInt()
    }

    internal fun setMap(mapData: MapData, needDestroy: Boolean = false, levelNumber: String = "1") {
        if (needDestroy) destroyMapView()
        val config = MapViewConfiguration(
            levelCount = mapData.zoomLevelCount,
            fullWidth = mapData.fullWidth,
            fullHeight = mapData.fullHeight,
            tileSize = mapData.tileSize,
            tileStreamProvider = mapData.tileStreamProvider
        ).setMaxScale(mapData.setMaxScale).enableRotation()
        fillMapData(mapData)
        configureLevelPicker()
        setLevelNumber(levelNumber)
        mapView.configure(config)
        mapView.defineBounds(0.0, 0.0, mapData.fullWidth.toDouble(), mapData.fullHeight.toDouble())
        mapView.addReferentialListener(refOwner)
        addFinishMarker()
        addStartMarker()
        updatePath()
    }

    private fun setStartPosition(start: Int) {
        startNode = start
    }

    private fun setFinishPosition(finish: Int) {
        finishNode = finish
        updatePath()
    }

    private fun moveStartMarker() {
        for (marker in markerList) {
            if (marker.name == startNode.toString()) {
                startMarker.visibility = View.VISIBLE
                mapView.moveMarker(startMarker, marker.x, marker.y)
                break
            }
        }
    }

    private fun moveFinishMarker() {
        for (marker in markerList) {
            if (marker.name == finishNode.toString()) {
                finishMarker.visibility = View.VISIBLE
                mapView.moveMarker(finishMarker, marker.x, marker.y)
                break
            }
        }
    }

    internal fun removePath(needFullDrop: Boolean = false): Int {
        mapView.removePathView(pathView)
        mapView.removeView(pathView)
        mapView.removeMarker(startMarker)
        mapView.removeMarker(finishMarker)
        val parent = mapView.parent as ViewGroup
        parent.removeView(startMarker)
        parent.removeView(finishMarker)
        if(needFullDrop){
            startNode = 0
            finishNode = 0
        }
        return parent.indexOfChild(mapView)
    }

    internal fun setListener(customViewListener: CustomViewListener) {
        this.listener = customViewListener
    }

    private fun destroyMapView() {
        val parent = mapView.parent as ViewGroup
        val index = removePath()
        parent.removeView(mapView)
        mapView = MapView(context)
        parent.addView(mapView, index)
    }

    private fun setChangeListeners() {
        numberPicker.setOnValueChangedListener(this)
        plusButton.setOnClickListener(this)
        minusButton.setOnClickListener(this)
        positionButton.setOnClickListener(this)
    }

    private fun configureLevelPicker() {
        try {
            val levelArray = mapData.levelArray
            numberPicker.wrapSelectorWheel = false
            initPickerWithString(1, levelArray.size, numberPicker, levelArray.toTypedArray())
        } catch (_: Exception) {
        }
    }

    private fun initPickerWithString(min: Int, max: Int, p: NumberPicker, levels: Array<String>) {
        p.minValue = min
        p.maxValue = max
        p.value = max
        p.displayedValues = levels
    }

    override fun onValueChange(picker: NumberPicker?, oldVal: Int, newVal: Int) {
        levelNumber = mapData.levelArray[picker?.value!! - 1].toInt()
        listener?.onLevelChanged(levelNumber.toString())
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.btn_zoomIn -> zoomIn()
            R.id.btn_zoomOut -> zoomOut()
            R.id.btn_position -> moveToMe()
        }
    }

    private fun zoomIn() {
        newScale += (maxScale - minScale) / mapData.zoomLevelCount
        if (newScale > maxScale) newScale = maxScale
        mapView.smoothScaleFromFocalPoint(mapView.width / 2, mapView.height / 2, newScale)
    }

    private fun zoomOut() {
        newScale -= (maxScale - minScale) / mapData.zoomLevelCount
        if (newScale < minScale) newScale = minScale
        mapView.smoothScaleFromFocalPoint(mapView.width / 2, mapView.height / 2, newScale)
    }

    private fun moveToMe() {
        mapView.scale = 1.3f
        newScale = 1.3f
        try {
            mapView.moveToMarker(startMarker, true)
        } catch (_: Exception) {
        }
    }

    private fun fillMapData(mapData: MapData) {
        this.mapData = mapData
        markerList = mapData.markerList
        dotList = mapData.dotList
        minPathWidth = mapData.minPathWidth
        maxScale = mapData.maxScale
        minScale = mapData.minScale
        maxPathWidth = mapData.maxPathWidth
    }

    private val refOwner = object : ReferentialListener {
        var referentialData: ReferentialData? = null

        override fun onReferentialChanged(refData: ReferentialData) {
            setMarkerScale(refData.scale)
            setPositionMarkerRotation(refData.angle)
            referentialData = refData
            for (mapMarker in markerList) {
                setMarkerScale(refData.scale, mapMarker)
            }
            newScale = refData.scale
        }
    }

    private fun addFinishMarker() {
        finishMarker.visibility = View.INVISIBLE
        mapView.addMarker(finishMarker, 0.0, 0.0, -0.5f, -0.5f, tag = "finish")
    }

    private fun addStartMarker() {
        startMarker.visibility = View.INVISIBLE
        mapView.addMarker(startMarker, 0.0, 0.0, -0.5f, -0.5f, tag = "start")
    }

    private fun setMarkerScale(scale: Float) {
        startMarker.scaleX = scale + 1f
        startMarker.scaleY = scale + 1f
        finishMarker.scaleX = scale + 1f
        finishMarker.scaleY = scale + 1f
    }

    private fun setPositionMarkerRotation(angle: Float) {
        startMarker.rotation = angle + positionRotation
    }

    private fun setMarkerScale(scale: Float, mapMarker: MapMarker) {
        val mapMax = 2.0
        val mapMin = 0.3
        val markerMin = 0
        val markerMax = 3
        var tmp =
            (((scale - mapMin) / (mapMax - mapMin)) * (markerMax - markerMin) + markerMin).toFloat()
        tmp = if (tmp > 0) tmp else 0.0F
        mapMarker.scaleX = tmp
        mapMarker.scaleY = tmp
    }

    private fun updatePath() {
        if (finishNode == startNode) return

        moveStartMarker()
        moveFinishMarker()
        var path = calculatePath(startNode, finishNode)
        if(path.size < 2) path = calculatePath(startNode, finishNode)
        val drawablePath = object : PathView.DrawablePath {
            override val visible: Boolean = true
            override var path: FloatArray = path
            override var paint: Paint? = strokePaint
            override val width: Float = calculatePathWidth()
        }

        pathView.updatePaths(listOf(drawablePath))
        try {
            mapView.removeView(pathView)
        } catch (_: Exception) {
        }
        mapView.addPathView(pathView)

    }

    private fun calculateAngel(x1: Int, y1: Int, x2: Int, y2: Int): Float {
        var angel = 90.0f
        val tmp = (atan(((y2 - y1).toDouble() / (x2 - x1).toDouble())) * 180 / Math.PI).toFloat()
        angel += if (x2 - x1 >= 0) tmp
        else tmp + 180
        return angel
    }

    private fun calculatePath(start: Int, finish: Int): FloatArray {
        val navigation = Navigation(Map(mapData = mapData))
        val path = navigation.path(start, finish)
        var i = 0
        var size = 0
        for (pathModel in path) {
            if (pathModel.level == levelNumber) {
                size++
            }
        }
        var isFirstOnLevel = true
        val pathList = try {
            FloatArray((size-1) * 2)
        }catch (_ : Exception){ FloatArray(0) }

        for (pathModel in path) {
            if (pathModel.level == levelNumber) {
                if (!isFirstOnLevel) {
                    pathList[i] = pathModel.x
                    pathList[i + 1] = pathModel.y
                    i += 2
                }else{
                    isFirstOnLevel = false
                }

            }
        }
        changeMarkerVisibility()
        if (pathList.size > 3) {
            val x1 = pathList[0].toInt()
            val y1 = pathList[1].toInt()
            val x2 = pathList[2].toInt()
            val y2 = pathList[3].toInt()
            positionRotation = calculateAngel(x1, y1, x2, y2)
            setPositionMarkerRotation(refOwner.referentialData?.angle ?: 0f)
        }
        return pathList
    }

    private fun changeMarkerVisibility() {
        var myStartMarker = Dot(0f, 0f)
        var myFinishMarker = Dot(0f, 0f)
        for (marker in dotList) {
            if (marker.getId() == startNode) myStartMarker = marker
            if (marker.getId() == finishNode) myFinishMarker = marker
        }

        if (levelNumber == myStartMarker.getLevel()) startMarker.visibility = View.VISIBLE
        else startMarker.visibility = View.INVISIBLE
        if (levelNumber == myFinishMarker.getLevel()) finishMarker.visibility = View.VISIBLE
        else finishMarker.visibility = View.INVISIBLE
    }

    private fun calculatePathWidth(): Float {
        var temp = minPathWidth + (maxPathWidth - minPathWidth) * newScale / maxScale
        if (newScale == minScale) temp = minPathWidth
        else if (newScale == maxScale) temp = maxPathWidth
        return temp
    }

    fun drawPath(startPosition: Int, finishPosition: Int) {
        setStartPosition(startPosition)
        setFinishPosition(finishPosition)
    }
}