package com.weatherclone.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weatherclone.app.data.DistanceUnit
import com.weatherclone.app.data.Place
import com.weatherclone.app.data.PrecipUnit
import com.weatherclone.app.data.PressureUnit
import com.weatherclone.app.data.Settings
import com.weatherclone.app.data.Weather
import com.weatherclone.app.data.WindUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun ListScreen(
    places: List<Place>,
    weather: Map<String, Weather>,
    settings: Settings,
    locationEnabled: Boolean,
    onOpen: (Int) -> Unit,
    onRemove: (Place) -> Unit,
    onMove: (Int, Int) -> Unit,
    onSettings: (Settings) -> Unit,
    onEnableLocation: () -> Unit,
    search: suspend (String) -> List<Place>,
    onPreview: (Place) -> Unit,
    notif: com.weatherclone.app.data.NotifPrefs = com.weatherclone.app.data.NotifPrefs(true, true, false, 7),
    onNotif: (com.weatherclone.app.data.NotifPrefs) -> Unit = {},
) {
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<Place>>(emptyList()) }
    var menu by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var unitsSheet by remember { mutableStateOf(false) }
    var notifSheet by remember { mutableStateOf(false) }
    var widgetMenu by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(query) {
        if (query.isBlank()) { results = emptyList(); return@LaunchedEffect }
        delay(250)
        results = runCatching { search(query) }.getOrDefault(emptyList())
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            AnimatedVisibility(!searching, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.End) {
                        if (editing) {
                            Text("Done", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.clickable { editing = false }.padding(6.dp))
                        } else Box {
                            Box(
                                Modifier.size(30.dp).clip(CircleShape).border(1.6.dp, Color.White, CircleShape).clickable { menu = true },
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Filled.MoreHoriz, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
                            SettingsMenu(menu, { menu = false }, settings, onSettings, onEdit = { editing = true }, onUnits = { unitsSheet = true }, onNotifications = { notifSheet = true }, onWidgets = { widgetMenu = true })
                            WidgetMenu(widgetMenu) { widgetMenu = false }
                        }
                    }
                    Text("Weather", fontSize = 34.sp, fontFamily = InterDisplay, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(horizontal = 16.dp))
                    Spacer(Modifier.height(8.dp))
                }
            }

            Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.weight(1f).height(38.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF1C1C1E)).padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Search, null, tint = Color(0xFF8E8E93), modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Box(Modifier.weight(1f)) {
                        if (query.isEmpty()) Text("Search for a city or airport", color = Color(0xFF8E8E93), fontSize = 17.sp, maxLines = 1)
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            textStyle = TextStyle(fontFamily = Inter, color = Color.White, fontSize = 17.sp),
                            cursorBrush = SolidColor(Color(0xFF0A84FF)),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester).onFocusChanged { if (it.isFocused) searching = true },
                        )
                    }
                    if (query.isNotEmpty()) Icon(
                        Icons.Filled.Cancel, null, tint = Color(0xFF8E8E93),
                        modifier = Modifier.size(18.dp).clickable { query = "" },
                    )
                }
                AnimatedVisibility(searching) {
                    Text(
                        "Cancel", color = Color.White, fontSize = 17.sp,
                        modifier = Modifier.padding(start = 12.dp).clickable { query = ""; searching = false; focus.clearFocus() },
                    )
                }
            }

            Box(Modifier.weight(1f)) {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 40.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize().navigationBarsPadding(),
                ) {
                    if (!locationEnabled) item("enable") {
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFF1C1C1E)).clickable(onClick = onEnableLocation).padding(16.dp)
                        ) {
                            Text("Turn on Location Services", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                            Text("See weather for your current location.", color = Secondary, fontSize = 14.sp)
                        }
                    }
                    items(places.size, key = { places[it].id }) { i ->
                        val p = places[i]
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (editing && !p.isCurrent) {
                                Icon(
                                    Icons.Filled.RemoveCircle, null, tint = Color(0xFFFF453A),
                                    modifier = Modifier.padding(end = 10.dp).size(26.dp).clickable { onRemove(p) },
                                )
                            }
                            SwipeToDelete(enabled = !p.isCurrent && !editing, onDelete = { onRemove(p) }, modifier = Modifier.weight(1f)) {
                                LocationCard(p, weather[p.id]) { if (!editing) onOpen(i) }
                            }
                            if (editing && !p.isCurrent) {
                                val first = places.indexOfFirst { !it.isCurrent }
                                Column(Modifier.padding(start = 6.dp)) {
                                    Icon(Icons.Filled.KeyboardArrowUp, null, tint = if (i > first) Color.White else Color.DarkGray,
                                        modifier = Modifier.size(28.dp).clickable(enabled = i > first) { onMove(i, i - 1) })
                                    Icon(Icons.Filled.KeyboardArrowDown, null, tint = if (i < places.size - 1) Color.White else Color.DarkGray,
                                        modifier = Modifier.size(28.dp).clickable(enabled = i < places.size - 1) { onMove(i, i + 1) })
                                }
                            }
                        }
                    }
                    item("attribution") {
                        Text(
                            "Learn more about weather data · Open-Meteo, NWS, RainViewer",
                            color = Color(0xFF8E8E93), fontSize = 12.sp, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        )
                    }
                }

                if (searching) {
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = if (query.isEmpty()) 0.7f else 1f)).clickable(enabled = query.isEmpty()) {
                        searching = false; focus.clearFocus()
                    }) {
                        LazyColumn(Modifier.fillMaxSize().imePadding(), contentPadding = PaddingValues(horizontal = 16.dp)) {
                            items(results, key = { it.id }) { r ->
                                Column(Modifier.fillMaxWidth().clickable { focus.clearFocus(); onPreview(r) }) {
                                    Text(highlight(r.fullName, query), fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(vertical = 12.dp))
                                    HorizontalDivider(color = Color(0xFF2C2C2E), thickness = 0.5.dp)
                                }
                            }
                            if (query.isNotBlank() && results.isEmpty()) item {
                                Text("No Results", color = Secondary, fontSize = 17.sp, modifier = Modifier.padding(vertical = 24.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (unitsSheet) UnitsSheet(settings, onSettings) { unitsSheet = false }
    if (notifSheet) NotificationsSheet(notif, onNotif) { notifSheet = false }
}

private fun highlight(text: String, q: String) = buildAnnotatedString {
    val idx = text.indexOf(q.trim(), ignoreCase = true)
    if (q.isBlank() || idx < 0) { withStyle(SpanStyle(color = Color(0xFF8E8E93))) { append(text) }; return@buildAnnotatedString }
    withStyle(SpanStyle(color = Color(0xFF8E8E93))) { append(text.substring(0, idx)) }
    withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.SemiBold)) { append(text.substring(idx, idx + q.trim().length)) }
    withStyle(SpanStyle(color = Color(0xFF8E8E93))) { append(text.substring(idx + q.trim().length)) }
}

@Composable
private fun SettingsMenu(expanded: Boolean, dismiss: () -> Unit, s: Settings, onSettings: (Settings) -> Unit, onEdit: () -> Unit, onUnits: () -> Unit, onNotifications: () -> Unit, onWidgets: () -> Unit) {
    DropdownMenu(expanded, dismiss, containerColor = Color(0xFF252527), shape = RoundedCornerShape(14.dp), modifier = Modifier.width(230.dp)) {
        DropdownMenuItem(text = { Text("Edit List", color = Color.White) }, onClick = { dismiss(); onEdit() })
        DropdownMenuItem(text = { Text("Notifications", color = Color.White) }, onClick = { dismiss(); onNotifications() })
        DropdownMenuItem(text = { Text("Add Widget", color = Color.White) }, onClick = { dismiss(); onWidgets() })
        HorizontalDivider(color = Color(0xFF3A3A3C))
        DropdownMenuItem(
            text = { Text("Celsius", color = Color.White) },
            leadingIcon = { if (!s.fahrenheit) Icon(Icons.Filled.Check, null, tint = Color.White) else Spacer(Modifier.size(24.dp)) },
            trailingIcon = { Text("°C", color = Color.White, fontSize = 17.sp) },
            onClick = { onSettings(s.copy(fahrenheit = false)); dismiss() },
        )
        DropdownMenuItem(
            text = { Text("Fahrenheit", color = Color.White) },
            leadingIcon = { if (s.fahrenheit) Icon(Icons.Filled.Check, null, tint = Color.White) else Spacer(Modifier.size(24.dp)) },
            trailingIcon = { Text("°F", color = Color.White, fontSize = 17.sp) },
            onClick = { onSettings(s.copy(fahrenheit = true)); dismiss() },
        )
        HorizontalDivider(color = Color(0xFF3A3A3C))
        DropdownMenuItem(text = { Text("Units", color = Color.White) }, onClick = { dismiss(); onUnits() })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnitsSheet(s: Settings, onSettings: (Settings) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF1C1C1E), contentColor = Color.White,
        shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
    ) {
        LazyColumn(Modifier.fillMaxHeight(0.9f).padding(horizontal = 16.dp)) {
            item {
                Box(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                    Text("Units", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.Center))
                    Text("Done", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF0A84FF),
                        modifier = Modifier.align(Alignment.CenterEnd).clickable(onClick = onDismiss))
                }
            }
            item { UnitGroup("TEMPERATURE", listOf("Fahrenheit (°F)" to true, "Celsius (°C)" to false), s.fahrenheit) { onSettings(s.copy(fahrenheit = it)) } }
            item { UnitGroup("WIND", WindUnit.entries.map { (if (it == WindUnit.Beaufort) "Beaufort Scale" else it.label) to it }, s.wind) { onSettings(s.copy(wind = it)) } }
            item { UnitGroup("PRECIPITATION", PrecipUnit.entries.map { it.label to it }, s.precip) { onSettings(s.copy(precip = it)) } }
            item { UnitGroup("PRESSURE", PressureUnit.entries.map { it.label to it }, s.pressure) { onSettings(s.copy(pressure = it)) } }
            item { UnitGroup("DISTANCE", DistanceUnit.entries.map { it.label to it }, s.distance) { onSettings(s.copy(distance = it)) } }
            item { Spacer(Modifier.height(40.dp)) }
        }
    }
}

