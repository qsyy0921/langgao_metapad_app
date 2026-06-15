package com.langgao.metapad

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MetaPadTheme {
                MetaPadApp()
            }
        }
    }
}

private enum class AppScreen(
    val title: String,
    val icon: ImageVector,
) {
    Capture("拍照", Icons.Filled.CameraAlt),
    Inspection("检测", Icons.Filled.Assessment),
    Storage("记录", Icons.Filled.Folder),
    Settings("设置", Icons.Filled.Settings),
}

private enum class StorageMode(val label: String) {
    PadOnly("只存平板"),
    ServerOnly("只存服务器"),
    PadAndServer("平板和服务器"),
}

private data class InspectionCheckUi(
    val name: String,
    val status: String,
    val summary: String,
)

private data class InspectionUi(
    val requestId: Int = 0,
    val loading: Boolean = false,
    val status: String = "待检测",
    val summary: String = "确认照片后自动上传服务器检测",
    val originalImagePath: String? = null,
    val detectedImagePath: String? = null,
    val recordId: String? = null,
    val checks: List<InspectionCheckUi> = emptyList(),
)

private data class InspectionRecordUi(
    val id: String,
    val time: String,
    val status: String,
    val summary: String,
    val syncStatus: String,
)

@Composable
private fun MetaPadTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF2563EB),
            secondary = Color(0xFF0F766E),
            tertiary = Color(0xFFB45309),
            surface = Color(0xFFF8FAFC),
            background = Color(0xFFF1F5F9),
            error = Color(0xFFDC2626),
        ),
        content = content,
    )
}

@Composable
private fun MetaPadApp() {
    val context = LocalContext.current
    var selectedScreenName by rememberSaveable { mutableStateOf(AppScreen.Capture.name) }
    var capturedPhotoPath by rememberSaveable { mutableStateOf<String?>(null) }
    var serverUrl by rememberSaveable { mutableStateOf("http://192.168.0.141:8765") }
    var autoSave by rememberSaveable { mutableStateOf(true) }
    var storageModeName by rememberSaveable { mutableStateOf(StorageMode.PadAndServer.name) }
    var inspection by remember { mutableStateOf(InspectionUi()) }
    val records = remember {
        mutableStateListOf(
            InspectionRecordUi(
                id = "20260615-0001",
                time = "今天 20:40",
                status = "NG",
                summary = "内部杂物：检测到多余螺栓",
                syncStatus = "已同步服务器",
            ),
        )
    }
    val selectedScreen = AppScreen.valueOf(selectedScreenName)
    val storageMode = StorageMode.valueOf(storageModeName)

    LaunchedEffect(inspection.requestId) {
        if (inspection.loading) {
            val currentInspection = inspection
            val photoPath = currentInspection.originalImagePath
            val result = if (photoPath == null) {
                currentInspection.copy(
                    loading = false,
                    status = "ERROR",
                    summary = "没有找到待上传照片，请返回拍照后重试",
                )
            } else {
                inspectImageOnServer(
                    context = context,
                    serverUrl = serverUrl,
                    requestId = currentInspection.requestId,
                    originalImagePath = photoPath,
                )
            }
            inspection = result
            if (autoSave) {
                records.add(
                    0,
                    InspectionRecordUi(
                        id = "LOCAL-${result.requestId.toString().padStart(4, '0')}",
                        time = DateTimeFormatter.ofPattern("HH:mm:ss").format(LocalDateTime.now()),
                        status = result.status,
                        summary = result.summary,
                        syncStatus = when (storageMode) {
                            StorageMode.PadOnly -> "已保存本地"
                            StorageMode.ServerOnly -> "已同步服务器"
                            StorageMode.PadAndServer -> "本地和服务器已保存"
                        },
                    ),
                )
            }
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                AppScreen.entries.forEach { screen ->
                    NavigationBarItem(
                        selected = selectedScreen == screen,
                        onClick = { selectedScreenName = screen.name },
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title) },
                    )
                }
            }
        },
    ) { padding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            color = MaterialTheme.colorScheme.background,
        ) {
            when (selectedScreen) {
                AppScreen.Capture -> CaptureScreen(
                    capturedPhotoPath = capturedPhotoPath,
                    onPhotoCaptured = { capturedPhotoPath = it },
                    onRetake = { capturedPhotoPath = null },
                    onConfirm = {
                        val photoPath = capturedPhotoPath
                        if (photoPath != null) {
                            inspection = InspectionUi(
                                requestId = inspection.requestId + 1,
                                loading = true,
                                status = "检测中",
                                summary = "正在上传照片并等待服务器返回结果",
                                originalImagePath = photoPath,
                            )
                            selectedScreenName = AppScreen.Inspection.name
                        }
                    },
                )

                AppScreen.Inspection -> InspectionScreen(
                    inspection = inspection,
                    serverUrl = serverUrl,
                    onBackToCapture = {
                        capturedPhotoPath = null
                        selectedScreenName = AppScreen.Capture.name
                    },
                )

                AppScreen.Storage -> StorageScreen(records)

                AppScreen.Settings -> SettingsScreen(
                    serverUrl = serverUrl,
                    onServerUrlChange = { serverUrl = it },
                    autoSave = autoSave,
                    onAutoSaveChange = { autoSave = it },
                    storageMode = storageMode,
                    onStorageModeChange = { storageModeName = it.name },
                )
            }
        }
    }
}

