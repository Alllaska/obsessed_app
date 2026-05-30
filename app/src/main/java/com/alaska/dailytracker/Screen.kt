package com.alaska.dailytracker

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch

/* ---------- palette ---------- */
private val AppBg = Color(0xFF18181A)
private val CardBg = Color(0xFF242427)
private val CardBorder = Color(0x0DFFFFFF)
private val InputBg = Color(0xFF2C2C30)
private val SheetBg = Color(0xFF1F1F22)
private val DialogBg = Color(0xFF232327)
private val TextMain = Color(0xFFF2F2F4)
private val TextDim = Color(0xFF8A8A90)
private val TextFaint = Color(0xFF6C6C72)
private val DescName = Color(0xFFD8D8DC)
private val LineColor = Color(0xFF3A3A3E)
private val Accent = Color(0xFF3B82F6)
private val Destructive = Color(0xFFFF453A)
private val InputBorder = Color(0x0FFFFFFF)
private val DashBorder = Color(0x1FFFFFFF)
private val AddIconBg = Color(0xFF3A3A3E)
private val AddGlyph = Color(0xFF9A9AA0)

private data class EditTarget(val id: String, val creatingNew: Boolean)

/* ---------- no-ripple click (matches the CSS tap-highlight: transparent) ---------- */
private fun Modifier.tap(onClick: () -> Unit): Modifier = this.then(
    Modifier.clickable(
        interactionSource = null,
        indication = null,
        onClick = onClick,
    )
)

@Composable
fun DailyTrackerApp(store: TrackerStore) {
    var editTarget by remember { mutableStateOf<EditTarget?>(null) }
    var showUpdate by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val day = store.sortedDay()

    val onToggle: (TaskDef) -> Unit = onToggle@{ t ->
        val becoming = store.toggleDone(t)
        if (!becoming) return@onToggle
        val nd = store.sortedDay()
        val idx = nd.indexOfFirst { it.id == t.id }
        if (idx >= 0 && nd.take(idx).all { store.isDone(it) }) {
            scope.launch { listState.animateScrollToItem(idx) }
        }
    }

    Box(Modifier.fillMaxSize().background(AppBg)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 8.dp, end = 14.dp, top = 44.dp, bottom = 40.dp),
        ) {
            itemsIndexed(day, key = { _, t -> t.id }) { index, t ->
                TaskRow(
                    store = store,
                    t = t,
                    isFirst = index == 0,
                    onTime = { editTarget = EditTarget(t.id, false) },
                    onToggle = { onToggle(t) },
                )
            }
            item(key = "__add") {
                AddRow(
                    isFirst = day.isEmpty(),
                    onClick = { editTarget = EditTarget("custom_" + System.currentTimeMillis(), true) },
                )
            }
        }

        // invisible reset hit area (top-left corner)
        Box(
            Modifier
                .align(Alignment.TopStart)
                .width(72.dp)
                .height(40.dp)
                .tap { showUpdate = true }
        )
    }

    editTarget?.let { target ->
        EditSheet(
            store = store,
            target = target,
            onDismiss = { editTarget = null },
        )
    }

    if (showUpdate) {
        UpdateDialog(
            onNo = { showUpdate = false },
            onYes = {
                store.resetToDefault()
                showUpdate = false
                scope.launch { listState.animateScrollToItem(0) }
            },
        )
    }
}

/* ---------- timeline row ---------- */
@Composable
private fun TaskRow(
    store: TrackerStore,
    t: TaskDef,
    isFirst: Boolean,
    onTime: () -> Unit,
    onToggle: () -> Unit,
) {
    val done = store.isDone(t)
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(46.dp).fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
            Text(
                text = store.getTime(t),
                color = TextDim,
                fontSize = 15.sp,
                modifier = Modifier.fillMaxWidth().tap(onTime),
            )
        }
        TimelineNode(iconKey = store.getIcon(t), done = done, isFirst = isFirst, isLast = false, onClick = onToggle)
        Box(Modifier.weight(1f).padding(vertical = 7.dp)) {
            TaskCard(store, t, done, onToggle)
        }
    }
}

