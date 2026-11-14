package app.cellaroo.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import app.cellaroo.ExtraChoice
import app.cellaroo.MainViewModel
import app.cellaroo.R
import app.cellaroo.ScanItem
import app.cellaroo.ScanKind
import app.cellaroo.ScanPhoto
import app.cellaroo.ScanSession
import app.cellaroo.core.MatchKind
import app.cellaroo.core.Resolution
import app.cellaroo.core.WineType
import app.cellaroo.data.MissingAction
import coil3.compose.AsyncImage
import java.io.File

@Composable
fun ScanScreen(vm: MainViewModel, nav: NavHostController) {
    val context = LocalContext.current
    val state by vm.cellar.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val session by vm.scan.collectAsStateWithLifecycle()
    val error by vm.scanError.collectAsStateWithLifecycle()
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var lineup by remember { mutableStateOf(true) }
    var flash by remember { mutableStateOf(false) }
    var capture by remember { mutableStateOf<ImageCapture?>(null) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var pickShelf by remember { mutableStateOf(false) }
    val askCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) vm.onPhotoPicked(uri) }
    val reconcile = session?.kind == ScanKind.RECONCILE

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(if (reconcile) "Check a shelf" else "Scan bottles") {
            if (granted) {
                TextButton(onClick = { lineup = !lineup }) { Text(if (lineup) "Lineup" else "Single") }
                IconButton(onClick = { flash = !flash }) {
                    Icon(painterResource(if (flash) R.drawable.ic_flash_on else R.drawable.ic_flash_off), contentDescription = "Flash")
                }
            }
        }
        if (!settings.hasKey) {
            Banner("Label reading needs your Gemini API key.", "Open Settings") { nav.navigate("settings") }
        }
        if (reconcile) {
            Banner(
                "Checking “${state.locationName(session?.locationId)}”. Photograph the shelf with labels facing you, one row per photo." +
                    if ((session?.photos?.size ?: 0) > 0) " ${session?.photos?.size} photo(s) so far." else "",
                "Cancel",
            ) { vm.cancelScan() }
        }
        error?.let { msg ->
            Banner(msg, if (vm.canRetryScan) "Retry" else "Dismiss") { if (vm.canRetryScan) vm.retryFailedPhoto() else vm.scanError.value = null }
        }
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp).clip(RoundedCornerShape(16.dp)).background(Color.Black)) {
            if (granted) {
                CameraPane(flash, onReady = { capture = it }, onError = { cameraError = it }, modifier = Modifier.fillMaxSize())
                FramingGuide(lineup)
                cameraError?.let { Text(it, color = Color.White, modifier = Modifier.align(Alignment.Center).padding(24.dp), textAlign = TextAlign.Center) }
                Box(
                    Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp).size(74.dp).clip(CircleShape).background(Color.White)
                        .clickable(enabled = capture != null) {
                            val ic = capture ?: return@clickable
                            val file = vm.images.tempCaptureFile()
                            ic.takePicture(
                                ImageCapture.OutputFileOptions.Builder(file).build(),
                                ContextCompat.getMainExecutor(context),
                                object : ImageCapture.OnImageSavedCallback {
                                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                        vm.onPhotoCaptured(file)
                                    }

                                    override fun onError(exception: ImageCaptureException) {
                                        vm.scanError.value = "The camera could not take the photo: ${exception.message}"
                                    }
                                },
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(60.dp).clip(CircleShape).background(Palette.Gold))
                }
            } else {
                Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Cellaroo uses the camera only to photograph your bottles.", color = Color.White, textAlign = TextAlign.Center)
                    Gap(12.dp)
                    Button(onClick = { askCamera.launch(Manifest.permission.CAMERA) }) { Text("Allow camera") }
                }
            }
        }
        FlowRow(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                Icon(painterResource(R.drawable.ic_photos), contentDescription = null)
                Text("  Gallery")
            }
            if (!reconcile) {
                OutlinedButton(onClick = { pickShelf = true }) {
                    Icon(painterResource(R.drawable.ic_shelf), contentDescription = null)
                    Text("  Check a shelf")
                }
                OutlinedButton(onClick = { vm.clearPaste(); nav.navigate("paste/0") }) {
                    Icon(painterResource(R.drawable.ic_paste), contentDescription = null)
                    Text("  Paste text")
                }
                OutlinedButton(onClick = { nav.navigate("edit/0") }) { Text("Add by hand") }
            } else if ((session?.photos?.size ?: 0) > 0) {
                Button(onClick = { nav.navigate("reconcileReview") }) { Text("Review shelf check") }
            }
        }
    }

    if (pickShelf) {
        AlertDialog(
            onDismissRequest = { pickShelf = false },
            title = { Text("Which shelf?") },
            text = {
                Column {
                    Text("Cellaroo compares the photo with what it expects on that shelf and proposes what was drunk, moved or added.", color = Palette.TextDim, style = MaterialTheme.typography.bodySmall)
                    Gap()
                    for (l in state.locations) {
                        val n = state.rows.sumOf { r -> r.bottles.count { it.locationId == l.id } }
                        TextButton(onClick = { vm.startScan(ScanKind.RECONCILE, l.id); pickShelf = false }) { Text("${l.name}  ($n bottles)") }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickShelf = false }) { Text("Cancel") } },
        )
    }
}