@Composable
private fun CaptureScreen(
    capturedPhotoPath: String?,
    onPhotoCaptured: (String) -> Unit,
    onRetake: () -> Unit,
    onConfirm: () -> Unit,
) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
        )
    }
    var captureMessage by rememberSaveable { mutableStateOf("补光灯准备中") }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCameraPermission = granted
        captureMessage = if (granted) "相机权限已开启" else "需要相机权限才能拍照"
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        CameraArea(
            hasCameraPermission = hasCameraPermission,
            capturedPhotoPath = capturedPhotoPath,
            imageCapture = imageCapture,
            onTorchStatus = { captureMessage = it },
            onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
            modifier = Modifier.weight(1.35f),
        )
        Column(
            modifier = Modifier
                .weight(0.75f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                PageTitle(title = "拍照确认", subtitle = "请将接线盒完整放入取景框")
                InfoStrip(icon = Icons.Filled.Lightbulb, text = captureMessage)
                CaptureTip("14 个螺栓和漆标需要清晰可见")
                CaptureTip("白色密封胶和盒内区域需要完整入框")
                CaptureTip("照片不清晰时请重拍")
            }

            if (capturedPhotoPath != null) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("照片已拍摄，请确认是否清晰。", fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = onRetake,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Filled.Replay, contentDescription = "重拍")
                            Spacer(Modifier.width(8.dp))
                            Text("重拍")
                        }
                        Button(
                            onClick = onConfirm,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Filled.CloudUpload, contentDescription = "确认照片")
                            Spacer(Modifier.width(8.dp))
                            Text("确认照片")
                        }
                    }
                }
            } else {
                Button(
                    onClick = {
                        if (hasCameraPermission) {
                            takePhoto(
                                context = context,
                                imageCapture = imageCapture,
                                onSaved = {
                                    captureMessage = "照片已保存，请确认"
                                    onPhotoCaptured(it)
                                },
                                onError = { captureMessage = it },
                            )
                        } else {
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                ) {
                    Icon(Icons.Filled.CameraAlt, contentDescription = "拍照")
                    Spacer(Modifier.width(10.dp))
                    Text("拍照", fontSize = 18.sp)
                }
            }
        }
    }
}

@Composable
private fun CameraArea(
    hasCameraPermission: Boolean,
    capturedPhotoPath: String?,
    imageCapture: ImageCapture,
    onTorchStatus: (String) -> Unit,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF0F172A)),
    ) {
        when {
            capturedPhotoPath != null -> CapturedPhotoPreview(photoPath = capturedPhotoPath)
            hasCameraPermission -> CameraPreview(
                imageCapture = imageCapture,
                onTorchStatus = onTorchStatus,
            )
            else -> PermissionPanel(onRequestPermission = onRequestPermission)
        }
        Text(
            text = if (capturedPhotoPath == null) "请保持接线盒边框完整入画" else "确认照片后将自动上传检测",
            color = Color.White,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .background(Color(0x990F172A))
                .padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun CameraPreview(
    imageCapture: ImageCapture,
    onTorchStatus: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { viewContext ->
            val previewView = PreviewView(viewContext).apply {
                scaleType = PreviewView.ScaleType.FIT_CENTER
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            }

            cameraProviderFuture.addListener(
                {
                    runCatching {
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }
                        val camera = cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageCapture,
                        )
                        if (camera.cameraInfo.hasFlashUnit()) {
                            camera.cameraControl.enableTorch(true)
                            onTorchStatus("补光灯已开启")
                        } else {
                            onTorchStatus("设备无补光灯，使用普通拍照")
                        }
                    }.onFailure { error ->
                        onTorchStatus("相机启动失败：${error.message ?: "未知错误"}")
                    }
                },
                ContextCompat.getMainExecutor(viewContext),
            )
            previewView
        },
    )
}