@Composable
private fun TimelineNode(iconKey: String, done: Boolean, isFirst: Boolean, isLast: Boolean, onClick: () -> Unit) {
    Box(Modifier.width(40.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val top = if (isFirst) size.height / 2f else 0f
            val bottom = if (isLast) size.height / 2f else size.height
            drawLine(
                color = LineColor,
                start = Offset(cx, top),
                end = Offset(cx, bottom),
                strokeWidth = 1.5.dp.toPx(),
            )
        }
        IconCircle(iconKey = iconKey, done = done, onClick = onClick)
    }
}

@Composable
private fun IconCircle(iconKey: String, done: Boolean, onClick: () -> Unit) {
    val base = Modifier.size(36.dp)
    val shadowed = if (!done) base.shadow(4.dp, CircleShape) else base
    Box(
        modifier = shadowed
            .clip(CircleShape)
            .background(if (done) SolidColor(doneIconColor(iconKey)) else iconBrush(iconKey), CircleShape)
            .tap(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            imageVector = glyphVector(iconKey),
            contentDescription = null,
            modifier = Modifier.size(19.dp),
            colorFilter = if (done) ColorFilter.tint(DONE_GLYPH_TINT) else null,
        )
    }
}

@Composable
private fun TaskCard(store: TrackerStore, t: TaskDef, done: Boolean, onTitleClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardBg)
            .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
            .alpha(if (done) 0.55f else 1f)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(
            text = store.getTitle(t),
            color = if (done) TextDim else TextMain,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            textDecoration = if (done) TextDecoration.LineThrough else null,
            modifier = Modifier.tap(onTitleClick),
        )
        val sub = store.getSub(t)
        if (sub.isNotEmpty()) {
            Text(text = sub, color = TextDim, fontSize = 13.5.sp, modifier = Modifier.padding(top = 3.dp))
        }
        val desc = store.getDesc(t)
        if (desc.isNotBlank()) {
            Column(
                modifier = Modifier.padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                desc.split("\n").forEach { line ->
                    if (line.isBlank()) return@forEach
                    val parts = line.split("|")
                    val name = parts.getOrNull(0)?.trim().orEmpty()
                    val reps = parts.getOrNull(1)?.trim().orEmpty()
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            text = name,
                            color = DescName,
                            fontSize = 14.sp,
                            textDecoration = if (done) TextDecoration.LineThrough else null,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (reps.isNotEmpty()) {
                            Text(
                                text = reps,
                                color = TextFaint,
                                fontSize = 14.sp,
                                textDecoration = if (done) TextDecoration.LineThrough else null,
                                modifier = Modifier.padding(start = 12.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/* ---------- add-new row ---------- */
@Composable
private fun AddRow(isFirst: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min).tap(onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(46.dp).fillMaxHeight())
        Box(Modifier.width(40.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val top = if (isFirst) size.height / 2f else 0f
                drawLine(
                    color = LineColor,
                    start = Offset(cx, top),
                    end = Offset(cx, size.height / 2f),
                    strokeWidth = 1.5.dp.toPx(),
                )
            }
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(AddIconBg),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    imageVector = plusVector(),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    colorFilter = ColorFilter.tint(AddGlyph),
                )
            }
        }
        Box(Modifier.weight(1f).padding(vertical = 7.dp)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .dashedBorder(DashBorder, 16.dp, 1.dp)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Text("Добавить задачу", color = TextFaint, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

private fun Modifier.dashedBorder(color: Color, corner: Dp, width: Dp): Modifier = this.drawBehind {
    drawRoundRect(
        color = color,
        cornerRadius = CornerRadius(corner.toPx()),
        style = Stroke(
            width = width.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)),
        ),
    )
}

/* ---------- edit / create sheet ---------- */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditSheet(store: TrackerStore, target: EditTarget, onDismiss: () -> Unit) {
    val def = remember(target.id) { DAY.firstOrNull { it.id == target.id } ?: TaskDef(target.id, "", ICON_ORDER[0], "", custom = true) }

    var title by remember(target) { mutableStateOf(if (target.creatingNew) "" else store.getTitle(def)) }
    var note by remember(target) { mutableStateOf(if (target.creatingNew) "" else store.getSub(def)) }
    var desc by remember(target) { mutableStateOf(if (target.creatingNew) "" else store.getDesc(def)) }
    var time by remember(target) { mutableStateOf(if (target.creatingNew) "" else store.getTime(def)) }
    var icon by remember(target) { mutableStateOf(if (target.creatingNew) ICON_ORDER[0] else store.getIcon(def)) }

    var showTimePicker by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SheetBg,
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        dragHandle = {
            Box(Modifier.fillMaxWidth().padding(top = 10.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.width(38.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF4A4A4F)))
            }
        },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = if (target.creatingNew) "Новая задача" else "Редактировать пункт",
                color = TextMain,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp),
                style = TextStyle(textAlign = androidx.compose.ui.text.style.TextAlign.Center),
            )

            FieldLabel("Иконка")
            IconPicker(selected = icon, onSelect = { icon = it })
            Spacer(Modifier.height(16.dp))

            FieldLabel("Название")
            SheetInput(value = title, onValueChange = { title = it })
            Spacer(Modifier.height(16.dp))

            FieldLabel("Примечание")
            SheetInput(value = note, onValueChange = { note = it }, placeholder = "напр. 1 hr 20 mins")
            Spacer(Modifier.height(16.dp))

            FieldLabel("Описание")
            SheetInput(value = desc, onValueChange = { desc = it }, singleLine = false, minHeight = 150.dp)
            Text(
                text = "Каждая строка — отдельный пункт. Можно добавить значение справа через «|», напр.: 3 x Chin-Up | 19 total reps",
                color = TextFaint,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp),
            )
            Spacer(Modifier.height(16.dp))

            FieldLabel("Время")
            TimeField(time = time, onClick = { showTimePicker = true })
            Spacer(Modifier.height(8.dp))

            if (!target.creatingNew) {
                Text(
                    text = "Удалить",
                    color = Destructive,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .tap { confirmDelete = true }
                        .padding(vertical = 12.dp),
                    style = TextStyle(textAlign = androidx.compose.ui.text.style.TextAlign.Center),
                )
            }

            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                SheetButton(text = "Отмена", bg = InputBg, fg = TextDim, modifier = Modifier.weight(1f), onClick = onDismiss)
                SheetButton(text = "Сохранить", bg = Accent, fg = Color.White, modifier = Modifier.weight(1f)) {
                    store.saveTask(
                        id = target.id,
                        creatingNew = target.creatingNew,
                        title = title,
                        sub = note,
                        desc = desc,
                        icon = icon,
                        time = time,
                    )
                    onDismiss()
                }
            }
        }
    }

    if (showTimePicker) {
        TimePickerDialog(
            initial = time,
            onConfirm = { time = it; showTimePicker = false },
            onDismiss = { showTimePicker = false },
        )
    }

    if (confirmDelete) {
        ConfirmDialog(
            message = "Удалить задачу?",
            onConfirm = {
                confirmDelete = false
                store.deleteTask(target.id)
                onDismiss()
            },
            onCancel = { confirmDelete = false },
        )
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text.uppercase(),
        color = TextFaint,
        fontSize = 12.sp,
        letterSpacing = 0.5.sp,
        modifier = Modifier.padding(start = 2.dp, bottom = 7.dp),
    )
}

