package com.stitchcodes.recording.ui.view

import android.Manifest.permission.POST_NOTIFICATIONS
import android.Manifest.permission.RECORD_AUDIO
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.tooling.preview.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stitchcodes.recording.R
import com.stitchcodes.recording.service.RecordingService
import com.stitchcodes.recording.utils.*
import kotlinx.coroutines.flow.*

//页面事件
sealed interface RecordingAction {
    data object StartRecording : RecordingAction
    data object StopRecording : RecordingAction
    data class ChangeStoragePath(val path: String) : RecordingAction
}

//页面ViewModel
class RecordingViewModel : ViewModel() {
    private val _recording = MutableStateFlow(false)
    val recording = _recording.asStateFlow()
    private val _storagePath = MutableStateFlow("/Documents/Recordings/ProjectX")
    val storagePath = _storagePath.asStateFlow()

    fun setRecording(state: Boolean) {
        _recording.value = state
    }

    fun changeStoragePath(path: String) {
        _storagePath.value = path
    }
}


@Composable
fun RecordingScreen(modifier: Modifier, vm: RecordingViewModel = viewModel()) {
    val context = LocalContext.current
    val recording by vm.recording.collectAsStateWithLifecycle()
    val storagePath by vm.storagePath.collectAsStateWithLifecycle()

    val onAction: (RecordingAction) -> Unit = { action ->
        when (action) {
            RecordingAction.StartRecording -> {
                //检查是否有权限
                val hasRecordPerms = PermsUtils.hasPerms(context, RECORD_AUDIO)
                val hasNotifyPerms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    PermsUtils.hasPerms(context, POST_NOTIFICATIONS)
                } else {
                    true
                }
                if (!hasRecordPerms) {
                    PermsLauncher.request(RECORD_AUDIO)
                }
                if (!hasRecordPerms) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        PermsLauncher.request(POST_NOTIFICATIONS)
                    }
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

            }
        }
    }
    RecordingView(modifier, recording, storagePath, onAction = onAction)
}

@Preview(showBackground = true)
@Composable
fun RecordingViewPreview() {
    RecordingView(Modifier, recording = false, storagePath = "/Documents/Recordings/ProjectX", onAction = {})
}

@Composable
private fun RecordingView(modifier: Modifier, recording: Boolean, storagePath: String, onAction: (RecordingAction) -> Unit) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        StorageSetting(storagePath = storagePath, changePath = { onAction(RecordingAction.ChangeStoragePath(it)) })
        OperateButton(recording = recording,
            startRecording = { onAction(RecordingAction.StartRecording) },
            stopRecording = { onAction(RecordingAction.StopRecording) })
        RecordingFiles()
    }
}

@Composable
private fun StorageSetting(storagePath: String, changePath: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("存储路径", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 24.sp))
            Text(storagePath, style = TextStyle(fontWeight = FontWeight.Normal, fontSize = 18.sp))
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
private fun RecordingFiles() {
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
                "存储文件", style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp), modifier = Modifier.padding(bottom = 2.dp)
            )
        }
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(6) {
                RecordingFile()
            }
        }
    }
}

@Composable
private fun RecordingFile() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp),
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
                        "12月26日 11点14分 25秒", style = TextStyle(
                            fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.Black
                        )
                    )
                    Text(
                        "20251226111425.mp3", style = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp), color = Color.Gray
                    )
                }
            }
            Text(
                "3:32", style = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp), color = Color.Gray
            )
        }
    }
}