@Composable
private fun CapturedPhotoPreview(photoPath: String) {
    val bitmap = remember(photoPath) { BitmapFactory.decodeFile(photoPath) }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "照片预览",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
        )
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("照片读取失败，请重拍", color = Color.White)
        }
    }
}

@Composable
private fun PermissionPanel(onRequestPermission: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("需要相机权限", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRequestPermission) {
            Text("开启相机权限")
        }
    }
}

@Composable
private fun InspectionScreen(
    inspection: InspectionUi,
    serverUrl: String,
    onBackToCapture: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        PageTitle(title = "检测结果", subtitle = "服务器：$serverUrl")

        if (inspection.loading) {
            LoadingInspection(modifier = Modifier.weight(1f))
        } else {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                PhotoResultPanel(
                    title = "原图",
                    photoPath = inspection.originalImagePath,
                    fallbackText = "等待照片",
                    modifier = Modifier.weight(1f),
                )
                PhotoResultPanel(
                    title = "检测图",
                    photoPath = inspection.detectedImagePath,
                    fallbackText = "等待服务器标注图",
                    modifier = Modifier.weight(1f),
                )
                ResultPanel(inspection = inspection, modifier = Modifier.weight(1.05f))
            }
        }

        Button(
            onClick = onBackToCapture,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) {
            Icon(Icons.Filled.CameraAlt, contentDescription = "返回拍照")
            Spacer(Modifier.width(8.dp))
            Text("返回拍照", fontSize = 18.sp)
        }
    }
}

@Composable
private fun LoadingInspection(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
            CircularProgressIndicator(strokeWidth = 5.dp)
            Text("正在上传照片并执行 YOLO + PatchCore 检测", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text("检测完成后将自动展示结果", color = Color(0xFF475569))
        }
    }
}

@Composable
private fun PhotoResultPanel(
    title: String,
    photoPath: String?,
    fallbackText: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxHeight(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFE2E8F0)),
                contentAlignment = Alignment.Center,
            ) {
                if (photoPath != null) {
                    CapturedPhotoPreview(photoPath = photoPath)
                } else {
                    Text(fallbackText, color = Color(0xFF475569))
                }
            }
        }
    }
}

@Composable
private fun ResultPanel(inspection: InspectionUi, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxHeight(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            StatusBanner(status = inspection.status, summary = inspection.summary)
            HorizontalDivider()
            inspection.checks.forEach { check ->
                CheckRow(check)
            }
        }
    }
}

@Composable
private fun StorageScreen(records: List<InspectionRecordUi>) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PageTitle(title = "检测记录", subtitle = "查看历史检测结果和同步状态")
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 8.dp),
        ) {
            items(records) { record ->
                RecordItem(record)
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    serverUrl: String,
    onServerUrlChange: (String) -> Unit,
    autoSave: Boolean,
    onAutoSaveChange: (Boolean) -> Unit,
    storageMode: StorageMode,
    onStorageModeChange: (StorageMode) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var connectionResult by rememberSaveable { mutableStateOf("未测试") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        PageTitle(title = "设置", subtitle = "配置服务器和自动存储策略")

        Card(shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = onServerUrlChange,
                    label = { Text("服务器地址") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = {
                            connectionResult = "测试中..."
                            scope.launch {
                                connectionResult = testHealth(serverUrl)
                            }
                        },
                    ) {
                        Text("连接测试")
                    }
                    Text(connectionResult, color = Color(0xFF475569))
                }
            }
        }

        Card(shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("自动存储", fontWeight = FontWeight.Bold)
                        Text("检测完成后按策略保存记录", color = Color(0xFF64748B))
                    }
                    Switch(checked = autoSave, onCheckedChange = onAutoSaveChange)
                }
                Text("存储位置", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StorageMode.entries.forEach { mode ->
                        val selected = mode == storageMode
                        if (selected) {
                            Button(onClick = { onStorageModeChange(mode) }) {
                                Text(mode.label)
                            }
                        } else {
                            OutlinedButton(onClick = { onStorageModeChange(mode) }) {
                                Text(mode.label)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PageTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
        Text(subtitle, color = Color(0xFF475569), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun InfoStrip(icon: ImageVector, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFEFF6FF))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, contentDescription = null, tint = Color(0xFF2563EB))
        Text(text, color = Color(0xFF1E40AF), fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun CaptureTip(text: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
        color = Color.White,
    ) {
        Text(text, modifier = Modifier.padding(12.dp), color = Color(0xFF334155))
    }
}

@Composable
private fun StatusBanner(status: String, summary: String) {
    val isPass = status == "PASS"
    val color = if (isPass) Color(0xFF16A34A) else Color(0xFFDC2626)
    val bg = if (isPass) Color(0xFFDCFCE7) else Color(0xFFFEE2E2)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = if (isPass) Icons.Filled.CheckCircle else Icons.Filled.Warning,
            contentDescription = status,
            tint = color,
        )
        Column {
            Text(status, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = color)
            Text(summary, color = Color(0xFF334155))
        }
    }
}

@Composable
private fun CheckRow(check: InspectionCheckUi) {
    val color = when (check.status) {
        "PASS" -> Color(0xFF16A34A)
        "NG" -> Color(0xFFDC2626)
        else -> Color(0xFFB45309)
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(check.status, color = color, fontWeight = FontWeight.Bold, modifier = Modifier.width(58.dp))
        Column {
            Text(check.name, fontWeight = FontWeight.SemiBold)
            Text(check.summary, color = Color(0xFF64748B))
        }
    }
}

@Composable
private fun RecordItem(record: InspectionRecordUi) {
    Card(shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StatusPill(record.status)
            Column(modifier = Modifier.weight(1f)) {
                Text(record.id, fontWeight = FontWeight.Bold)
                Text(record.summary, color = Color(0xFF334155))
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(record.time, color = Color(0xFF64748B))
                Text(record.syncStatus, color = Color(0xFF2563EB))
            }
        }
    }
}