@Composable
fun Banner(text: String, action: String, onAction: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clip(RoundedCornerShape(12.dp))
            .background(Palette.SurfaceHigh).padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = onAction) { Text(action) }
    }
}

@Composable
private fun CameraPane(flash: Boolean, onReady: (ImageCapture) -> Unit, onError: (String) -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var request by remember { mutableStateOf<SurfaceRequest?>(null) }
    val imageCapture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build() }
    LaunchedEffect(owner) {
        try {
            val provider = ProcessCameraProvider.awaitInstance(context)
            val preview = Preview.Builder().build()
            preview.setSurfaceProvider { req -> request = req }
            provider.unbindAll()
            provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
            onReady(imageCapture)
        } catch (e: Exception) {
            onError("The camera could not start: ${e.message}")
        }
    }
    LaunchedEffect(flash) {
        imageCapture.flashMode = if (flash) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
    }
    request?.let { CameraXViewfinder(surfaceRequest = it, modifier = modifier) }
}

@Composable
private fun FramingGuide(lineup: Boolean) {
    Canvas(Modifier.fillMaxSize()) {
        val w = if (lineup) size.width * 0.92f else size.width * 0.5f
        val h = if (lineup) size.height * 0.55f else size.height * 0.78f
        drawRoundRect(
            color = Color.White.copy(alpha = 0.75f),
            topLeft = Offset((size.width - w) / 2, (size.height - h) / 2 - size.height * 0.04f),
            size = Size(w, h),
            cornerRadius = CornerRadius(24f, 24f),
            style = Stroke(width = 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(24f, 16f))),
        )
    }
}

