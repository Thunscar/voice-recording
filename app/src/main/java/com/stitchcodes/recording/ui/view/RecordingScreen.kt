package com.stitchcodes.recording.ui.view

import android.Manifest.permission.*
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stitchcodes.recording.ContextHolder
import com.stitchcodes.recording.R
import com.stitchcodes.recording.service.RecordingService
import com.stitchcodes.recording.service.VoiceRecordHandler
import com.stitchcodes.recording.utils.*
import kotlinx.coroutines.flow.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import java.io.File
import java.text.SimpleDateFormat
import java.util.LinkedList
import java.util.Locale

//页面事件
sealed interface RecordingAction {
    data object StartRecording : RecordingAction
    data object StopRecording : RecordingAction
    data object ChangeStoragePath : RecordingAction
}

data class RecordingFile(val uri: Uri, val fileName: String, val timeStr: String, val duration: Int)

//页面ViewModel
class RecordingViewModel : ViewModel() {
    private val _recording = MutableStateFlow(false)
    val recording = _recording.asStateFlow()
    private val _storagePath = MutableStateFlow("")
    val storagePath = _storagePath.asStateFlow()
    val recordings = mutableStateListOf<RecordingFile>()

    init {
        val writeFolder = FoldPickerLauncher.getWriteFolder()
        if (writeFolder != null) {
            _storagePath.value = DocumentFile.fromTreeUri(ContextHolder.appContext(), writeFolder)?.uri?.path ?: "未知路径"
        } else {
            _storagePath.value = "默认存储路径"
        }
        loadRecordingFiles()
    }

    fun setRecording(state: Boolean) {
        _recording.value = state
    }

    fun changeStoragePath(path: String) {
        _storagePath.value = path
    }

    fun loadRecordingFiles() {
        val nodes = FoldPickerLauncher.getRootNode()?.listChildren() ?: return
        val dateFormat = SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault())
        val dateStrFormat = SimpleDateFormat("MM月dd日 HH:mm ss", Locale.getDefault())
        val fileList = LinkedList<RecordingFile>()
        for (node in nodes) {
            if (node.size == 0L) continue
            val nameSplit = node.name.split(".")
            if (nameSplit.size != 2) continue
            val dateTime = dateFormat.parse(nameSplit[0]) ?: continue
            val wavDur = (WavUtils.getWavDurationMs(node.uri) / 1000L).toInt()
            val dateTimeStr = dateStrFormat.format(dateTime)
            fileList.addFirst(RecordingFile(node.uri, node.name, dateTimeStr, wavDur))
        }
        recordings.clear()
        recordings.addAll(fileList)
    }
}


@Composable
fun RecordingScreen(modifier: Modifier, vm: RecordingViewModel = viewModel()) {
    val context = LocalContext.current
    val recording by vm.recording.collectAsStateWithLifecycle()
    val storagePath by vm.storagePath.collectAsStateWithLifecycle()

    DisposableEffect(Unit) {
        val subscriber = object {
            @Subscribe
            fun onMessageEvent(event: FoldPickerLauncher.StorageChangeEvent) {
                vm.changeStoragePath(DocumentFile.fromTreeUri(ContextHolder.appContext(), event.newPath)?.uri?.path ?: "未知路径")
                vm.loadRecordingFiles()
            }

            @Subscribe
            fun onSaveFileEvent(event: VoiceRecordHandler.SaveFileEvent) {
                vm.loadRecordingFiles()
            }
        }
        EventBus.getDefault().register(subscriber)
        onDispose {
            EventBus.getDefault().unregister(subscriber)
        }
    }

    val onAction: (RecordingAction) -> Unit = { action ->
        when (action) {
            RecordingAction.StartRecording -> {
                //检查是否有权限
                val hasRecordPerms = PermsUtils.hasPerms(context, RECORD_AUDIO)
                val hasNotifyPerms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    PermsUtils.hasPerms(context, POST_NOTIFICATIONS)
                } else {
                    NotificationManagerCompat.from(context).areNotificationsEnabled()
                }
                val writePerms = PermsUtils.hasPerms(context, WRITE_EXTERNAL_STORAGE)
                if (!hasRecordPerms) {
                    PermsLauncher.request(RECORD_AUDIO)
                }
                if (!hasRecordPerms) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        PermsLauncher.request(POST_NOTIFICATIONS)
                    }
                }
                if (!writePerms && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    PermsLauncher.request(WRITE_EXTERNAL_STORAGE)
                }
                if (hasNotifyPerms && hasRecordPerms) {
                    val intent = Intent(context, RecordingService::class.java)
                    context.startForegroundService(intent)
                    vm.setRecording(true)
                }
            }

            RecordingAction.StopRecording -> {
                if (ServiceUtils.isServiceRunning(context, RecordingService::class.java)) {
                    val intent = Intent(context, RecordingService::class.java)
                    context.stopService(intent)
                }
                vm.setRecording(false)
            }

            is RecordingAction.ChangeStoragePath -> {
                FoldPickerLauncher.launch()
            }
        }
    }
    RecordingView(modifier, recording, storagePath, vm.recordings, onAction = onAction)
}

