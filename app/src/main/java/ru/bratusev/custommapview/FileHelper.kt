/**
 * Класс для работы с файловой системой
 * @Author Братусев Денис
 * @Since 01.06.2023
 * @Version 1.0
 * */
package ru.bratusev.custommapview

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import net.lingala.zip4j.ZipFile
import java.io.File

/**
 * Класс для работы с файловой системой
 * @Param [context] для работы с сервисами
 * @Param [locationName] название локации для подгрузки
 * @Constructor Создаёт FileHelper для работы с системой
 */
class FileHelper(private val context: Context, private val fragment: MainActivity, val locationName: String) {

    private val SDPath =
        Environment.getExternalStorageDirectory().absolutePath + "/Android/data/ru.bratusev.hostesnavigation"
    internal val dataPath = "$SDPath/files/locations/"
    internal val unzipPath = "$SDPath/files/locations/"

    private var dataPathTmp = dataPath
    private var unzipPathTmp = unzipPath

    internal fun fileDownload(uRl: String) {
        dataPathTmp += "$locationName/"
        unzipPathTmp += "$locationName/"
        if(!checkStorageLocation()){
            val request = DownloadManager.Request(Uri.parse(convertUrl(uRl)))
                .setTitle("$locationName.zip")
                .setDescription("Downloading")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(
                    context,
                    "locations/$locationName",
                    "$locationName.zip"
                )
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadManager.enqueue(request)

            val intentFilter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
            context.registerReceiver(onComplete, intentFilter)
        }
    }

    private val onComplete = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if(unzip(locationName) == true){ }
        }
    }

    private fun convertUrl(id: String): String {
        return "https://drive.google.com/uc?export=download&confirm=no_antivirus&id=$id"
    }

    internal fun getJsonMap(name: String): String {
        if (checkStorageLocation()) {
            return try {
                return File("$dataPath$name/map.json").readText()
            } catch (e: Exception) {
                e.message.toString()
            }
        } else {
            //TODO: Добавить обработку отсутствия карты
            //fileDownload("1rq4aFmBEvLCAhXTQ3YPbtaHkoc2_8B8v")
        }
        return "empty location"
    }

    private fun unzip(fileName: String): Boolean? {
        return try {
            val zipFile = ZipFile("$dataPathTmp$fileName.zip")
            zipFile.extractAll(unzipPathTmp)
            File("$dataPathTmp$fileName.zip").delete()
            Toast.makeText(context, "Установка успешна", Toast.LENGTH_SHORT).show()
            true
        } catch (e: Exception) {
            false
        }
    }

    internal fun getLevelCount(fileName: String): Int {
        val directory = File("$unzipPath$locationName/$fileName")
        val files = directory.listFiles()
        return files?.size ?: 0
    }

    private fun checkStorageLocation(): Boolean {
        try {
            for (file in File(unzipPath).listFiles()!!) {
                if (file.name == locationName) return true
            }
            Toast.makeText(context, "Локация не найдена", Toast.LENGTH_SHORT).show()
        }catch (e: Exception){
        }
        return false
    }
}