@Composable
private fun StatusPill(status: String) {
    val color = if (status == "PASS") Color(0xFF16A34A) else Color(0xFFDC2626)
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.12f),
    ) {
        Text(
            text = status,
            color = color,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

private fun takePhoto(
    context: Context,
    imageCapture: ImageCapture,
    onSaved: (String) -> Unit,
    onError: (String) -> Unit,
) {
    val photoFile = createPhotoFile(context)
    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
    imageCapture.takePicture(
        outputOptions,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                onSaved(photoFile.absolutePath)
            }

            override fun onError(exception: ImageCaptureException) {
                onError("拍照失败：${exception.message ?: "未知错误"}")
            }
        },
    )
}

private fun createPhotoFile(context: Context): File {
    val captureDir = File(context.cacheDir, "captures").apply { mkdirs() }
    val timestamp = DateTimeFormatter
        .ofPattern("yyyyMMdd_HHmmss_SSS", Locale.CHINA)
        .format(LocalDateTime.now())
    return File(captureDir, "metapad_$timestamp.jpg")
}

private suspend fun testHealth(serverUrl: String): String = withContext(Dispatchers.IO) {
    val normalized = serverUrl.trim().trimEnd('/')
    if (normalized.isBlank()) return@withContext "请输入服务器地址"
    runCatching {
        val url = URL("$normalized/api/health")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 2500
            readTimeout = 2500
        }
        connection.use {
            if (it.responseCode in 200..299) "连接成功" else "连接失败：HTTP ${it.responseCode}"
        }
    }.getOrElse { error ->
        "连接失败：${error.message ?: "网络不可用"}"
    }
}

private suspend fun inspectImageOnServer(
    context: Context,
    serverUrl: String,
    requestId: Int,
    originalImagePath: String,
): InspectionUi = withContext(Dispatchers.IO) {
    val normalized = serverUrl.trim().trimEnd('/')
    if (normalized.isBlank()) {
        return@withContext inspectionError(requestId, originalImagePath, "请输入服务器地址")
    }

    val imageFile = File(originalImagePath)
    if (!imageFile.exists()) {
        return@withContext inspectionError(requestId, originalImagePath, "照片文件不存在，请重新拍照")
    }

    runCatching {
        val response = uploadImageForInspection(
            inspectUrl = "$normalized/api/inspect",
            imageFile = imageFile,
        )
        val json = JSONObject(response)
        val passed = json.optBoolean("pass", false)
        val recordId = json.optString("recordId").ifBlank { null }
        val reasons = json.optJSONArray("reasons")
            ?.let { array -> List(array.length()) { index -> array.optString(index) }.filter { it.isNotBlank() } }
            .orEmpty()
        val detectedUrl = json.optString("detectedUrl")
        val detectedImagePath = if (detectedUrl.isNotBlank()) {
            downloadServerImage(
                context = context,
                baseUrl = normalized,
                pathOrUrl = detectedUrl,
                fileName = "detected_${recordId ?: requestId}.jpg",
            )
        } else {
            null
        }
        val status = if (passed) "PASS" else "NG"
        val summary = if (passed) {
            "检测通过：服务器未返回异常原因"
        } else {
            "检测未通过：" + reasons.ifEmpty { listOf("服务器返回未通过，但未给出原因") }.joinToString("；")
        }
        InspectionUi(
            requestId = requestId,
            loading = false,
            status = status,
            summary = summary,
            originalImagePath = originalImagePath,
            detectedImagePath = detectedImagePath,
            recordId = recordId,
            checks = buildServerChecks(passed, reasons),
        )
    }.getOrElse { error ->
        inspectionError(
            requestId = requestId,
            originalImagePath = originalImagePath,
            message = "连接服务器失败：${error.message ?: "未知错误"}",
        )
    }
}

