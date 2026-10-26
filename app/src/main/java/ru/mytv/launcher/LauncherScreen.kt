package ru.mytv.launcher

import android.provider.Settings
import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Collections
import java.util.Locale

private val Ru = Locale("ru", "RU")

// ---------------------------------------------------------------------------
// Нажатия с пульта: короткое OK — открыть, долгое OK или кнопка «Меню» — меню.
// ---------------------------------------------------------------------------

private class KeyState {
    var down = false
    var long = false
}

fun Modifier.tvClickable(
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier = composed {
    val state = remember { KeyState() }
    val interaction = remember { MutableInteractionSource() }
    this
        .onPreviewKeyEvent { event ->
            val native = event.nativeKeyEvent
            val code = native.keyCode
            val isSelect = code == AndroidKeyEvent.KEYCODE_DPAD_CENTER ||
                code == AndroidKeyEvent.KEYCODE_ENTER ||
                code == AndroidKeyEvent.KEYCODE_NUMPAD_ENTER
            when {
                code == AndroidKeyEvent.KEYCODE_MENU && onLongClick != null -> {
                    if (event.type == KeyEventType.KeyUp) onLongClick()
                    true
                }
                isSelect && event.type == KeyEventType.KeyDown -> {
                    if (native.repeatCount == 0) {
                        state.down = true
                        state.long = false
                    } else if (state.down && !state.long && onLongClick != null &&
                        native.eventTime - native.downTime >= 500
                    ) {
                        state.long = true
                        state.down = false
                        onLongClick()
                    }
                    true
                }
                isSelect && event.type == KeyEventType.KeyUp -> {
                    // Клик засчитывается, только если и нажатие было здесь же.
                    if (state.down && !state.long) onClick()
                    state.down = false
                    state.long = false
                    true
                }
                else -> false
            }
        }
        .clickable(interactionSource = interaction, indication = null, onClick = onClick)
}

// ---------------------------------------------------------------------------
// Главный экран
// ---------------------------------------------------------------------------

@Composable
fun LauncherScreen(resumeTick: Int) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }

    var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var favorites by remember { mutableStateOf<List<String>>(emptyList()) }
    var themeMode by remember { mutableStateOf(prefs.theme) }
    var accentValue by remember { mutableLongStateOf(prefs.accent) }
    var now by remember { mutableStateOf(Calendar.getInstance()) }
    var menuApp by remember { mutableStateOf<AppInfo?>(null) }
    var showAppearance by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    var didInitialFocus by remember { mutableStateOf(false) }
    val firstFocus = remember { FocusRequester() }

    // Загружаем приложения при запуске и каждый раз при возврате на главный экран.
    LaunchedEffect(resumeTick) {
        val list = withContext(Dispatchers.Default) { Apps.load(context) }
        val installed = list.map { it.packageName }.toSet()
        val saved = prefs.favorites
        val favs = (saved ?: Apps.defaultFavorites(list)).filter { it in installed }
        if (saved == null && list.isNotEmpty()) prefs.favorites = favs
        apps = list
        favorites = favs
        now = Calendar.getInstance()
        loaded = true
    }

    // Часы.
    LaunchedEffect(Unit) {
        while (true) {
            delay(5_000)
            now = Calendar.getInstance()
        }
    }

    // Фокус на первую плитку после загрузки.
    LaunchedEffect(loaded) {
        if (loaded && !didInitialFocus) {
            delay(100)
            runCatching { firstFocus.requestFocus() }
            didInitialFocus = true
        }
    }

    // Кнопка «Назад» на главном экране ничего не делает.
    BackHandler(enabled = true) { }

    fun saveFavorites(new: List<String>) {
        favorites = new
        prefs.favorites = new
    }

    val hour = now.get(Calendar.HOUR_OF_DAY)
    val dark = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> hour < 7 || hour >= 20
    }
    val p = if (dark) DarkPalette else LightPalette
    val accent = Color(accentValue)
    val byPackage = apps.associateBy { it.packageName }
    val favApps = favorites.mapNotNull { byPackage[it] }

    val timeText = SimpleDateFormat("HH:mm", Ru).format(now.time)
    val dateText = SimpleDateFormat("EEEE, d MMMM", Ru).format(now.time)
        .replaceFirstChar { it.titlecase(Ru) }
    val greeting = when (hour) {
        in 5..11 -> "Доброе утро!"
        in 12..17 -> "Добрый день!"
        in 18..22 -> "Добрый вечер!"
        else -> "Доброй ночи!"
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(p.bg)
    ) {
        // Мягкие пятна на фоне, как в макете.
        Box(
            Modifier
                .offset((-100).dp, (-130).dp)
                .size(380.dp)
                .background(p.blob1, CircleShape)
        )
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .offset(80.dp, 150.dp)
                .size(410.dp)
                .background(p.blob2, CircleShape)
        )

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 40.dp, vertical = 26.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // Шапка
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Мой ТВ",
                    color = p.text,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.4).sp,
                )
                Spacer(Modifier.weight(1f))
                HeaderButton(
                    p, accent,
                    icon = if (dark) R.drawable.ic_moon else R.drawable.ic_sun,
                    label = themeLabel(themeMode),
                    description = "Оформление",
                ) { showAppearance = true }
                HeaderButton(p, accent, R.drawable.ic_wifi, null, "Wi‑Fi") {
                    Apps.openSettings(context, Settings.ACTION_WIFI_SETTINGS)
                }
                HeaderButton(p, accent, R.drawable.ic_settings, null, "Настройки") {
                    Apps.openSettings(context, Settings.ACTION_SETTINGS)
                }
            }

            // Часы и подсказка
            val heroShape = RoundedCornerShape(20.dp)
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .shadow(6.dp, heroShape, ambientColor = p.shadow, spotColor = p.shadow)
                    .background(p.hero, heroShape)
                    .padding(horizontal = 36.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(dateText, color = p.sub, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text(
                        timeText,
                        color = p.text,
                        fontSize = 64.sp,
                        lineHeight = 66.sp,
                        fontWeight = FontWeight.Light,
                        letterSpacing = (-2).sp,
                    )
                    Text(greeting, color = p.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
                Column(
                    Modifier
                        .width(230.dp)
                        .background(p.soft, RoundedCornerShape(16.dp))
                        .padding(horizontal = 17.dp, vertical = 15.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("Подсказка", color = p.sub, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Удерживайте OK на приложении, чтобы добавить его в избранное или убрать оттуда",
                        color = p.text,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            // Избранное
            SectionTitle("Избранное", p)
            if (favApps.isEmpty()) {
                Text(
                    if (loaded) "Пока пусто. Выберите приложение ниже и удерживайте OK." else "Загрузка…",
                    color = p.sub,
                    fontSize = 13.sp,
                )
            } else {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(15.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                ) {
                    itemsIndexed(favApps, key = { _, app -> app.packageName }) { index, app ->
                        FavoriteTile(
                            app, p, accent,
                            modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
                            onClick = { Apps.launch(context, app) },
                            onLongClick = { menuApp = app },
                        )
                    }
                }
            }

            // Все приложения
            SectionTitle("Все приложения", p)
            Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
                apps.chunked(8).forEachIndexed { rowIndex, row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                        row.forEachIndexed { i, app ->
                            val base = if (favApps.isEmpty() && rowIndex == 0 && i == 0) {
                                Modifier.focusRequester(firstFocus)
                            } else {
                                Modifier
                            }
                            AppTile(
                                app, p, accent,
                                modifier = base.weight(1f),
                                onClick = { Apps.launch(context, app) },
                                onLongClick = { menuApp = app },
                            )
                        }
                        repeat(8 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }

    // Меню приложения (долгое нажатие OK)
    menuApp?.let { app ->
        val index = favorites.indexOf(app.packageName)
        AppMenuDialog(
            app = app,
            isFavorite = index >= 0,
            canMoveLeft = index > 0,
            canMoveRight = index >= 0 && index < favorites.size - 1,
            p = p,
            accent = accent,
            onDismiss = { menuApp = null },
            onToggleFavorite = {
                saveFavorites(if (index >= 0) favorites - app.packageName else favorites + app.packageName)
                menuApp = null
            },
            onMoveLeft = {
                val list = favorites.toMutableList()
                Collections.swap(list, index, index - 1)
                saveFavorites(list)
                menuApp = null
            },
            onMoveRight = {
                val list = favorites.toMutableList()
                Collections.swap(list, index, index + 1)
                saveFavorites(list)
                menuApp = null
            },
            onInfo = {
                menuApp = null
                Apps.openAppInfo(context, app.packageName)
            },
        )
    }

    // Оформление: тема и цвет подсветки
    if (showAppearance) {
        AppearanceDialog(
            themeMode = themeMode,
            accentValue = accentValue,
            p = p,
            accent = accent,
            onTheme = {
                themeMode = it
                prefs.theme = it
            },
            onAccent = {
                accentValue = it
                prefs.accent = it
            },
            onDismiss = { showAppearance = false },
        )
    }
}

private fun themeLabel(mode: String) = when (mode) {
    "light" -> "Светлая"
    "dark" -> "Тёмная"
    else -> "Авто"
}

// ---------------------------------------------------------------------------
// Элементы экрана
// ---------------------------------------------------------------------------

@Composable
private fun SectionTitle(text: String, p: Palette) {
    Text(
        text,
        color = p.text,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.15).sp,
        modifier = Modifier.padding(bottom = 0.dp),
    )
}

@Composable
private fun AppIcon(app: AppInfo, size: Dp, radius: Dp) {
    Image(
        bitmap = app.icon,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(radius)),
    )
}

@Composable
private fun FavoriteTile(
    app: AppInfo,
    p: Palette,
    accent: Color,
    modifier: Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, label = "favScale")
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = modifier
            .size(width = 128.dp, height = 98.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(if (focused) 14.dp else 3.dp, shape, ambientColor = p.shadow, spotColor = p.shadow)
            .background(p.card, shape)
            .then(if (focused) Modifier.border(3.dp, accent, shape) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .tvClickable(onLongClick = onLongClick, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        AppIcon(app, 46.dp, 13.dp)
        Spacer(Modifier.height(9.dp))
        Text(
            app.label,
            color = p.text,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
    }
}

@Composable
private fun AppTile(
    app: AppInfo,
    p: Palette,
    accent: Color,
    modifier: Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, label = "appScale")
    val shape = RoundedCornerShape(15.dp)
    Column(
        modifier = modifier
            .height(75.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(if (focused) 12.dp else 2.dp, shape, ambientColor = p.shadow, spotColor = p.shadow)
            .background(if (focused) p.card else p.small, shape)
            .then(if (focused) Modifier.border(3.dp, accent, shape) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .tvClickable(onLongClick = onLongClick, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        AppIcon(app, 32.dp, 9.dp)
        Spacer(Modifier.height(6.dp))
        Text(
            app.label,
            color = p.text,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 6.dp),
        )
    }
}

@Composable
private fun HeaderButton(
    p: Palette,
    accent: Color,
    icon: Int,
    label: String?,
    description: String,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(50)
    val fg = if (focused) Color.White else p.sub2
    Row(
        Modifier
            .height(32.dp)
            .then(if (label == null) Modifier.width(32.dp) else Modifier)
            .shadow(2.dp, shape, ambientColor = p.shadow, spotColor = p.shadow)
            .background(if (focused) accent else p.card, shape)
            .onFocusChanged { focused = it.isFocused }
            .tvClickable(onClick = onClick)
            .padding(horizontal = if (label == null) 0.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        Image(
            painter = painterResource(icon),
            contentDescription = description,
            colorFilter = ColorFilter.tint(fg),
            modifier = Modifier.size(15.dp),
        )
        if (label != null) {
            Text(label, color = fg, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ---------------------------------------------------------------------------
// Окна
// ---------------------------------------------------------------------------

@Composable
private fun DialogOption(
    text: String,
    p: Palette,
    accent: Color,
    selected: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(if (focused) accent else p.soft, shape)
            .onFocusChanged { focused = it.isFocused }
            .tvClickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            color = if (focused) Color.White else p.text,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Box(
                Modifier
                    .size(8.dp)
                    .background(if (focused) Color.White else accent, CircleShape)
            )
        }
    }
}

@Composable
private fun AppMenuDialog(
    app: AppInfo,
    isFavorite: Boolean,
    canMoveLeft: Boolean,
    canMoveRight: Boolean,
    p: Palette,
    accent: Color,
    onDismiss: () -> Unit,
    onToggleFavorite: () -> Unit,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
    onInfo: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        val first = remember { FocusRequester() }
        LaunchedEffect(Unit) {
            delay(50)
            runCatching { first.requestFocus() }
        }
        Column(
            Modifier
                .width(300.dp)
                .background(p.card, RoundedCornerShape(22.dp))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                Modifier.padding(bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AppIcon(app, 40.dp, 11.dp)
                Text(
                    app.label,
                    color = p.text,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            DialogOption(
                if (isFavorite) "Убрать из избранного" else "Добавить в избранное",
                p, accent,
                modifier = Modifier.focusRequester(first),
                onClick = onToggleFavorite,
            )
            if (canMoveLeft) DialogOption("Переместить влево", p, accent, onClick = onMoveLeft)
            if (canMoveRight) DialogOption("Переместить вправо", p, accent, onClick = onMoveRight)
            DialogOption("О приложении", p, accent, onClick = onInfo)
        }
    }
}

@Composable
private fun AppearanceDialog(
    themeMode: String,
    accentValue: Long,
    p: Palette,
    accent: Color,
    onTheme: (String) -> Unit,
    onAccent: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        val first = remember { FocusRequester() }
        LaunchedEffect(Unit) {
            delay(50)
            runCatching { first.requestFocus() }
        }
        Column(
            Modifier
                .width(340.dp)
                .background(p.card, RoundedCornerShape(22.dp))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Оформление", color = p.text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text("Тема", color = p.sub, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            val themes = listOf(
                "light" to "Светлая",
                "dark" to "Тёмная",
                "auto" to "Авто: днём светлая, вечером тёмная",
            )
            themes.forEachIndexed { i, (key, label) ->
                DialogOption(
                    label, p, accent,
                    selected = themeMode == key,
                    modifier = if (i == 0) Modifier.focusRequester(first) else Modifier,
                ) { onTheme(key) }
            }
            Text(
                "Цвет подсветки",
                color = p.sub,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 6.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Accents.forEach { value ->
                    AccentDot(Color(value), selected = value == accentValue, p = p) { onAccent(value) }
                }
            }
            Spacer(Modifier.height(4.dp))
            DialogOption("Готово", p, accent, onClick = onDismiss)
        }
    }
}

@Composable
private fun AccentDot(color: Color, selected: Boolean, p: Palette, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .size(36.dp)
            .then(if (focused) Modifier.border(3.dp, p.text, CircleShape) else Modifier)
            .padding(5.dp)
            .background(color, CircleShape)
            .onFocusChanged { focused = it.isFocused }
            .tvClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Box(
                Modifier
                    .size(9.dp)
                    .background(Color.White, CircleShape)
            )
        }
    }
}
