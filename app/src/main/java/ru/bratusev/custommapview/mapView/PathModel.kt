package ru.bratusev.custommapview.mapView

data class PathModel(
    var x: Float,
    var y: Float,
    var level: Int,
    var id: Int,
){
    override fun toString(): String {
        return "$x $y $level $id"
    }
}