private fun uploadImageForInspection(inspectUrl: String, imageFile: File): String {
    val boundary = "----MetaPadBoundary${System.currentTimeMillis()}"
    val lineEnd = "\r\n"
    val connection = (URL(inspectUrl).openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        doInput = true
        doOutput = true
        connectTimeout = 10_000
        readTimeout = 30_000
        setRequestProperty("Connection", "Keep-Alive")
        setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
    }

    connection.outputStream.use { output ->
        output.writeText("--$boundary$lineEnd")
        output.writeText(
            "Content-Disposition: form-data; name=\"image\"; filename=\"${imageFile.name}\"$lineEnd",
        )
        output.writeText("Content-Type: image/jpeg$lineEnd$lineEnd")
        imageFile.inputStream().use { input -> input.copyTo(output) }
        output.writeText(lineEnd)
        output.writeText("--$boundary--$lineEnd")
    }

    return connection.use { http ->
        val code = http.responseCode
        val stream = if (code in 200..299) http.inputStream else http.errorStream
        val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        if (code !in 200..299) {
            error("HTTP $code ${body.take(160)}")
        }
        body
    }
}

private fun downloadServerImage(
    context: Context,
    baseUrl: String,
    pathOrUrl: String,
    fileName: String,
): String {
    val url = if (pathOrUrl.startsWith("http://") || pathOrUrl.startsWith("https://")) {
        URL(pathOrUrl)
    } else {
        URL(baseUrl + if (pathOrUrl.startsWith("/")) pathOrUrl else "/$pathOrUrl")
    }
    val outputDir = File(context.cacheDir, "server-results").apply { mkdirs() }
    val outputFile = File(outputDir, fileName)
    val connection = (url.openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 10_000
        readTimeout = 30_000
    }
    connection.use { http ->
        val code = http.responseCode
        if (code !in 200..299) {
            error("下载检测图失败：HTTP $code")
        }
        http.inputStream.use { input ->
            outputFile.outputStream().use { output -> input.copyTo(output) }
        }
    }
    return outputFile.absolutePath
}

private fun buildServerChecks(passed: Boolean, reasons: List<String>): List<InspectionCheckUi> {
    if (passed) {
        return listOf(
            InspectionCheckUi("服务器检测", "PASS", "已收到服务器检测结果和标注图"),
            InspectionCheckUi("螺栓与漆标", "PASS", "以服务器模型输出为准"),
            InspectionCheckUi("白色密封胶", "PASS", "以服务器模型输出为准"),
            InspectionCheckUi("端面 / 内部异常", "PASS", "以服务器模型输出为准"),
        )
    }
    return listOf(
        InspectionCheckUi("服务器检测", "NG", reasons.ifEmpty { listOf("服务器未给出原因") }.joinToString("；")),
        InspectionCheckUi("螺栓与漆标", "REVIEW", "等待服务器细分检测项"),
        InspectionCheckUi("白色密封胶", "REVIEW", "等待服务器细分检测项"),
        InspectionCheckUi("端面 / 内部异常", "REVIEW", "等待服务器细分检测项"),
    )
}

private fun inspectionError(requestId: Int, originalImagePath: String?, message: String): InspectionUi {
    return InspectionUi(
        requestId = requestId,
        loading = false,
        status = "ERROR",
        summary = message,
        originalImagePath = originalImagePath,
        checks = listOf(
            InspectionCheckUi("服务器连接", "NG", message),
        ),
    )
}

private fun java.io.OutputStream.writeText(text: String) {
    write(text.toByteArray(Charsets.UTF_8))
}

private inline fun <T : HttpURLConnection, R> T.use(block: (T) -> R): R {
    return try {
        block(this)
    } finally {
        disconnect()
    }
}