@Composable
private fun RecordingView(
    modifier: Modifier, recording: Boolean, storagePath: String, recordings: List<RecordingFile>, onAction: (RecordingAction) -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        StorageSetting(storagePath = storagePath, changePath = { onAction(RecordingAction.ChangeStoragePath) })
        OperateButton(recording = recording,
            startRecording = { onAction(RecordingAction.StartRecording) },
            stopRecording = { onAction(RecordingAction.StopRecording) })
        RecordingFiles(recordings)
    }
}

@Composable
private fun StorageSetting(storagePath: String, changePath: () -> Unit) {
    Row(modifier = Modifier
        .fillMaxWidth()
        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
            changePath()
        }, horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.weight(1f)) {
            Text("存储路径", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 24.sp))
            Text(storagePath, style = TextStyle(fontWeight = FontWeight.Normal, fontSize = 18.sp), maxLines = 1)
        }
        Image(painterResource(R.drawable.next), contentDescription = "", modifier = Modifier.size(36.dp), contentScale = ContentScale.Crop)
    }
}

@Composable
private fun OperateButton(recording: Boolean, startRecording: () -> Unit, stopRecording: () -> Unit) {
    val btnText = if (recording) "停止录音" else "开始录音"
    val btnFontColor = if (recording) Color.White else Color.Red
    val btnBackColor = if (recording) Color.Red else Color.White
    Button(
        onClick = {
            if (recording) stopRecording() else startRecording()
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
        colors = ButtonDefaults.buttonColors(containerColor = btnBackColor)
    ) {
        Text(
            btnText, style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = btnFontColor)
        )
    }
}

@Composable
private fun RecordingFiles(recordings: List<RecordingFile>) {
    val lazyState = rememberLazyListState()
    LaunchedEffect(recordings.size) {
        if (recordings.isNotEmpty()) {
            lazyState.scrollToItem(0)
        }
    }
    if (recordings.isNotEmpty()) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.height(40.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                VerticalDivider(
                    color = Color.Black, thickness = 3.dp, modifier = Modifier
                        .height(30.dp)
                        .clip(RoundedCornerShape(1.5.dp))
                )
                Text(
                    "存储文件",
                    style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp),
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
            LazyColumn(
                modifier = Modifier
                    .weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp), state = lazyState
            ) {
                items(recordings, key = { it.fileName }) {
                    RecordingFile(it)
                }
            }
        }
    }
}

@Composable
private fun RecordingFile(file: RecordingFile) {
    val context = LocalContext.current
    Card(modifier = Modifier
        .fillMaxWidth()
        .height(72.dp)
        .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
            if (FoldPickerLauncher.getWriteFolder() != null) {
                WavPlayer.playWithSystemPlayer(uri = file.uri)
            } else {
                val uri = FileProvider.getUriForFile(
                    context, "${context.packageName}.fileprovider", File(
                        ContextHolder
                            .appContext()
                            .getExternalFilesDir(null), file.fileName
                    )
                )
                WavPlayer.playWithSystemPlayer(uri = uri)
            }
        },
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 15.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painterResource(R.drawable.audio_file),
                    contentDescription = "",
                    modifier = Modifier.size(36.dp),
                    contentScale = ContentScale.Crop
                )
                Column {
                    Text(
                        file.timeStr, style = TextStyle(
                            fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.Black
                        )
                    )
                    Text(
                        file.fileName, style = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp), color = Color.Gray
                    )
                }
            }
            Text(
                "%02d:%02d".format(file.duration / 60, file.duration % 60),
                style = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp),
                color = Color.Gray
            )
        }
    }
}
