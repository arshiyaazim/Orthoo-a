package com.orthoo.ai

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.orthoo.ai.ui.navigation.AboutRoute
import com.orthoo.ai.ui.navigation.BenchmarkRoute
import com.orthoo.ai.ui.navigation.ChatRoute
import com.orthoo.ai.ui.navigation.ModelsRoute
import com.orthoo.ai.ui.navigation.OnboardingRoute
import com.orthoo.ai.ui.navigation.PalsRoute
import com.orthoo.ai.ui.navigation.SettingsRoute
import com.orthoo.ai.ui.screens.about.AboutScreen
import com.orthoo.ai.ui.screens.benchmark.BenchmarkScreen
import com.orthoo.ai.ui.screens.chat.ChatScreen
import com.orthoo.ai.ui.screens.chat.ChatSidebarContent
import com.orthoo.ai.ui.screens.models.ModelsScreen
import com.orthoo.ai.ui.screens.onboarding.OnboardingScreen
import com.orthoo.ai.ui.screens.pals.PalsScreen
import com.orthoo.ai.ui.screens.settings.SettingsScreen
import com.orthoo.ai.ui.theme.OrthooTheme
import com.orthoo.ai.ui.viewmodel.AppViewModelFactory
import com.orthoo.ai.ui.viewmodel.BenchmarkViewModel
import com.orthoo.ai.ui.viewmodel.ChatViewModel
import com.orthoo.ai.ui.viewmodel.MainViewModel
import com.orthoo.ai.ui.viewmodel.ModelsViewModel
import com.orthoo.ai.ui.viewmodel.PalsViewModel
import com.orthoo.ai.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {

    private var pendingIntentRepo by mutableStateOf<String?>(null)

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        pendingIntentRepo = extractDeepLinkRepo(intent)
        val container = (application as OrthooApplication).container
        val startOnboardingCompleted = runCatching {
            runBlocking { container.preferences.settings.first().hasCompletedOnboarding }
        }.getOrDefault(false)

        setContent {
            val widthSizeClass = calculateWindowSizeClass(this).widthSizeClass
            val factory = remember { AppViewModelFactory(container) }

            val mainViewModel: MainViewModel = viewModel(factory = factory)
            val chatViewModel: ChatViewModel = viewModel(factory = factory)
            val modelsViewModel: ModelsViewModel = viewModel(factory = factory)
            val palsViewModel: PalsViewModel = viewModel(factory = factory)
            val benchmarkViewModel: BenchmarkViewModel = viewModel(factory = factory)
            val settingsViewModel: SettingsViewModel = viewModel(factory = factory)

            LaunchedEffect(pendingIntentRepo) {
                pendingIntentRepo?.let { repo ->
                    mainViewModel.handleDeepLinkRepo(repo)
                    pendingIntentRepo = null
                }
            }

            val settings by mainViewModel.settings.collectAsStateWithLifecycle()

            OrthooTheme(themeMode = settings.themeMode) {
                OrthooRootShell(
                    isExpandedWidth = widthSizeClass == WindowWidthSizeClass.Expanded,
                    startCompletedOnboarding = startOnboardingCompleted,
                    container = container,
                    mainViewModel = mainViewModel,
                    chatViewModel = chatViewModel,
                    modelsViewModel = modelsViewModel,
                    palsViewModel = palsViewModel,
                    benchmarkViewModel = benchmarkViewModel,
                    settingsViewModel = settingsViewModel
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pendingIntentRepo = extractDeepLinkRepo(intent)
    }

    private fun extractDeepLinkRepo(intent: Intent?): String? {
        val data: Uri = intent?.data ?: return null
        if (data.scheme != "orthoo") return null
        val repoParam = data.getQueryParameter("repo") ?: data.getQueryParameter("id")
        if (!repoParam.isNullOrBlank()) return repoParam
        val path = data.path?.trim('/')
        return path?.takeIf { it.isNotBlank() }
    }
}

@Composable
private fun OrthooRootShell(
    isExpandedWidth: Boolean,
    startCompletedOnboarding: Boolean,
    container: com.orthoo.ai.di.AppContainer,
    mainViewModel: MainViewModel,
    chatViewModel: ChatViewModel,
    modelsViewModel: ModelsViewModel,
    palsViewModel: PalsViewModel,
    benchmarkViewModel: BenchmarkViewModel,
    settingsViewModel: SettingsViewModel
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val isOnboarding = currentDestination?.hasRoute<OnboardingRoute>() == true

    val pendingRepo by mainViewModel.pendingDeepLinkRepo.collectAsStateWithLifecycle()
    LaunchedEffect(pendingRepo, isOnboarding) {
        if (!pendingRepo.isNullOrBlank() && !isOnboarding) {
            navController.navigate(ModelsRoute) {
                launchSingleTop = true
            }
        }
    }

    var pendingMicAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            pendingMicAction?.invoke()
        } else {
            Toast.makeText(
                context,
                context.getString(R.string.mic_permission_rationale),
                Toast.LENGTH_LONG
            ).show()
        }
        pendingMicAction = null
    }

    val requestMicAndRun: (() -> Unit) -> Unit = { action ->
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            action()
        } else {
            pendingMicAction = action
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(4)
    ) { uris ->
        chatViewModel.addAttachedImages(uris.map { it.toString() })
    }

    val ggufPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { modelsViewModel.importLocalGguf(context, it) }
    }

    val groupedSessions by chatViewModel.groupedSessions.collectAsStateWithLifecycle()
    val activeSessionId by chatViewModel.selectedSessionId.collectAsStateWithLifecycle()
    val sidebarQuery by chatViewModel.sidebarQuery.collectAsStateWithLifecycle()
    val selectedSessionIds by chatViewModel.selectedSessionIds.collectAsStateWithLifecycle()

    fun navigateTopLevel(route: Any) {
        scope.launch { drawerState.close() }
        navController.navigate(route) {
            popUpTo(ChatRoute) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    val sidebarContent: @Composable () -> Unit = {
        ChatSidebarContent(
            groupedSessions = groupedSessions,
            activeSessionId = activeSessionId,
            searchQuery = sidebarQuery,
            selectedIds = selectedSessionIds,
            onSearchQueryChange = { chatViewModel.updateSidebarQuery(it) },
            onNewChat = {
                chatViewModel.createNewChat()
                navigateTopLevel(ChatRoute)
            },
            onSelectSession = { sid ->
                chatViewModel.selectSession(sid)
                navigateTopLevel(ChatRoute)
            },
            onToggleSelectSession = { chatViewModel.toggleSessionSelection(it) },
            onClearSelection = { chatViewModel.clearSessionSelection() },
            onDeleteSelected = { chatViewModel.deleteSelectedSessions() },
            onRenameSession = { id, title -> chatViewModel.renameSession(id, title) },
            onTogglePinSession = { chatViewModel.togglePinSession(it) },
            onDuplicateSession = { chatViewModel.duplicateSession(it) },
            onDeleteSession = { chatViewModel.deleteSession(it) },
            onNavigatePals = { navigateTopLevel(PalsRoute) },
            onNavigateModels = { navigateTopLevel(ModelsRoute) },
            onNavigateBenchmark = { navigateTopLevel(BenchmarkRoute) },
            onNavigateSettings = { navigateTopLevel(SettingsRoute) },
            onNavigateAbout = { navigateTopLevel(AboutRoute) }
        )
    }

    val navGraphContent: @Composable (Modifier) -> Unit = { hostModifier ->
        NavHost(
            navController = navController,
            startDestination = if (startCompletedOnboarding) ChatRoute else OnboardingRoute,
            modifier = hostModifier
        ) {
            composable<OnboardingRoute> {
                OnboardingScreen(
                    onFinish = { topic, openModels ->
                        mainViewModel.completeOnboarding(topic)
                        val target: Any = if (openModels) ModelsRoute else ChatRoute
                        navController.navigate(target) {
                            popUpTo<OnboardingRoute> { inclusive = true }
                        }
                    }
                )
            }
            composable<ChatRoute> {
                ChatScreen(
                    viewModel = chatViewModel,
                    showMenuButton = !isExpandedWidth,
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    onNavigateModels = { navigateTopLevel(ModelsRoute) },
                    onPickImages = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onRequestMicPermission = requestMicAndRun,
                    onCancelDownload = { modelsViewModel.cancelDownload(it) }
                )
            }
            composable<PalsRoute> {
                PalsScreen(
                    viewModel = palsViewModel,
                    showMenuButton = !isExpandedWidth,
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    onStartChatWithPal = { pal ->
                        chatViewModel.createNewChat(palId = pal.id, modelId = pal.defaultModelId)
                        navigateTopLevel(ChatRoute)
                    }
                )
            }
            composable<ModelsRoute> {
                ModelsScreen(
                    viewModel = modelsViewModel,
                    showMenuButton = !isExpandedWidth,
                    initialHfRepoId = pendingRepo,
                    onConsumedInitialHfRepo = { mainViewModel.consumeDeepLinkRepo() },
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    onImportLocalGguf = { ggufPickerLauncher.launch(arrayOf("*/*")) },
                    onNavigateToChat = { navigateTopLevel(ChatRoute) }
                )
            }
            composable<BenchmarkRoute> {
                BenchmarkScreen(
                    viewModel = benchmarkViewModel,
                    showMenuButton = !isExpandedWidth,
                    onOpenDrawer = { scope.launch { drawerState.open() } }
                )
            }
            composable<SettingsRoute> {
                SettingsScreen(
                    viewModel = settingsViewModel,
                    showMenuButton = !isExpandedWidth,
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    onReplayOnboarding = {
                        navController.navigate(OnboardingRoute)
                    }
                )
            }
            composable<AboutRoute> {
                AboutScreen(
                    hardwareInfoProvider = container.hardwareInfoProvider,
                    showMenuButton = !isExpandedWidth,
                    onOpenDrawer = { scope.launch { drawerState.open() } }
                )
            }
        }
    }

    if (isOnboarding) {
        navGraphContent(Modifier.fillMaxSize())
    } else if (isExpandedWidth) {
        Row(modifier = Modifier.fillMaxSize()) {
            NavigationRail {
                NavigationRailItem(
                    selected = currentDestination?.hasRoute<ChatRoute>() == true,
                    onClick = { navigateTopLevel(ChatRoute) },
                    icon = { Icon(Icons.AutoMirrored.Outlined.Chat, contentDescription = "Chat") },
                    label = { Text("Chat") }
                )
                NavigationRailItem(
                    selected = currentDestination?.hasRoute<PalsRoute>() == true,
                    onClick = { navigateTopLevel(PalsRoute) },
                    icon = { Icon(Icons.Outlined.Face, contentDescription = "Pals") },
                    label = { Text("Pals") }
                )
                NavigationRailItem(
                    selected = currentDestination?.hasRoute<ModelsRoute>() == true,
                    onClick = { navigateTopLevel(ModelsRoute) },
                    icon = { Icon(Icons.Outlined.FolderOpen, contentDescription = "Models") },
                    label = { Text("Models") }
                )
                NavigationRailItem(
                    selected = currentDestination?.hasRoute<BenchmarkRoute>() == true,
                    onClick = { navigateTopLevel(BenchmarkRoute) },
                    icon = { Icon(Icons.Outlined.Speed, contentDescription = "Benchmark") },
                    label = { Text("Bench") }
                )
                NavigationRailItem(
                    selected = currentDestination?.hasRoute<SettingsRoute>() == true,
                    onClick = { navigateTopLevel(SettingsRoute) },
                    icon = { Icon(Icons.Outlined.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") }
                )
                NavigationRailItem(
                    selected = currentDestination?.hasRoute<AboutRoute>() == true,
                    onClick = { navigateTopLevel(AboutRoute) },
                    icon = { Icon(Icons.Outlined.Info, contentDescription = "Info") },
                    label = { Text("Info") }
                )
            }
            VerticalDivider()
            sidebarContent()
            VerticalDivider()
            navGraphContent(Modifier.weight(1f))
        }
    } else {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet {
                    sidebarContent()
                }
            }
        ) {
            navGraphContent(Modifier.fillMaxSize())
        }
    }
}