@Composable
private fun <T> UnitGroup(title: String, options: List<Pair<String, T>>, selected: T, onSelect: (T) -> Unit) {
    Text(title, color = Color(0xFF8E8E93), fontSize = 13.sp, modifier = Modifier.padding(start = 16.dp, top = 18.dp, bottom = 6.dp))
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFF2C2C2E))) {
        options.forEachIndexed { i, (label, v) ->
            if (i > 0) HorizontalDivider(color = Color(0xFF3A3A3C), thickness = 0.5.dp, modifier = Modifier.padding(start = 16.dp))
            Row(Modifier.fillMaxWidth().clickable { onSelect(v) }.padding(horizontal = 16.dp).height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(label, color = Color.White, fontSize = 17.sp, modifier = Modifier.weight(1f))
                if (v == selected) Icon(Icons.Filled.Check, null, tint = Color(0xFF0A84FF), modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
fun LocationCard(p: Place, w: Weather?, onClick: () -> Unit) {
    val u = LocalUnits.current
    val scene = Scene.of(w)
    Box(Modifier.fillMaxWidth().height(117.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick)) {
        CompositionLocalProvider(LocalAnimate provides false) { SkyBackground(scene) }
        Row(Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 10.dp)) {
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Text(if (p.isCurrent) "My Location" else p.name, color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val sub = if (p.isCurrent) p.name else w?.let {
                    LocalTime.now(it.zone).format(DateTimeFormatter.ofPattern(if (u.use24h) "HH:mm" else "h:mm a", Locale.US))
                } ?: ""
                Text(sub, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                Spacer(Modifier.weight(1f))
                Text(w?.condition?.text ?: "", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            }
            Column(Modifier.fillMaxHeight(), horizontalAlignment = Alignment.End) {
                Text(w?.let { u.temp(it.current.temp) } ?: "--", color = Color.White, fontSize = 50.sp, fontFamily = InterDisplay, fontWeight = FontWeight.Light, lineHeight = 54.sp)
                Spacer(Modifier.weight(1f))
                Text(w?.let { "H:${u.temp(it.today.high)}  L:${u.temp(it.today.low)}" } ?: "", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun SwipeToDelete(enabled: Boolean, onDelete: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val reveal = with(density) { 84.dp.toPx() }
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    Box(modifier) {
        if (offset.value < -1f) {
            Box(
                Modifier.matchParentSize().clip(RoundedCornerShape(16.dp)).background(Color(0xFFFF3B30)).clickable { onDelete() },
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(Icons.Filled.Delete, null, tint = Color.White, modifier = Modifier.padding(end = 30.dp).size(26.dp))
            }
        }
        Box(
            Modifier
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .draggable(
                    enabled = enabled,
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { d -> scope.launch { offset.snapTo((offset.value + d).coerceIn(-reveal * 3, 0f)) } },
                    onDragStopped = { v ->
                        when {
                            offset.value < -reveal * 2 -> onDelete()
                            offset.value < -reveal / 2 || v < -800 -> offset.animateTo(-reveal)
                            else -> offset.animateTo(0f)
                        }
                    },
                )
        ) { content() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotificationsSheet(n: com.weatherclone.app.data.NotifPrefs, onChange: (com.weatherclone.app.data.NotifPrefs) -> Unit, onDismiss: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val permission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { }
    fun set(v: com.weatherclone.app.data.NotifPrefs) {
        if (android.os.Build.VERSION.SDK_INT >= 33 && !com.weatherclone.app.background.Notifier.canPost(ctx)) {
            permission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        onChange(v)
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF1C1C1E), contentColor = Color.White,
        shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
    ) {
        Column(Modifier.padding(horizontal = 16.dp).navigationBarsPadding()) {
            Box(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                Text("Notifications", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.Center))
                Text("Done", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF0A84FF),
                    modifier = Modifier.align(Alignment.CenterEnd).clickable(onClick = onDismiss))
            }
            Text("Notifications follow your current location, or your first saved city.", color = Color(0xFF8E8E93), fontSize = 13.sp,
                modifier = Modifier.padding(start = 16.dp, bottom = 6.dp, end = 16.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0xFF2C2C2E))) {
                ToggleRow("Severe Weather", "Government warnings for your area", n.severe) { set(n.copy(severe = it)) }
                HorizontalDivider(color = Color(0xFF3A3A3C), thickness = 0.5.dp, modifier = Modifier.padding(start = 16.dp))
                ToggleRow("Next-Hour Precipitation", "When rain or snow is about to start", n.rain) { set(n.copy(rain = it)) }
                HorizontalDivider(color = Color(0xFF3A3A3C), thickness = 0.5.dp, modifier = Modifier.padding(start = 16.dp))
                ToggleRow("Daily Forecast", "A summary of the day's weather", n.daily) { set(n.copy(daily = it)) }
                if (n.daily) {
                    HorizontalDivider(color = Color(0xFF3A3A3C), thickness = 0.5.dp, modifier = Modifier.padding(start = 16.dp))
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Time", color = Color.White, fontSize = 17.sp, modifier = Modifier.weight(1f))
                        Icon(Icons.Filled.KeyboardArrowDown, "Earlier", tint = Color.White, modifier = Modifier.size(28.dp).clickable { onChange(n.copy(hour = (n.hour + 23) % 24)) })
                        val h12 = if (n.hour % 12 == 0) 12 else n.hour % 12
                        Text("$h12:00 ${if (n.hour < 12) "AM" else "PM"}", color = Color(0xFF0A84FF), fontSize = 17.sp, modifier = Modifier.padding(horizontal = 8.dp))
                        Icon(Icons.Filled.KeyboardArrowUp, "Later", tint = Color.White, modifier = Modifier.size(28.dp).clickable { onChange(n.copy(hour = (n.hour + 1) % 24)) })
                    }
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 17.sp)
            Text(subtitle, color = Color(0xFF8E8E93), fontSize = 13.sp)
        }
        androidx.compose.material3.Switch(
            checked = checked, onCheckedChange = onChange,
            colors = androidx.compose.material3.SwitchDefaults.colors(
                checkedThumbColor = Color.White, checkedTrackColor = Color(0xFF34C759), checkedBorderColor = Color.Transparent,
                uncheckedThumbColor = Color.White, uncheckedTrackColor = Color(0xFF39393D), uncheckedBorderColor = Color.Transparent,
            ),
        )
    }
}

@Composable
private fun WidgetMenu(expanded: Boolean, dismiss: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    fun pin(cls: Class<*>) {
        val mgr = android.appwidget.AppWidgetManager.getInstance(ctx)
        if (mgr.isRequestPinAppWidgetSupported) mgr.requestPinAppWidget(android.content.ComponentName(ctx, cls), null, null)
        dismiss()
    }
    DropdownMenu(expanded, dismiss, containerColor = Color(0xFF252527), shape = RoundedCornerShape(14.dp), modifier = Modifier.width(230.dp)) {
        DropdownMenuItem(text = { Text("Small", color = Color.White) }, onClick = { pin(com.weatherclone.app.glance.SmallWidgetReceiver::class.java) })
        DropdownMenuItem(text = { Text("Medium", color = Color.White) }, onClick = { pin(com.weatherclone.app.glance.MediumWidgetReceiver::class.java) })
        DropdownMenuItem(text = { Text("Large", color = Color.White) }, onClick = { pin(com.weatherclone.app.glance.LargeWidgetReceiver::class.java) })
        DropdownMenuItem(text = { Text("At a Glance", color = Color.White) }, onClick = { pin(com.weatherclone.app.glance.GlanceWidgetReceiver::class.java) })
    }
}