@Composable
private fun SheetInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String? = null,
    singleLine: Boolean = true,
    minHeight: Dp? = null,
) {
    var focused by remember { mutableStateOf(false) }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = singleLine,
        textStyle = TextStyle(color = TextMain, fontSize = 16.sp),
        cursorBrush = SolidColor(Accent),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(InputBg)
            .border(1.dp, if (focused) Accent else InputBorder, RoundedCornerShape(12.dp))
            .onFocusChanged { focused = it.isFocused },
        decorationBox = { inner ->
            Box(
                Modifier
                    .padding(horizontal = 14.dp, vertical = 13.dp)
                    .then(if (minHeight != null) Modifier.heightIn(min = minHeight) else Modifier),
                contentAlignment = if (minHeight != null) Alignment.TopStart else Alignment.CenterStart,
            ) {
                if (value.isEmpty() && placeholder != null) {
                    Text(placeholder, color = TextFaint, fontSize = 16.sp)
                }
                inner()
            }
        },
    )
}

@Composable
private fun TimeField(time: String, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(InputBg)
            .border(1.dp, InputBorder, RoundedCornerShape(12.dp))
            .tap(onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
    ) {
        if (time.isEmpty()) {
            Text("Выбрать время", color = TextFaint, fontSize = 16.sp)
        } else {
            Text(time, color = TextMain, fontSize = 16.sp)
        }
    }
}

