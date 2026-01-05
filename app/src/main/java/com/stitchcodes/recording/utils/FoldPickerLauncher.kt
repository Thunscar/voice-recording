package com.stitchcodes.recording.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.ActivityResultCaller
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.stitchcodes.recording.ContextHolder
import org.greenrobot.eventbus.EventBus
import java.io.*

object FoldPickerLauncher {

    private const val WRITE_PATH_KEY = "write_path"
    private lateinit var foldPickerLauncher: ActivityResultLauncher<Uri?>
    private lateinit var writeUri: Uri

    fun init(activity: Activity, caller: ActivityResultCaller) {
        val savePath = getPathFromPrefs()
        if (savePath != null) {
            writeUri = savePath
            EventBus.getDefault().post(StorageChangeEvent(savePath))
        }
        foldPickerLauncher = caller.registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uriSelected: Uri? ->
            uriSelected?.let { uri ->
                // 保存权限，便于后续访问
                val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                activity.contentResolver.takePersistableUriPermission(uri, takeFlags)
                writeUri = uri
                savePathToPrefs()
                EventBus.getDefault().post(StorageChangeEvent(uri))
            }
        }
    }

    fun launch() {
        if (::writeUri.isInitialized) {
            foldPickerLauncher.launch(writeUri)
        } else {
            foldPickerLauncher.launch(Uri.parse(ContextHolder.appContext().getExternalFilesDir(null)?.path))
        }
    }

    fun getWriteFolder(): Uri? {
        return if (::writeUri.isInitialized) {
            writeUri
        } else {
            null
        }
    }

    fun getRootNode(): StorageNode? {
        return if (::writeUri.isInitialized) {
            val docTree = DocumentFile.fromTreeUri(ContextHolder.appContext(), writeUri) ?: error("Invalid SAF Uri")
            StorageNode.SafNode(docTree)
        } else {
            val dir = ContextHolder.appContext().getExternalFilesDir(null)
            if (dir != null) StorageNode.FileNode(dir) else null
        }
    }

    fun getCurrentOutputTarget(): OutputTarget? {
        return if (::writeUri.isInitialized) {
            OutputTarget.SafTarget(writeUri)
        } else {
            val dir = ContextHolder.appContext().getExternalFilesDir(null)
            if (dir != null) OutputTarget.FileTarget(dir) else null
        }
    }

    private fun savePathToPrefs() {
        val prefs = ContextHolder.appContext().getSharedPreferences("recording_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString(WRITE_PATH_KEY, writeUri.toString()).apply()
    }

    private fun getPathFromPrefs(): Uri? {
        val prefs = ContextHolder.appContext().getSharedPreferences("recording_prefs", Context.MODE_PRIVATE)
        val savePref = prefs.getString(WRITE_PATH_KEY, null) ?: return null
        return Uri.parse(savePref)
    }

    class StorageChangeEvent(val newPath: Uri)

    sealed class OutputTarget {
        abstract fun createOutputStream(
            context: Context, fileName: String, mimeType: String
        ): OutputStream

        /** App 外部存储默认目录 */
        class FileTarget(private val dir: File) : OutputTarget() {
            override fun createOutputStream(
                context: Context, fileName: String, mimeType: String
            ): OutputStream {
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, fileName)
                return FileOutputStream(file)
            }
        }

        /** 用户选择的 SAF 目录 */
        class SafTarget(private val treeUri: Uri) : OutputTarget() {
            override fun createOutputStream(
                context: Context, fileName: String, mimeType: String
            ): OutputStream {
                val docTree = DocumentFile.fromTreeUri(context, treeUri) ?: error("Invalid SAF Uri")

                val file = docTree.createFile(mimeType, fileName) ?: error("Create file failed")

                return context.contentResolver.openOutputStream(file.uri) ?: error("OpenOutputStream failed")
            }
        }
    }

    sealed class StorageNode {
        abstract val uri: Uri
        abstract val name: String
        abstract val isDirectory: Boolean
        abstract val size: Long
        abstract val lastModified: Long

        abstract fun listChildren(): List<StorageNode>
        abstract fun delete(): Boolean

        class FileNode(private val file: File) : StorageNode() {
            override val uri: Uri = file.toUri()
            override val name: String = file.name
            override val isDirectory: Boolean = file.isDirectory
            override val size: Long = if (file.isFile) file.length() else 0L
            override val lastModified: Long = file.lastModified()

            override fun listChildren(): List<StorageNode> {
                if (!file.isDirectory) return emptyList()
                return file.listFiles()?.map { FileNode(it) } ?: emptyList()
            }

            override fun delete(): Boolean = file.delete()
        }

        class SafNode(private val doc: DocumentFile) : StorageNode() {
            override val uri: Uri = doc.uri
            override val name: String = doc.name ?: ""
            override val isDirectory: Boolean = doc.isDirectory
            override val size: Long = doc.length()
            override val lastModified: Long = doc.lastModified()

            override fun listChildren(): List<StorageNode> {
                if (!doc.isDirectory) return emptyList()
                return doc.listFiles().map { SafNode(it) }
            }

            override fun delete(): Boolean = doc.delete()
        }

    }
}