package com.weatherclone.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weatherclone.app.data.Place
import com.weatherclone.app.data.Weather
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.launch

private enum class Screen { Pager, List, Map }

@Composable
fun WeatherApp(vm: WeatherViewModel, requestLocation: () -> Unit) {
    val places = vm.places
    var screen by rememberSaveable { mutableStateOf(if (places.isEmpty()) Screen.List else Screen.Pager) }
    var mapStart by remember { mutableStateOf<Weather?>(null) }
    var preview by remember { mutableStateOf<Place?>(null) }
    val pager = rememberPagerState(initialPage = vm.lastPage.coerceIn(0, (places.size - 1).coerceAtLeast(0))) { vm.places.size }
    val scope = rememberCoroutineScope()

    LaunchedEffect(pager) { snapshotFlow { pager.currentPage }.collect { vm.lastPage = it } }
    LaunchedEffect(places.size) { if (places.isEmpty() && screen == Screen.Pager) screen = Screen.List }
    // First launch: jump to the forecast as soon as there is a location to show.
    var autoOpened by rememberSaveable { mutableStateOf(places.isNotEmpty()) }
    LaunchedEffect(places.isEmpty()) {
        if (places.isNotEmpty() && !autoOpened) { autoOpened = true; screen = Screen.Pager }
    }

    BackHandler(screen != Screen.List) { screen = if (screen == Screen.Map) Screen.Pager else Screen.List }

    AnimatedContent(
        targetState = screen,
        transitionSpec = {
            if (targetState == Screen.Pager) (fadeIn(tween(250)) + scaleIn(tween(250), initialScale = 0.92f)) togetherWith fadeOut(tween(200))
            else (fadeIn(tween(250))) togetherWith (fadeOut(tween(200)) + scaleOut(tween(250), targetScale = 0.95f))
        },
        label = "screen",
    ) { s ->
        when (s) {
            Screen.Pager -> Box(Modifier.fillMaxSize().background(Color.Black)) {
                val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 50.dp
                val barHaze = rememberHazeState()
                HorizontalPager(pager, key = { vm.places.getOrNull(it)?.id ?: it }, modifier = Modifier.fillMaxSize().hazeSource(barHaze)) { page ->
                    val p = vm.places.getOrNull(page) ?: return@HorizontalPager
                    LaunchedEffect(p.id) { vm.load(p) }
                    LocationPage(
                        place = p,
                        weather = vm.weather[p.id],
                        error = vm.failed[p.id] == true,
                        radar = vm.radar,
                        bottomInset = bottomInset,
                        onOpenMap = { mapStart = it; screen = Screen.Map },
                    )
                }
                BottomBar(
                    count = places.size,
                    page = pager.currentPage,
                    firstIsCurrent = places.firstOrNull()?.isCurrent == true,
                    onMap = { mapStart = vm.places.getOrNull(pager.currentPage)?.let { vm.weather[it.id] }; screen = Screen.Map },
                    onList = { screen = Screen.List },
                    modifier = Modifier.align(Alignment.BottomCenter).hazeBlur(
                        input = HazeInput.Backdrop(barHaze),
                        style = remember { HazeBlurStyle { blurRadius(24.dp); noiseFactor(0f); colorEffects(listOf(HazeColorEffect.tint(Color.Black.copy(alpha = 0.12f)))) } },
                    ),
                )
            }
            Screen.List -> ListScreen(
                places = places,
                weather = vm.weather,
                settings = vm.settings,
                locationEnabled = vm.locationEnabled,
                onOpen = { i -> scope.launch { pager.scrollToPage(i) }; screen = Screen.Pager },
                onRemove = { vm.remove(it) },
                onMove = { a, b -> vm.move(a, b) },
                onSettings = { vm.updateSettings(it) },
                onEnableLocation = requestLocation,
                search = { vm.search(it) },
                onPreview = { preview = it },
                notif = vm.notif,
                onNotif = { vm.updateNotif(it) },
            )
            Screen.Map -> MapScreen(
                weathers = places.mapNotNull { vm.weather[it.id] },
                start = mapStart,
                radar = vm.radar,
                grids = vm.gridCache,
                onDone = { screen = Screen.Pager },
                onList = { screen = Screen.List },
            )
        }
    }

    preview?.let { p ->
        PreviewSheet(
            place = p,
            alreadyAdded = places.any { it.id == p.id },
            load = { vm.preview(p) },
            radar = vm.radar,
            onAdd = { w -> vm.addWithWeather(p, w); preview = null },
            onDismiss = { preview = null },
        )
    }
}

@Composable
private fun BottomBar(count: Int, page: Int, firstIsCurrent: Boolean, onMap: () -> Unit, onList: () -> Unit, modifier: Modifier) {
    Column(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(Color.White.copy(alpha = 0.18f)))
        Row(
            Modifier.fillMaxWidth().height(50.dp).padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Map, "Map", tint = Color.White, modifier = Modifier.size(26.dp).clickable(onClick = onMap))
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                val shown = count.coerceAtMost(15)
                for (i in 0 until shown) {
                    val sel = i == page
                    val color = if (sel) Color.White else Color.White.copy(alpha = 0.4f)
                    if (i == 0 && firstIsCurrent) {
                        Canvas(Modifier.padding(horizontal = 4.dp).size(9.dp)) { locationArrow(color) }
                    } else {
                        Box(Modifier.padding(horizontal = 4.dp).size(7.dp).clip(CircleShape).background(color))
                    }
                }
            }
            Icon(Icons.AutoMirrored.Filled.FormatListBulleted, "Locations", tint = Color.White, modifier = Modifier.size(26.dp).clickable(onClick = onList))
        }
        Spacer(Modifier.navigationBarsPadding())
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PreviewSheet(
    place: Place,
    alreadyAdded: Boolean,
    load: suspend () -> Weather?,
    radar: Radar?,
    onAdd: (Weather?) -> Unit,
    onDismiss: () -> Unit,
) {
    var w by remember(place.id) { mutableStateOf<Weather?>(null) }
    var failed by remember(place.id) { mutableStateOf(false) }
    LaunchedEffect(place.id) { w = load(); failed = w == null }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color.Black,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
    ) {
        Box(Modifier.fillMaxWidth().fillMaxHeight(0.96f).clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))) {
            LocationPage(place, w, failed, radar, bottomInset = 0.dp, onOpenMap = {}, topInset = 8.dp)
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
                Text("Cancel", color = Color.White, fontSize = 17.sp, modifier = Modifier.clickable(onClick = onDismiss))
                Spacer(Modifier.weight(1f))
                if (!alreadyAdded) Text("Add", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { onAdd(w) })
            }
        }
    }
}