/** A stored photo with numbered outlines. Boxes are [ymin, xmin, ymax, xmax] on a 0–1000 scale. */
@Composable
fun PhotoWithBoxes(file: File?, photo: ScanPhoto, items: List<ScanItem>, selected: Int?, onSelect: (Int) -> Unit) {
    Box(Modifier.fillMaxWidth().aspectRatio(photo.width.toFloat() / photo.height.coerceAtLeast(1)).clip(RoundedCornerShape(12.dp))) {
        // The box and the image share one aspect ratio, so box coordinates map straight onto the canvas.
        AsyncImage(model = file, contentDescription = "Scanned photo", contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
        Canvas(
            Modifier.fillMaxSize().pointerInput(items) {
                detectTapGestures { p ->
                    val y = p.y / size.height * 1000
                    val x = p.x / size.width * 1000
                    items.firstOrNull { it.boxes.any { b -> y >= b[0] && y <= b[2] && x >= b[1] && x <= b[3] } }?.let { onSelect(it.id) }
                }
            }
        ) {
            val paint = android.graphics.Paint().apply {
                isAntiAlias = true
                textSize = 13.dp.toPx()
                textAlign = android.graphics.Paint.Align.CENTER
                isFakeBoldText = true
                color = android.graphics.Color.BLACK
            }
            for (item in items) {
                val color = Palette.box(item.id)
                val isSel = item.id == selected
                for (b in item.boxes) {
                    val left = b[1] / 1000f * size.width
                    val top = b[0] / 1000f * size.height
                    val right = b[3] / 1000f * size.width
                    val bottom = b[2] / 1000f * size.height
                    if (isSel) drawRect(color.copy(alpha = 0.22f), Offset(left, top), Size(right - left, bottom - top))
                    drawRect(color, Offset(left, top), Size(right - left, bottom - top), style = Stroke(width = (if (isSel) 4.dp else 2.dp).toPx()))
                    val r = 11.dp.toPx()
                    val cx = (left + r + 2).coerceAtMost(size.width - r)
                    val cy = (top + r + 2).coerceAtMost(size.height - r)
                    drawCircle(color, r, Offset(cx, cy))
                    drawContext.canvas.nativeCanvas.drawText(item.id.toString(), cx, cy + paint.textSize / 3, paint)
                }
            }
        }
    }
}

@Composable
private fun NumberBadge(id: Int) {
    Box(Modifier.size(26.dp).clip(CircleShape).background(Palette.box(id)), contentAlignment = Alignment.Center) {
        Text(id.toString(), color = Color.Black, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun ItemFields(item: ScanItem, onChange: (ScanItem) -> Unit, showQty: Boolean) {
    var more by remember(item.id) { mutableStateOf(false) }
    OutlinedTextField(item.producer, { onChange(item.copy(producer = it)) }, label = { Text("Producer") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(item.wineName, { onChange(item.copy(wineName = it)) }, label = { Text("Wine") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        NumberField(item.vintage, { onChange(item.copy(vintage = it.take(4))) }, "Vintage", Modifier.weight(1f))
        NumberField(item.price, { onChange(item.copy(price = it)) }, "Paid each $", Modifier.weight(1f), decimal = true)
        if (showQty) QtyStepper(item.qty, { onChange(item.copy(qty = (item.qty - 1).coerceAtLeast(1))) }, { onChange(item.copy(qty = item.qty + 1)) })
    }
    if (more) {
        OutlinedTextField(item.region, { onChange(item.copy(region = it)) }, label = { Text("Appellation / region") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(item.country, { onChange(item.copy(country = it)) }, label = { Text("Country") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(item.grapes, { onChange(item.copy(grapes = it)) }, label = { Text("Grapes") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (t in WineType.entries) FilterChip(selected = item.type == t, onClick = { onChange(item.copy(type = t)) }, label = { Text(t.label) })
        }
    } else {
        TextButton(onClick = { more = true }) {
            Text(listOf(item.region, item.country, item.type.label).filter { it.isNotBlank() }.joinToString(" · ") + "  ·  edit")
        }
    }
}

@Composable
private fun ReviewCard(color: Color? = null, content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = if (color != null) color.copy(alpha = 0.12f).compositeOverSurface() else Palette.Surface),
        shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { content() }
    }
}

private fun Color.compositeOverSurface(): Color {
    val a = alpha
    return Color(
        red = red * a + Palette.Surface.red * (1 - a),
        green = green * a + Palette.Surface.green * (1 - a),
        blue = blue * a + Palette.Surface.blue * (1 - a),
        alpha = 1f,
    )
}

@Composable
fun ScanReviewScreen(vm: MainViewModel, nav: NavHostController) {
    val session by vm.scan.collectAsStateWithLifecycle()
    val state by vm.cellar.collectAsStateWithLifecycle()
    val s = session
    var selected by remember { mutableStateOf<Int?>(null) }
    var location by remember(state.locations) { mutableStateOf(state.locations.firstOrNull()?.id) }
    if (s == null) {
        Column { ScreenHeader("Review scan", onBack = { nav.popBackStack() }) }
        return
    }
    val total = s.items.filter { it.producer.isNotBlank() || it.wineName.isNotBlank() }.sumOf { it.qty }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Review scan", onBack = { vm.cancelScan(); nav.popBackStack() })
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(s.photos.size) { i ->
                PhotoWithBoxes(vm.images.file(s.photos[i].name), s.photos[i], s.items.filter { it.photo == i }, selected) { selected = it }
            }
            item {
                Text(
                    "Check each label against the photo. Tap a card to highlight its bottle. Blank vintage means the label did not show one.",
                    color = Palette.TextDim, style = MaterialTheme.typography.bodySmall,
                )
            }
            items(s.items, key = { it.id }) { item ->
                val m = remember(item, state.rows) { vm.matchFor(item) }
                ReviewCard(if (selected == item.id) Palette.box(item.id) else null) {
                    Row(Modifier.clickable { selected = item.id }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumberBadge(item.id)
                        Column(Modifier.weight(1f)) {
                            val hint = when (m.kind) {
                                MatchKind.MATCH -> "Already in cellar: adds ${item.qty} bottle${if (item.qty == 1) "" else "s"} to it"
                                MatchKind.REVIEW -> if (m.wineId != null) "Similar wine in cellar (${m.reason}). Saved as a new wine." else "New wine. ${m.reason ?: ""}"
                                MatchKind.UNKNOWN -> "New wine"
                            }
                            Text(hint, style = MaterialTheme.typography.labelMedium, color = if (m.kind == MatchKind.MATCH) Palette.Emerald else Palette.TextDim)
                            if (!item.legible || item.confidence < 0.6) Text("Hard to read: please check", color = Palette.Amber, style = MaterialTheme.typography.labelMedium)
                            item.sticker?.let { Text("Sticker: $it (not stored as a critic score)", color = Palette.TextDim, style = MaterialTheme.typography.labelSmall) }
                        }
                        IconButton(onClick = { vm.removeScanItem(item.id) }) { Icon(Icons.Filled.Delete, contentDescription = "Remove") }
                    }
                    ItemFields(item, vm::updateScanItem, showQty = true)
                }
            }
        }
        Column(Modifier.fillMaxWidth().background(Palette.Surface).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LocationPicker(state.locations, location, { location = it })
                TextButton(onClick = { nav.popBackStack() }) { Text("Add another photo") }
            }
            Button(onClick = { vm.saveScan(location) }, enabled = total > 0, modifier = Modifier.fillMaxWidth()) {
                Text("Save all ($total bottle${if (total == 1) "" else "s"}) to cellar")
            }
        }
    }
}

@Composable
fun ReconcileReviewScreen(vm: MainViewModel, nav: NavHostController) {
    val session by vm.scan.collectAsStateWithLifecycle()
    val state by vm.cellar.collectAsStateWithLifecycle()
    val s: ScanSession? = session
    var selected by remember { mutableStateOf<Int?>(null) }
    val plan = remember(s, state.rows) { s?.let { vm.reconcilePlan(it) } }
    if (s == null || plan == null) {
        Column { ScreenHeader("Shelf check", onBack = { nav.popBackStack() }) }
        return
    }
    val items = s.items.associateBy { it.id }
    val fallback = vm.defaultMissingAction(plan)
    val ignored = s.resolutions.count { it.value == Resolution.Ignore }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(state.locationName(s.locationId), onBack = { nav.popBackStack() }) {
            IconButton(onClick = { vm.cancelScan(); nav.popBackStack("scan", inclusive = false) }) { Icon(Icons.Filled.Close, contentDescription = "Discard") }
        }
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(s.photos.size) { i ->
                PhotoWithBoxes(vm.images.file(s.photos[i].name), s.photos[i], s.items.filter { it.photo == i }, selected) { selected = it }
            }
            item {
                Text(
                    "${s.items.size} bottles seen, ${plan.matchedCount} match what the cellar expects here." +
                        if (ignored > 0) " $ignored ignored." else "",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (plan.review.isNotEmpty()) {
                item { GroupTitle("Needs your eye", "Decide these first; they change what counts as missing.") }
                items(plan.review, key = { "r${it.detection.index}" }) { r ->
                    val item = items[r.detection.index]
                    ReviewCard(Palette.Amber) {
                        Row(Modifier.clickable { selected = r.detection.index }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NumberBadge(r.detection.index)
                            Column(Modifier.weight(1f)) {
                                Text(listOf(item?.vintage.orEmpty(), item?.producer.orEmpty(), item?.wineName.orEmpty()).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "Unreadable label" })
                                Text(r.reason, color = Palette.Amber, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            r.suggestedWineId?.let { wid ->
                                state.row(wid)?.let { row ->
                                    Button(onClick = { vm.resolve(r.detection.index, Resolution.IsWine(wid)) }) { Text("It's ${row.title}") }
                                }
                            }
                            OutlinedButton(onClick = { vm.resolve(r.detection.index, Resolution.NewWine) }) { Text("New wine") }
                            OutlinedButton(onClick = { vm.resolve(r.detection.index, Resolution.Ignore) }) { Text("Ignore") }
                        }
                    }
                }
            }
            if (plan.missing.isNotEmpty()) {
                item { GroupTitle("Expected here, not in the photo", if (plan.isClean) "Defaulted to Drunk." else "Defaulted to Still here until the items above are settled.") }
                items(plan.missing, key = { "m${it.bottleId}" }) { b ->
                    val chosen = s.missingActions[b.bottleId] ?: fallback
                    ReviewCard(Palette.Crimson) {
                        Text(state.row(b.wineId)?.title ?: "Wine")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            for (a in MissingAction.entries) {
                                FilterChip(selected = chosen == a, onClick = { vm.setMissingAction(b.bottleId, a) }, label = { Text(a.label) })
                            }
                        }
                    }
                }
            }
            if (plan.extras.isNotEmpty()) {
                item { GroupTitle("More here than expected", null) }
                items(plan.extras, key = { "x${it.detection.index}" }) { x ->
                    val chosen = s.extraChoices[x.detection.index] ?: if (x.moveFrom != null) ExtraChoice.MOVE else ExtraChoice.NEW
                    ReviewCard(Palette.Teal) {
                        Row(Modifier.clickable { selected = x.detection.index }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NumberBadge(x.detection.index)
                            Text(state.row(x.wineId)?.title ?: "Wine", Modifier.weight(1f))
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (x.moveFrom != null) {
                                FilterChip(selected = chosen == ExtraChoice.MOVE, onClick = { vm.setExtraChoice(x.detection.index, ExtraChoice.MOVE) },
                                    label = { Text("Moved from ${state.locationName(x.moveFrom?.locationId)}") })
                            }
                            FilterChip(selected = chosen == ExtraChoice.NEW, onClick = { vm.setExtraChoice(x.detection.index, ExtraChoice.NEW) }, label = { Text("New bottle") })
                            FilterChip(selected = chosen == ExtraChoice.IGNORE, onClick = { vm.setExtraChoice(x.detection.index, ExtraChoice.IGNORE) }, label = { Text("Ignore") })
                        }
                    }
                }
            }
            if (plan.unknown.isNotEmpty()) {
                item { GroupTitle("New wines", "Not in the cellar yet. They will be added to this shelf.") }
                items(plan.unknown, key = { "u${it.index}" }) { d ->
                    val item = items[d.index] ?: return@items
                    ReviewCard(Palette.Emerald) {
                        Row(Modifier.clickable { selected = d.index }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NumberBadge(d.index)
                            Text("Add to cellar", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                            TextButton(onClick = { vm.resolve(d.index, Resolution.Ignore) }) { Text("Ignore") }
                        }
                        ItemFields(item, vm::updateScanItem, showQty = false)
                    }
                }
            }
            if (s.resolutions.isNotEmpty()) {
                item { TextButton(onClick = { s.resolutions.keys.forEach { vm.resolve(it, null) } }) { Text("Reset my decisions") } }
            }
        }
        Column(Modifier.fillMaxWidth().background(Palette.Surface).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (plan.review.isNotEmpty()) {
                Text("${plan.review.size} item(s) still need a decision. Undecided items are left alone.", color = Palette.Amber, style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { nav.popBackStack() }, modifier = Modifier.weight(1f)) { Text("Add photo") }
                Button(onClick = { vm.applyReconcile() }, modifier = Modifier.weight(1f)) { Text("Apply") }
            }
        }
    }
}

@Composable
private fun GroupTitle(title: String, subtitle: String?) {
    Column(Modifier.padding(top = 6.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = Palette.Gold)
        if (subtitle != null) Text(subtitle, color = Palette.TextDim, style = MaterialTheme.typography.bodySmall)
    }
}