@Composable
private fun IconPicker(selected: String, onSelect: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ICON_ORDER.forEach { key ->
            val isSel = key == selected
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(iconBrush(key), CircleShape)
                    .then(if (isSel) Modifier.border(2.dp, Color.White, CircleShape) else Modifier)
                    .tap { onSelect(key) },
                contentAlignment = Alignment.Center,
            ) {
                Image(imageVector = glyphVector(key), contentDescription = null, modifier = Modifier.size(21.dp))
            }
        }
    }
}

@Composable
private fun SheetButton(text: String, bg: Color, fg: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .tap(onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = fg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

/* ---------- dialogs ---------- */
@Composable
private fun UpdateDialog(onNo: () -> Unit, onYes: () -> Unit) {
    Dialog(onDismissRequest = onNo) {
        Surface(shape = RoundedCornerShape(20.dp), color = DialogBg) {
            Column(Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 26.dp, bottom = 20.dp)) {
                Text(
                    "Обновить?",
                    color = TextMain,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 22.dp),
                    style = TextStyle(textAlign = androidx.compose.ui.text.style.TextAlign.Center),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    SheetButton("Нет", InputBg, TextDim, Modifier.weight(1f), onNo)
                    SheetButton("Да", Accent, Color.White, Modifier.weight(1f), onYes)
                }
            }
        }
    }
}

@Composable
private fun ConfirmDialog(message: String, onConfirm: () -> Unit, onCancel: () -> Unit) {
    Dialog(onDismissRequest = onCancel) {
        Surface(shape = RoundedCornerShape(20.dp), color = DialogBg) {
            Column(Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 26.dp, bottom = 20.dp)) {
                Text(
                    message,
                    color = TextMain,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 22.dp),
                    style = TextStyle(textAlign = androidx.compose.ui.text.style.TextAlign.Center),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    SheetButton("Отмена", InputBg, TextDim, Modifier.weight(1f), onCancel)
                    SheetButton("Удалить", Destructive, Color.White, Modifier.weight(1f), onConfirm)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    val mins = timeToMinutes(initial)
    val h0 = if (mins == Int.MAX_VALUE) 12 else mins / 60
    val m0 = if (mins == Int.MAX_VALUE) 0 else mins % 60
    val state = rememberTimePickerState(initialHour = h0, initialMinute = m0, is24Hour = true)
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), color = DialogBg) {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                TimePicker(state = state)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Отмена", color = TextDim) }
                    TextButton(onClick = {
                        onConfirm(String.format("%02d:%02d", state.hour, state.minute))
                    }) { Text("OK", color = Accent) }
                }
            }
        }
    }
}
