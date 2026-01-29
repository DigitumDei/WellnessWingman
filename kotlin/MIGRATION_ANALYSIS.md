# WellnessWingman: Kotlin Multiplatform Migration Analysis

## Executive Summary

This document provides a comprehensive analysis for migrating WellnessWingman from .NET MAUI to Kotlin Multiplatform (KMP) with Compose Multiplatform.

### Current Stack
- **Framework:** .NET MAUI (.NET 10)
- **Language:** C# with XAML
- **Architecture:** MVVM with CommunityToolkit.Mvvm
- **Data Layer:** Entity Framework Core with SQLite
- **LLM Integration:** OpenAI SDK, Google Gemini SDK
- **Platforms:** Android, iOS, macOS (Catalyst), Windows

### Target Stack
- **Framework:** Kotlin Multiplatform + Compose Multiplatform
- **Language:** Kotlin
- **Architecture:** MVVM/MVI with Compose state management
- **Data Layer:** SQLDelight with SQLite
- **LLM Integration:** openai-kotlin, Ktor HTTP client
- **Platforms:** Android, iOS, Desktop (JVM), optionally Web (Wasm)

### Migration Scope
| Component | Count |
|-----------|-------|
| UI Pages/Screens | 12 |
| Services | 30+ |
| Data Models | 15+ |
| Test Files | 10 |
| Platform-specific implementations | 20+ |

---

## 1. Platform Target Mapping

| MAUI Target | KMP Target | Runtime | Notes |
|-------------|------------|---------|-------|
| `net10.0-android` | `androidMain` | ART/Dalvik | Native Android, full feature parity |
| `net10.0-ios` | `iosMain` | Kotlin/Native | iOS via LLVM compilation |
| `net10.0-maccatalyst` | `desktopMain` | JVM | macOS via JVM-based desktop |
| `net10.0-windows` | `desktopMain` | JVM | Windows via JVM-based desktop |

### Platform Feature Availability

| Feature | Android | iOS | Desktop |
|---------|---------|-----|---------|
| Camera Capture | Full | Full | Limited (webcam/file picker) |
| Audio Recording | Full | Full | Limited |
| Background Processing | WorkManager | BGTaskScheduler | Coroutines |
| Notifications | Full | Full | System tray |
| Share Intent | Full | Limited | N/A |
| Secure Storage | EncryptedSharedPrefs | Keychain | OS credential store |

---

## 2. Architecture Migration

### 2.1 MVVM Pattern Translation

#### Current (.NET MAUI + CommunityToolkit.Mvvm)
```csharp
public partial class EntryLogViewModel : ObservableObject
{
    [ObservableProperty]
    private ObservableCollection<TrackedEntryCard> _entries = new();

    [ObservableProperty]
    private bool _isLoading;

    [RelayCommand]
    private async Task RefreshEntriesAsync()
    {
        IsLoading = true;
        // ... fetch logic
        IsLoading = false;
    }
}
```

#### Target (Kotlin + Compose)
```kotlin
class EntryLogViewModel(
    private val repository: TrackedEntryRepository
) : ViewModel() {

    private val _entries = MutableStateFlow<List<TrackedEntryCard>>(emptyList())
    val entries: StateFlow<List<TrackedEntryCard>> = _entries.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun refreshEntries() {
        viewModelScope.launch {
            _isLoading.value = true
            _entries.value = repository.getEntries()
            _isLoading.value = false
        }
    }
}
```

### 2.2 State Management Mapping

| MAUI Concept | Compose Equivalent | Usage |
|--------------|-------------------|-------|
| `[ObservableProperty]` | `MutableStateFlow<T>` | ViewModel state |
| `ObservableCollection<T>` | `StateFlow<List<T>>` or `SnapshotStateList<T>` | Collections |
| `[RelayCommand]` | Kotlin suspend function | Async actions |
| `INotifyPropertyChanged` | `StateFlow.collect()` | State observation |
| `MessagingCenter` | `SharedFlow` or event bus | Cross-component events |

### 2.3 Dependency Injection

#### Current (Microsoft.Extensions.DependencyInjection)
```csharp
builder.Services.AddSingleton<IAppSettingsRepository, SecureStorageAppSettingsRepository>();
builder.Services.AddScoped<WellnessWingmanDbContext>();
builder.Services.AddTransient<EntryLogViewModel>();
```

#### Target (Koin)
```kotlin
val appModule = module {
    // Singleton
    single<AppSettingsRepository> { SecureStorageAppSettingsRepository(get()) }

    // Factory (equivalent to Transient)
    factory { EntryLogViewModel(get(), get()) }

    // Scoped (per-scope, similar to Scoped in DI)
    scope<DatabaseScope> {
        scoped { WellnessWingmanDatabase(get()) }
    }
}

// In Application
startKoin {
    androidContext(this@App)
    modules(appModule)
}
```

### 2.4 Navigation

#### Current (MAUI Shell)
```xml
<Shell>
    <ShellContent Route="today" ContentTemplate="{DataTemplate pages:MainPage}" />
    <ShellContent Route="settings" ContentTemplate="{DataTemplate pages:SettingsPage}" />
</Shell>
```
```csharp
await Shell.Current.GoToAsync("settings");
await Shell.Current.GoToAsync($"day?date={selectedDate:yyyy-MM-dd}");
```

#### Target (Voyager - Recommended)
```kotlin
// Screen definitions
class MainScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = koinViewModel<EntryLogViewModel>()

        MainContent(
            entries = viewModel.entries.collectAsState(),
            onSettingsClick = { navigator.push(SettingsScreen()) },
            onDayClick = { date -> navigator.push(DayDetailScreen(date)) }
        )
    }
}

// Navigation setup
@Composable
fun App() {
    Navigator(MainScreen()) { navigator ->
        SlideTransition(navigator)
    }
}
```

#### Alternative: Decompose (for complex navigation)
```kotlin
interface RootComponent {
    val stack: Value<ChildStack<*, Child>>

    sealed class Child {
        class Main(val component: MainComponent) : Child()
        class Settings(val component: SettingsComponent) : Child()
        class DayDetail(val component: DayDetailComponent) : Child()
    }
}
```

---

## 3. UI Migration

### 3.1 Page-by-Page Migration

| MAUI Page | Compose Screen | Complexity | Key Composables |
|-----------|---------------|------------|-----------------|
| MainPage | MainScreen | Medium | LazyColumn, Card, FloatingActionButton |
| SettingsPage | SettingsScreen | Low | TextField, DropdownMenu, Switch |
| MealDetailPage | MealDetailScreen | Low | AsyncImage, Text, Column |
| ExerciseDetailPage | ExerciseDetailScreen | Low | Card, Row, Icon |
| SleepDetailPage | SleepDetailScreen | Low | Card, ProgressIndicator |
| PhotoReviewPage | PhotoReviewScreen | Medium | AsyncImage, Button, AlertDialog |
| ShareEntryPage | ShareEntryScreen | Low | Card, ShareSheet |
| DailySummaryPage | DailySummaryScreen | Low | Card, LazyColumn |
| WeekViewPage | WeekViewScreen | Medium | LazyVerticalGrid, Calendar |
| MonthViewPage | MonthViewScreen | Medium | Calendar component |
| YearViewPage | YearViewScreen | Medium | LazyVerticalGrid |
| DayDetailPage | DayDetailScreen | Low | LazyColumn, Card |

### 3.2 XAML to Compose Mapping

#### Layout Containers

| XAML | Compose | Notes |
|------|---------|-------|
| `StackLayout Orientation="Vertical"` | `Column` | Vertical arrangement |
| `StackLayout Orientation="Horizontal"` | `Row` | Horizontal arrangement |
| `Grid` | `Column` + `Row` or `LazyVerticalGrid` | Grid layouts |
| `ScrollView` | `verticalScroll(rememberScrollState())` | Scrollable content |
| `CollectionView` | `LazyColumn` / `LazyRow` | Virtualized lists |
| `CarouselView` | `HorizontalPager` | Swipeable pages |
| `Frame` | `Card` or `Surface` | Elevated container |
| `Border` | `Box` with `Modifier.border()` | Bordered container |

#### Controls

| XAML | Compose | Notes |
|------|---------|-------|
| `Label` | `Text` | Text display |
| `Entry` | `TextField` / `OutlinedTextField` | Text input |
| `Button` | `Button` / `TextButton` / `IconButton` | Buttons |
| `Image` | `Image` / `AsyncImage` (Coil) | Images |
| `ActivityIndicator` | `CircularProgressIndicator` | Loading |
| `Switch` | `Switch` | Toggle |
| `Picker` | `DropdownMenu` + `ExposedDropdownMenuBox` | Selection |
| `DatePicker` | `DatePicker` (Material3) | Date selection |
| `RefreshView` | `pullRefresh` modifier | Pull to refresh |

#### Example: MainPage Entry List

**Current XAML:**
```xml
<CollectionView ItemsSource="{Binding Entries}">
    <CollectionView.ItemTemplate>
        <DataTemplate x:DataType="models:TrackedEntryCard">
            <Frame Margin="8" Padding="12" CornerRadius="8">
                <StackLayout>
                    <Label Text="{Binding Title}" FontSize="16" FontAttributes="Bold"/>
                    <Label Text="{Binding Description}" FontSize="14"/>
                    <Label Text="{Binding Timestamp, StringFormat='{0:HH:mm}'}"
                           FontSize="12" TextColor="Gray"/>
                </StackLayout>
            </Frame>
        </DataTemplate>
    </CollectionView.ItemTemplate>
</CollectionView>
```

**Target Compose:**
```kotlin
@Composable
fun EntryList(
    entries: List<TrackedEntryCard>,
    onEntryClick: (TrackedEntryCard) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(entries, key = { it.id }) { entry ->
            EntryCard(
                entry = entry,
                onClick = { onEntryClick(entry) }
            )
        }
    }
}

@Composable
fun EntryCard(
    entry: TrackedEntryCard,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = entry.description,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = entry.timestamp.format(DateTimeFormatter.ofPattern("HH:mm")),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
```

### 3.3 Value Converters to Extension Functions

**Current C#:**
```csharp
public class IsProcessingConverter : IValueConverter
{
    public object Convert(object value, Type targetType, object parameter, CultureInfo culture)
    {
        return value is ProcessingStatus status && status == ProcessingStatus.Processing;
    }
}
```

**Target Kotlin:**
```kotlin
fun ProcessingStatus.isProcessing(): Boolean = this == ProcessingStatus.Processing

// Usage in Composable
if (entry.status.isProcessing()) {
    CircularProgressIndicator()
}
```

### 3.4 Theming

**Current MAUI Resources:**
```xml
<Color x:Key="Primary">#512BD4</Color>
<Style TargetType="Button">
    <Setter Property="BackgroundColor" Value="{StaticResource Primary}"/>
</Style>
```

**Target Compose Theme:**
```kotlin
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF512BD4),
    secondary = Color(0xFF625B71),
    // ...
)

@Composable
fun WellnessWingmanTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
```

---

## 4. Data Layer Migration

### 4.1 Database: EF Core to SQLDelight

#### Current EF Core Schema
```csharp
public class WellnessWingmanDbContext : DbContext
{
    public DbSet<TrackedEntry> TrackedEntries { get; set; }
    public DbSet<EntryAnalysis> EntryAnalyses { get; set; }
    public DbSet<DailySummary> DailySummaries { get; set; }
}

public class TrackedEntry
{
    public Guid Id { get; set; }
    public EntryType EntryType { get; set; }
    public DateTime CaptureTimestamp { get; set; }
    public string? BlobPath { get; set; }
    public ProcessingStatus Status { get; set; }
    public string? UserNotes { get; set; }
    public string? PayloadJson { get; set; }
}
```

#### Target SQLDelight Schema

**`shared/src/commonMain/sqldelight/com/wellnesswingman/db/WellnessWingman.sq`:**
```sql
CREATE TABLE TrackedEntry (
    id TEXT NOT NULL PRIMARY KEY,
    entryType TEXT NOT NULL,
    captureTimestamp INTEGER NOT NULL,
    blobPath TEXT,
    status TEXT NOT NULL,
    userNotes TEXT,
    payloadJson TEXT
);

CREATE TABLE EntryAnalysis (
    id TEXT NOT NULL PRIMARY KEY,
    entryId TEXT NOT NULL,
    analysisJson TEXT NOT NULL,
    schemaVersion TEXT NOT NULL,
    createdAt INTEGER NOT NULL,
    FOREIGN KEY (entryId) REFERENCES TrackedEntry(id)
);

CREATE TABLE DailySummary (
    id TEXT NOT NULL PRIMARY KEY,
    date TEXT NOT NULL,
    summaryJson TEXT NOT NULL,
    createdAt INTEGER NOT NULL
);

-- Queries
getEntriesForDay:
SELECT * FROM TrackedEntry
WHERE date(captureTimestamp / 1000, 'unixepoch', 'localtime') = ?
ORDER BY captureTimestamp DESC;

getEntriesForWeek:
SELECT * FROM TrackedEntry
WHERE captureTimestamp >= ? AND captureTimestamp < ?
ORDER BY captureTimestamp DESC;

insertEntry:
INSERT INTO TrackedEntry(id, entryType, captureTimestamp, blobPath, status, userNotes, payloadJson)
VALUES (?, ?, ?, ?, ?, ?, ?);

updateEntryStatus:
UPDATE TrackedEntry SET status = ? WHERE id = ?;

getAnalysisForEntry:
SELECT * FROM EntryAnalysis WHERE entryId = ?;
```

#### Repository Implementation

```kotlin
// Interface in commonMain
interface TrackedEntryRepository {
    suspend fun getEntriesForDay(date: LocalDate): List<TrackedEntry>
    suspend fun getEntriesForWeek(startDate: LocalDate, endDate: LocalDate): List<TrackedEntry>
    suspend fun insertEntry(entry: TrackedEntry)
    suspend fun updateStatus(id: String, status: ProcessingStatus)
}

// Implementation
class SqlDelightTrackedEntryRepository(
    private val database: WellnessWingmanDatabase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : TrackedEntryRepository {

    private val queries = database.trackedEntryQueries

    override suspend fun getEntriesForDay(date: LocalDate): List<TrackedEntry> =
        withContext(dispatcher) {
            queries.getEntriesForDay(date.toString())
                .executeAsList()
                .map { it.toTrackedEntry() }
        }

    override suspend fun insertEntry(entry: TrackedEntry) =
        withContext(dispatcher) {
            queries.insertEntry(
                id = entry.id,
                entryType = entry.entryType.name,
                captureTimestamp = entry.captureTimestamp.toEpochMilliseconds(),
                blobPath = entry.blobPath,
                status = entry.status.name,
                userNotes = entry.userNotes,
                payloadJson = entry.payloadJson
            )
        }
}
```

#### Database Driver Setup (expect/actual)

```kotlin
// commonMain
expect class DriverFactory {
    fun createDriver(): SqlDriver
}

// androidMain
actual class DriverFactory(private val context: Context) {
    actual fun createDriver(): SqlDriver {
        return AndroidSqliteDriver(
            schema = WellnessWingmanDatabase.Schema,
            context = context,
            name = "wellnesswingman.db"
        )
    }
}

// iosMain
actual class DriverFactory {
    actual fun createDriver(): SqlDriver {
        return NativeSqliteDriver(
            schema = WellnessWingmanDatabase.Schema,
            name = "wellnesswingman.db"
        )
    }
}

// desktopMain
actual class DriverFactory {
    actual fun createDriver(): SqlDriver {
        val databasePath = File(System.getProperty("user.home"), ".wellnesswingman/wellnesswingman.db")
        databasePath.parentFile?.mkdirs()
        return JdbcSqliteDriver("jdbc:sqlite:${databasePath.absolutePath}").also {
            WellnessWingmanDatabase.Schema.create(it)
        }
    }
}
```

### 4.2 Secure Storage

#### Current Implementation
```csharp
public class SecureStorageAppSettingsRepository : IAppSettingsRepository
{
    public async Task<string?> GetApiKeyAsync(LlmProvider provider)
    {
        return await SecureStorage.GetAsync($"apikey_{provider}");
    }

    public async Task SetApiKeyAsync(LlmProvider provider, string apiKey)
    {
        await SecureStorage.SetAsync($"apikey_{provider}", apiKey);
    }
}
```

#### Target Implementation (multiplatform-settings)

```kotlin
// commonMain - expect declaration
expect class SecureSettingsFactory {
    fun create(): Settings
}

// androidMain
actual class SecureSettingsFactory(private val context: Context) {
    actual fun create(): Settings {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        val sharedPreferences = EncryptedSharedPreferences.create(
            context,
            "secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        return SharedPreferencesSettings(sharedPreferences)
    }
}

// iosMain
actual class SecureSettingsFactory {
    actual fun create(): Settings {
        return KeychainSettings(service = "com.wellnesswingman")
    }
}

// Repository using Settings
class SecureAppSettingsRepository(
    private val settings: Settings
) : AppSettingsRepository {

    override fun getApiKey(provider: LlmProvider): String? {
        return settings.getStringOrNull("apikey_${provider.name}")
    }

    override fun setApiKey(provider: LlmProvider, apiKey: String) {
        settings["apikey_${provider.name}"] = apiKey
    }
}
```

### 4.3 File System Access

```kotlin
// commonMain
expect class FileSystem {
    fun getAppDataDirectory(): String
    fun getPhotosDirectory(): String
    fun readBytes(path: String): ByteArray
    fun writeBytes(path: String, bytes: ByteArray)
    fun delete(path: String): Boolean
    fun exists(path: String): Boolean
}

// androidMain
actual class FileSystem(private val context: Context) {
    actual fun getAppDataDirectory(): String = context.filesDir.absolutePath
    actual fun getPhotosDirectory(): String =
        File(context.filesDir, "photos").apply { mkdirs() }.absolutePath
    // ... other implementations
}

// iosMain
actual class FileSystem {
    actual fun getAppDataDirectory(): String {
        val paths = NSSearchPathForDirectoriesInDomains(
            NSDocumentDirectory, NSUserDomainMask, true
        )
        return paths.first() as String
    }
    // ... other implementations
}
```

---

## 5. LLM Integration Migration

### 5.1 OpenAI SDK

#### Current Implementation
```csharp
public class OpenAiLlmClient : ILLmClient
{
    private readonly OpenAIClient _client;

    public async Task<LlmAnalysisResult> AnalyzeImageAsync(
        byte[] imageBytes,
        string prompt,
        CancellationToken cancellationToken)
    {
        var chatClient = _client.GetChatClient("gpt-5-mini");

        var messages = new List<ChatMessage>
        {
            new UserChatMessage(
                ChatMessageContentPart.CreateTextPart(prompt),
                ChatMessageContentPart.CreateImagePart(
                    BinaryData.FromBytes(imageBytes),
                    "image/jpeg")
            )
        };

        var response = await chatClient.CompleteChatAsync(
            messages,
            new ChatCompletionOptions { ResponseFormat = ChatResponseFormat.JsonObject },
            cancellationToken);

        return ParseResponse(response);
    }
}
```

#### Target Implementation (openai-kotlin)

```kotlin
// build.gradle.kts
dependencies {
    implementation("com.aallam.openai:openai-client:3.7.0")
    implementation("io.ktor:ktor-client-cio:2.3.8") // or platform-specific engine
}

// Implementation
class OpenAiLlmClient(
    private val apiKey: String
) : LlmClient {

    private val client = OpenAI(
        token = apiKey,
        logging = LoggingConfig(LogLevel.None)
    )

    override suspend fun analyzeImage(
        imageBytes: ByteArray,
        prompt: String
    ): LlmAnalysisResult {
        val base64Image = Base64.encode(imageBytes)

        val response = client.chatCompletion(
            ChatCompletionRequest(
                model = ModelId("gpt-4o-mini"),
                messages = listOf(
                    ChatMessage(
                        role = ChatRole.User,
                        content = listOf(
                            TextPart(prompt),
                            ImagePart(
                                url = "data:image/jpeg;base64,$base64Image"
                            )
                        )
                    )
                ),
                responseFormat = ChatResponseFormat.JsonObject
            )
        )

        return parseResponse(response)
    }

    override suspend fun transcribeAudio(
        audioBytes: ByteArray,
        mimeType: String
    ): String {
        val transcription = client.transcription(
            TranscriptionRequest(
                audio = FileSource(
                    name = "audio.${mimeType.substringAfter("/")}",
                    source = audioBytes.inputStream().asSource()
                ),
                model = ModelId("whisper-1")
            )
        )
        return transcription.text
    }
}
```

### 5.2 Google Gemini SDK

#### Current Implementation
```csharp
public class GeminiLlmClient : ILLmClient
{
    private readonly GenerativeModel _model;

    public async Task<LlmAnalysisResult> AnalyzeImageAsync(
        byte[] imageBytes,
        string prompt,
        CancellationToken cancellationToken)
    {
        var content = new Content
        {
            Parts = new List<Part>
            {
                new TextPart { Text = prompt },
                new BlobPart { MimeType = "image/jpeg", Data = imageBytes }
            }
        };

        var response = await _model.GenerateContentAsync(content);
        return ParseResponse(response);
    }
}
```

#### Target Implementation (Ktor HTTP Client)

The official Gemini SDK is Android-only, so for KMP we use Ktor directly:

```kotlin
class GeminiLlmClient(
    private val apiKey: String,
    private val httpClient: HttpClient = HttpClient(CIO) {
        install(ContentNegotiation) { json() }
    }
) : LlmClient {

    private val baseUrl = "https://generativelanguage.googleapis.com/v1beta"

    override suspend fun analyzeImage(
        imageBytes: ByteArray,
        prompt: String
    ): LlmAnalysisResult {
        val base64Image = Base64.encode(imageBytes)

        val request = GeminiRequest(
            contents = listOf(
                GeminiContent(
                    parts = listOf(
                        GeminiPart.Text(prompt),
                        GeminiPart.InlineData(
                            mimeType = "image/jpeg",
                            data = base64Image
                        )
                    )
                )
            ),
            generationConfig = GenerationConfig(
                responseMimeType = "application/json"
            )
        )

        val response = httpClient.post(
            "$baseUrl/models/gemini-1.5-flash:generateContent?key=$apiKey"
        ) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }

        return parseResponse(response.body<GeminiResponse>())
    }
}

// Request/Response models
@Serializable
data class GeminiRequest(
    val contents: List<GeminiContent>,
    val generationConfig: GenerationConfig? = null
)

@Serializable
data class GeminiContent(
    val parts: List<GeminiPart>,
    val role: String = "user"
)

@Serializable
sealed class GeminiPart {
    @Serializable
    @SerialName("text")
    data class Text(val text: String) : GeminiPart()

    @Serializable
    @SerialName("inline_data")
    data class InlineData(
        val mimeType: String,
        val data: String
    ) : GeminiPart()
}
```

### 5.3 LLM Factory Pattern

```kotlin
// Interface
interface LlmClient {
    suspend fun analyzeImage(imageBytes: ByteArray, prompt: String): LlmAnalysisResult
    suspend fun transcribeAudio(audioBytes: ByteArray, mimeType: String): String
    suspend fun generateCompletion(prompt: String, jsonSchema: String? = null): String
}

// Factory
class LlmClientFactory(
    private val settingsRepository: AppSettingsRepository
) {
    fun create(provider: LlmProvider): LlmClient {
        val apiKey = settingsRepository.getApiKey(provider)
            ?: throw IllegalStateException("API key not configured for $provider")

        return when (provider) {
            LlmProvider.OpenAI -> OpenAiLlmClient(apiKey)
            LlmProvider.Gemini -> GeminiLlmClient(apiKey)
        }
    }

    fun createForCurrentProvider(): LlmClient {
        val provider = settingsRepository.getSelectedProvider()
        return create(provider)
    }
}

// Koin module
val llmModule = module {
    factory<LlmClient> {
        get<LlmClientFactory>().createForCurrentProvider()
    }
    single { LlmClientFactory(get()) }
}
```

---

## 6. Platform-Specific Code Migration

### 6.1 Camera Capture

```kotlin
// commonMain
expect class CameraCaptureService {
    suspend fun capturePhoto(): CaptureResult
    suspend fun pickFromGallery(): CaptureResult?
}

sealed class CaptureResult {
    data class Success(val photoPath: String, val bytes: ByteArray) : CaptureResult()
    data class Error(val message: String) : CaptureResult()
    object Cancelled : CaptureResult()
}

// androidMain - Using CameraX
actual class CameraCaptureService(
    private val context: Context,
    private val activityProvider: () -> ComponentActivity
) {
    actual suspend fun capturePhoto(): CaptureResult = suspendCancellableCoroutine { cont ->
        val activity = activityProvider()
        val photoFile = File(context.cacheDir, "photo_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", photoFile)

        val takePicture = activity.activityResultRegistry.register(
            "camera_capture",
            ActivityResultContracts.TakePicture()
        ) { success ->
            if (success) {
                cont.resume(CaptureResult.Success(photoFile.absolutePath, photoFile.readBytes()))
            } else {
                cont.resume(CaptureResult.Cancelled)
            }
        }

        takePicture.launch(uri)
    }
}

// iosMain - Using UIImagePickerController
actual class CameraCaptureService {
    actual suspend fun capturePhoto(): CaptureResult = suspendCancellableCoroutine { cont ->
        val picker = UIImagePickerController()
        picker.sourceType = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
        picker.delegate = object : NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {
            override fun imagePickerController(
                picker: UIImagePickerController,
                didFinishPickingMediaWithInfo: Map<Any?, *>
            ) {
                val image = didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage
                picker.dismissViewControllerAnimated(true, null)

                if (image != null) {
                    val data = UIImageJPEGRepresentation(image, 0.8)
                    val bytes = data?.toByteArray() ?: ByteArray(0)
                    val path = saveToFile(bytes)
                    cont.resume(CaptureResult.Success(path, bytes))
                } else {
                    cont.resume(CaptureResult.Error("Failed to capture image"))
                }
            }

            override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
                picker.dismissViewControllerAnimated(true, null)
                cont.resume(CaptureResult.Cancelled)
            }
        }

        UIApplication.sharedApplication.keyWindow?.rootViewController
            ?.presentViewController(picker, true, null)
    }
}

// desktopMain - Using AWT file chooser
actual class CameraCaptureService {
    actual suspend fun capturePhoto(): CaptureResult = withContext(Dispatchers.IO) {
        // Desktop doesn't have camera, use file picker
        pickFromGallery() ?: CaptureResult.Cancelled
    }

    actual suspend fun pickFromGallery(): CaptureResult? = withContext(Dispatchers.IO) {
        val fileDialog = FileDialog(null as Frame?, "Select Image", FileDialog.LOAD)
        fileDialog.setFilenameFilter { _, name ->
            name.lowercase().let { it.endsWith(".jpg") || it.endsWith(".png") || it.endsWith(".jpeg") }
        }
        fileDialog.isVisible = true

        val file = fileDialog.file?.let { File(fileDialog.directory, it) }
        file?.let { CaptureResult.Success(it.absolutePath, it.readBytes()) }
    }
}
```

### 6.2 Audio Recording

```kotlin
// commonMain
expect class AudioRecordingService {
    suspend fun startRecording(): RecordingHandle
    fun isRecording(): Boolean
}

interface RecordingHandle {
    suspend fun stop(): AudioResult
    fun cancel()
}

sealed class AudioResult {
    data class Success(val filePath: String, val durationMs: Long) : AudioResult()
    data class Error(val message: String) : AudioResult()
}

// androidMain
actual class AudioRecordingService(private val context: Context) {
    private var mediaRecorder: MediaRecorder? = null
    private var outputFile: File? = null

    actual suspend fun startRecording(): RecordingHandle {
        val file = File(context.cacheDir, "recording_${System.currentTimeMillis()}.m4a")
        outputFile = file

        mediaRecorder = MediaRecorder(context).apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }

        return AndroidRecordingHandle(this, file)
    }

    actual fun isRecording(): Boolean = mediaRecorder != null

    internal fun stopRecording(): AudioResult {
        return try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null

            outputFile?.let { file ->
                AudioResult.Success(file.absolutePath, getAudioDuration(file))
            } ?: AudioResult.Error("No output file")
        } catch (e: Exception) {
            AudioResult.Error(e.message ?: "Recording failed")
        }
    }
}

// iosMain - Using AVAudioEngine
actual class AudioRecordingService {
    private var audioEngine: AVAudioEngine? = null
    private var outputFile: NSURL? = null

    actual suspend fun startRecording(): RecordingHandle {
        val documentsPath = NSSearchPathForDirectoriesInDomains(
            NSDocumentDirectory, NSUserDomainMask, true
        ).first() as String
        val filePath = "$documentsPath/recording_${NSDate().timeIntervalSince1970}.m4a"
        val fileUrl = NSURL.fileURLWithPath(filePath)
        outputFile = fileUrl

        // AVAudioEngine setup via interop
        // ... iOS-specific implementation

        return IOSRecordingHandle(this, fileUrl)
    }
}

// desktopMain - No-op or use Java Sound API
actual class AudioRecordingService {
    actual suspend fun startRecording(): RecordingHandle {
        throw UnsupportedOperationException("Audio recording not supported on desktop")
    }
    actual fun isRecording(): Boolean = false
}
```

### 6.3 Background Processing

```kotlin
// commonMain
expect class BackgroundAnalysisScheduler {
    fun scheduleAnalysis(entryId: String)
    fun cancelAnalysis(entryId: String)
}

// androidMain - Using WorkManager
actual class BackgroundAnalysisScheduler(private val context: Context) {

    actual fun scheduleAnalysis(entryId: String) {
        val workRequest = OneTimeWorkRequestBuilder<AnalysisWorker>()
            .setInputData(workDataOf("entryId" to entryId))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                "analysis_$entryId",
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
    }

    actual fun cancelAnalysis(entryId: String) {
        WorkManager.getInstance(context)
            .cancelUniqueWork("analysis_$entryId")
    }
}

class AnalysisWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val entryId = inputData.getString("entryId") ?: return Result.failure()

        // Get dependencies from Koin
        val orchestrator: AnalysisOrchestrator = getKoin().get()

        return try {
            orchestrator.processEntry(entryId)
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}

// iosMain - Using BGTaskScheduler
actual class BackgroundAnalysisScheduler {

    actual fun scheduleAnalysis(entryId: String) {
        val request = BGProcessingTaskRequest("com.wellnesswingman.analysis")
        request.requiresNetworkConnectivity = true
        request.requiresExternalPower = false

        BGTaskScheduler.sharedScheduler.submitTaskRequest(request, null)
    }
}

// desktopMain - Using coroutines (no true background on desktop)
actual class BackgroundAnalysisScheduler(
    private val scope: CoroutineScope,
    private val orchestrator: AnalysisOrchestrator
) {
    private val jobs = mutableMapOf<String, Job>()

    actual fun scheduleAnalysis(entryId: String) {
        jobs[entryId] = scope.launch {
            orchestrator.processEntry(entryId)
        }
    }

    actual fun cancelAnalysis(entryId: String) {
        jobs[entryId]?.cancel()
        jobs.remove(entryId)
    }
}
```

### 6.4 Share Intent Processing (Android-only)

```kotlin
// In androidApp module
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        handleShareIntent(intent)

        setContent {
            WellnessWingmanApp()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SEND -> {
                if (intent.type?.startsWith("image/") == true) {
                    val imageUri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                    imageUri?.let { processSharedImage(it) }
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val imageUris = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                imageUris?.forEach { processSharedImage(it) }
            }
        }
    }

    private fun processSharedImage(uri: Uri) {
        val shareProcessor: ShareIntentProcessor = getKoin().get()
        lifecycleScope.launch {
            shareProcessor.processSharedImage(uri)
        }
    }
}

// AndroidManifest.xml
<activity
    android:name=".MainActivity"
    android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.SEND" />
        <category android:name="android.intent.category.DEFAULT" />
        <data android:mimeType="image/*" />
    </intent-filter>
    <intent-filter>
        <action android:name="android.intent.action.SEND_MULTIPLE" />
        <category android:name="android.intent.category.DEFAULT" />
        <data android:mimeType="image/*" />
    </intent-filter>
</activity>
```

---

## 7. Services Migration

### 7.1 Analysis Orchestrator

```kotlin
class AnalysisOrchestrator(
    private val entryRepository: TrackedEntryRepository,
    private val analysisRepository: EntryAnalysisRepository,
    private val llmClientFactory: LlmClientFactory,
    private val analysisApplier: UnifiedAnalysisApplier,
    private val fileSystem: FileSystem
) {
    suspend fun processEntry(entryId: String): AnalysisResult {
        val entry = entryRepository.getById(entryId)
            ?: return AnalysisResult.Error("Entry not found")

        entryRepository.updateStatus(entryId, ProcessingStatus.Processing)

        return try {
            val llmClient = llmClientFactory.createForCurrentProvider()

            val imageBytes = entry.blobPath?.let { fileSystem.readBytes(it) }
            val prompt = buildPromptForEntry(entry)

            val llmResult = if (imageBytes != null) {
                llmClient.analyzeImage(imageBytes, prompt)
            } else {
                val response = llmClient.generateCompletion(prompt)
                LlmAnalysisResult(response, LlmDiagnostics())
            }

            val analysis = EntryAnalysis(
                id = generateId(),
                entryId = entryId,
                analysisJson = llmResult.content,
                schemaVersion = "1.0",
                createdAt = Clock.System.now()
            )

            analysisRepository.insert(analysis)
            analysisApplier.applyAnalysis(entry, analysis)
            entryRepository.updateStatus(entryId, ProcessingStatus.Completed)

            AnalysisResult.Success(analysis)
        } catch (e: Exception) {
            entryRepository.updateStatus(entryId, ProcessingStatus.Failed)
            AnalysisResult.Error(e.message ?: "Analysis failed")
        }
    }
}
```

### 7.2 Daily Summary Service

```kotlin
class DailySummaryService(
    private val entryRepository: TrackedEntryRepository,
    private val analysisRepository: EntryAnalysisRepository,
    private val summaryRepository: DailySummaryRepository,
    private val llmClientFactory: LlmClientFactory
) {
    suspend fun generateSummary(date: LocalDate): DailySummaryResult {
        val entries = entryRepository.getEntriesForDay(date)
        if (entries.isEmpty()) {
            return DailySummaryResult.NoEntries
        }

        val entriesWithAnalysis = entries.mapNotNull { entry ->
            val analysis = analysisRepository.getForEntry(entry.id)
            if (analysis != null) {
                DailySummaryEntryContext(entry, analysis)
            } else null
        }

        val request = buildSummaryRequest(date, entriesWithAnalysis)
        val llmClient = llmClientFactory.createForCurrentProvider()

        val response = llmClient.generateCompletion(
            prompt = request.toPrompt(),
            jsonSchema = DailySummaryResponse.jsonSchema
        )

        val summaryResponse = Json.decodeFromString<DailySummaryResponse>(response)

        val summary = DailySummary(
            id = generateId(),
            date = date,
            summaryJson = response,
            createdAt = Clock.System.now()
        )

        summaryRepository.insert(summary)

        return DailySummaryResult.Success(summary, summaryResponse)
    }
}
```

### 7.3 Daily Totals Calculator

```kotlin
// Pure Kotlin - direct port
class DailyTotalsCalculator {

    fun calculateTotals(meals: List<MealAnalysisResult>): NutritionTotals {
        return NutritionTotals(
            calories = meals.sumOf { it.nutrition?.calories ?: 0 },
            protein = meals.sumOf { it.nutrition?.proteinGrams ?: 0.0 },
            carbs = meals.sumOf { it.nutrition?.carbsGrams ?: 0.0 },
            fat = meals.sumOf { it.nutrition?.fatGrams ?: 0.0 },
            fiber = meals.sumOf { it.nutrition?.fiberGrams ?: 0.0 },
            sugar = meals.sumOf { it.nutrition?.sugarGrams ?: 0.0 }
        )
    }

    fun calculateFromEntries(
        entries: List<TrackedEntry>,
        analysisProvider: (String) -> MealAnalysisResult?
    ): NutritionTotals {
        val meals = entries
            .filter { it.entryType == EntryType.Meal }
            .mapNotNull { analysisProvider(it.id) }

        return calculateTotals(meals)
    }
}
```

### 7.4 Logging (Napier)

```kotlin
// build.gradle.kts
dependencies {
    implementation("io.github.aakira:napier:2.7.1")
}

// Setup in Application
class WellnessWingmanApp : Application() {
    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            Napier.base(DebugAntilog())
        }

        // Add file logger
        Napier.base(FileAntilog(getLogFile()))
    }
}

// Custom file antilog
class FileAntilog(private val logFile: File) : Antilog() {
    private val maxSize = 1024 * 1024 // 1 MB

    override fun performLog(
        priority: Napier.Level,
        tag: String?,
        throwable: Throwable?,
        message: String?
    ) {
        if (logFile.length() > maxSize) {
            rotateLog()
        }

        val timestamp = Clock.System.now().toString()
        val logLine = "$timestamp [${priority.name}] ${tag ?: ""}: $message\n"

        logFile.appendText(logLine)

        throwable?.let {
            logFile.appendText(it.stackTraceToString())
        }
    }
}

// Usage
Napier.d("Processing entry", tag = "AnalysisOrchestrator")
Napier.e("Analysis failed", throwable = exception, tag = "LlmClient")
```

---

## 8. Testing Strategy Migration

### 8.1 Unit Tests

#### Test Framework Setup

```kotlin
// shared/build.gradle.kts
kotlin {
    sourceSets {
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
                implementation("io.mockk:mockk:1.13.9")
                implementation("app.cash.turbine:turbine:1.0.0") // Flow testing
            }
        }

        val androidUnitTest by getting {
            dependencies {
                implementation("junit:junit:4.13.2")
                implementation("org.robolectric:robolectric:4.11.1")
            }
        }

        val jvmTest by getting {
            dependencies {
                implementation("org.junit.jupiter:junit-jupiter:5.10.1")
            }
        }
    }
}
```

#### Repository Tests

```kotlin
class SqlDelightTrackedEntryRepositoryTest {

    private lateinit var database: WellnessWingmanDatabase
    private lateinit var repository: TrackedEntryRepository

    @BeforeTest
    fun setup() {
        // In-memory SQLite driver for testing
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        WellnessWingmanDatabase.Schema.create(driver)
        database = WellnessWingmanDatabase(driver)
        repository = SqlDelightTrackedEntryRepository(database)
    }

    @AfterTest
    fun teardown() {
        database.close()
    }

    @Test
    fun `getEntriesForDay returns entries for specified date`() = runTest {
        // Arrange
        val today = LocalDate(2024, 3, 15)
        val entry1 = createTestEntry(today.atTime(10, 0))
        val entry2 = createTestEntry(today.atTime(14, 30))
        val yesterdayEntry = createTestEntry(today.minus(1, DateTimeUnit.DAY).atTime(12, 0))

        repository.insertEntry(entry1)
        repository.insertEntry(entry2)
        repository.insertEntry(yesterdayEntry)

        // Act
        val result = repository.getEntriesForDay(today)

        // Assert
        assertEquals(2, result.size)
        assertTrue(result.all { it.captureTimestamp.date == today })
    }

    @Test
    fun `updateStatus changes entry status`() = runTest {
        // Arrange
        val entry = createTestEntry()
        repository.insertEntry(entry)

        // Act
        repository.updateStatus(entry.id, ProcessingStatus.Completed)

        // Assert
        val updated = repository.getById(entry.id)
        assertEquals(ProcessingStatus.Completed, updated?.status)
    }
}
```

#### Service Tests with MockK

```kotlin
class DailySummaryServiceTest {

    private lateinit var service: DailySummaryService
    private val entryRepository: TrackedEntryRepository = mockk()
    private val analysisRepository: EntryAnalysisRepository = mockk()
    private val summaryRepository: DailySummaryRepository = mockk()
    private val llmClientFactory: LlmClientFactory = mockk()
    private val llmClient: LlmClient = mockk()

    @BeforeTest
    fun setup() {
        every { llmClientFactory.createForCurrentProvider() } returns llmClient
        coEvery { summaryRepository.insert(any()) } just Runs

        service = DailySummaryService(
            entryRepository,
            analysisRepository,
            summaryRepository,
            llmClientFactory
        )
    }

    @Test
    fun `generateSummary returns NoEntries when no entries exist`() = runTest {
        // Arrange
        val date = LocalDate(2024, 3, 15)
        coEvery { entryRepository.getEntriesForDay(date) } returns emptyList()

        // Act
        val result = service.generateSummary(date)

        // Assert
        assertTrue(result is DailySummaryResult.NoEntries)
        coVerify(exactly = 0) { llmClient.generateCompletion(any(), any()) }
    }

    @Test
    fun `generateSummary calls LLM and saves summary`() = runTest {
        // Arrange
        val date = LocalDate(2024, 3, 15)
        val entries = listOf(createTestEntry(), createTestEntry())
        val analyses = entries.map { createTestAnalysis(it.id) }

        coEvery { entryRepository.getEntriesForDay(date) } returns entries
        entries.forEachIndexed { index, entry ->
            coEvery { analysisRepository.getForEntry(entry.id) } returns analyses[index]
        }
        coEvery { llmClient.generateCompletion(any(), any()) } returns validSummaryJson

        // Act
        val result = service.generateSummary(date)

        // Assert
        assertTrue(result is DailySummaryResult.Success)
        coVerify { summaryRepository.insert(any()) }
    }
}
```

#### Pure Logic Tests

```kotlin
class DailyTotalsCalculatorTest {

    private val calculator = DailyTotalsCalculator()

    @Test
    fun `calculateTotals sums all nutrition values`() {
        // Arrange
        val meals = listOf(
            MealAnalysisResult(
                nutrition = NutritionInfo(calories = 500, proteinGrams = 25.0, carbsGrams = 60.0, fatGrams = 15.0)
            ),
            MealAnalysisResult(
                nutrition = NutritionInfo(calories = 300, proteinGrams = 15.0, carbsGrams = 40.0, fatGrams = 10.0)
            )
        )

        // Act
        val totals = calculator.calculateTotals(meals)

        // Assert
        assertEquals(800, totals.calories)
        assertEquals(40.0, totals.protein, 0.01)
        assertEquals(100.0, totals.carbs, 0.01)
        assertEquals(25.0, totals.fat, 0.01)
    }

    @Test
    fun `calculateTotals handles null nutrition gracefully`() {
        // Arrange
        val meals = listOf(
            MealAnalysisResult(nutrition = null),
            MealAnalysisResult(nutrition = NutritionInfo(calories = 500))
        )

        // Act
        val totals = calculator.calculateTotals(meals)

        // Assert
        assertEquals(500, totals.calories)
    }
}
```

### 8.2 UI Tests (Compose)

```kotlin
// composeApp/src/commonTest/kotlin
class MainScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `displays loading indicator when loading`() {
        // Arrange
        val viewModel = mockk<EntryLogViewModel>()
        every { viewModel.isLoading } returns MutableStateFlow(true)
        every { viewModel.entries } returns MutableStateFlow(emptyList())

        // Act
        composeTestRule.setContent {
            MainScreen(viewModel = viewModel)
        }

        // Assert
        composeTestRule
            .onNodeWithTag("loading_indicator")
            .assertIsDisplayed()
    }

    @Test
    fun `displays entries when loaded`() {
        // Arrange
        val entries = listOf(
            TrackedEntryCard(id = "1", title = "Breakfast", description = "Eggs and toast"),
            TrackedEntryCard(id = "2", title = "Lunch", description = "Salad")
        )
        val viewModel = mockk<EntryLogViewModel>()
        every { viewModel.isLoading } returns MutableStateFlow(false)
        every { viewModel.entries } returns MutableStateFlow(entries)

        // Act
        composeTestRule.setContent {
            MainScreen(viewModel = viewModel)
        }

        // Assert
        composeTestRule.onNodeWithText("Breakfast").assertIsDisplayed()
        composeTestRule.onNodeWithText("Lunch").assertIsDisplayed()
    }

    @Test
    fun `clicking entry navigates to detail`() {
        // Arrange
        val entries = listOf(TrackedEntryCard(id = "1", title = "Breakfast"))
        val navigator = mockk<Navigator>(relaxed = true)

        // Act
        composeTestRule.setContent {
            CompositionLocalProvider(LocalNavigator provides navigator) {
                EntryList(entries = entries, onEntryClick = { navigator.push(MealDetailScreen(it.id)) })
            }
        }

        composeTestRule.onNodeWithText("Breakfast").performClick()

        // Assert
        verify { navigator.push(match { it is MealDetailScreen }) }
    }
}
```

### 8.3 E2E Tests (Maestro)

Create Maestro flow files for E2E testing:

**`maestro/flows/smoke_test.yaml`:**
```yaml
appId: com.wellnesswingman
---
- launchApp
- assertVisible: "Diet Tracker"
- assertVisible: "Add Entry"
- tapOn: "Settings"
- assertVisible: "LLM Provider"
- back
```

**`maestro/flows/create_entry.yaml`:**
```yaml
appId: com.wellnesswingman
---
- launchApp
- tapOn: "Add Entry"
- assertVisible: "Photo Review"
- tapOn: "Cancel"
- assertVisible: "Diet Tracker"
```

**`maestro/flows/settings.yaml`:**
```yaml
appId: com.wellnesswingman
---
- launchApp
- tapOn: "Settings"
- tapOn: "LLM Provider"
- tapOn: "OpenAI"
- assertVisible: "API Key"
- inputText:
    text: "sk-test-key"
- tapOn: "Save"
- assertVisible: "Settings saved"
```

### 8.4 Test Coverage (Kover)

```kotlin
// build.gradle.kts (root)
plugins {
    id("org.jetbrains.kotlinx.kover") version "0.7.5"
}

koverReport {
    filters {
        excludes {
            classes("*Generated*", "*_Factory", "*Composable*")
        }
    }

    defaults {
        html { onCheck = true }
        xml { onCheck = true }
    }

    verify {
        rule {
            isEnabled = true
            bound {
                minValue = 70 // Minimum 70% coverage
            }
        }
    }
}
```

### 8.5 Test Coverage Mapping

| Current Test Class | KMP Equivalent | Framework |
|-------------------|----------------|-----------|
| SqliteTrackedEntryRepositoryTests | SqlDelightTrackedEntryRepositoryTest | kotlin.test + in-memory driver |
| UnifiedAnalysisApplierTests | UnifiedAnalysisApplierTest | kotlin.test + MockK |
| DailySummaryServiceTests | DailySummaryServiceTest | kotlin.test + MockK + Turbine |
| DailyTotalsCalculatorTests | DailyTotalsCalculatorTest | kotlin.test (pure logic) |
| DateTimeConverterTests | DateTimeConverterTest | kotlin.test + kotlinx-datetime |
| WeekSummaryBuilderTests | WeekSummaryBuilderTest | kotlin.test |
| SmokeTests (Appium) | smoke_test.yaml | Maestro |
| EntryCreationTests (Appium) | create_entry.yaml | Maestro |
| NavigationTests (Appium) | navigation.yaml | Maestro |
| SettingsTests (Appium) | settings.yaml | Maestro |

---

## 9. Dependency Mapping

| NuGet Package | Version | Kotlin/KMP Equivalent | Version | Notes |
|---------------|---------|----------------------|---------|-------|
| Microsoft.Maui.Controls | 10.0.10 | Compose Multiplatform | 1.6.0 | UI framework |
| Microsoft.Maui.Essentials | 10.0.10 | (various expect/actual) | - | Platform APIs |
| CommunityToolkit.Mvvm | 8.4.0 | Built-in Compose state | - | MVVM patterns |
| CommunityToolkit.Maui | 13.0.0 | Compose foundation | - | UI utilities |
| OpenAI | 2.7.0 | openai-kotlin | 3.7.0 | OpenAI API client |
| Google.GenAI | 0.9.0 | Ktor + custom client | 2.3.8 | Gemini API |
| Microsoft.EntityFrameworkCore.Sqlite | 10.0.0 | SQLDelight | 2.0.1 | Database ORM |
| Microsoft.Extensions.DependencyInjection | 10.0.0 | Koin | 3.5.3 | Dependency injection |
| Microsoft.Extensions.Logging | 10.0.0 | Napier | 2.7.1 | Logging |
| Microsoft.Extensions.Http.Resilience | 10.0.0 | Ktor client plugins | 2.3.8 | HTTP resilience |
| OpenTelemetry.* | 1.14.0 | OpenTelemetry-Kotlin | 1.32.0 | Observability |
| xunit | 2.9.3 | kotlin.test + JUnit5 | 1.9.22 | Unit testing |
| coverlet.msbuild | 6.0.4 | Kover | 0.7.5 | Code coverage |
| Appium.WebDriver | 5.0.0 | Maestro | 1.34.0 | E2E testing |
| Selenium.WebDriver | 4.21.0 | (not needed) | - | Replaced by Maestro |

---

## 10. Risk Assessment

### Low Risk (Direct Mapping)

| Component | Effort | Notes |
|-----------|--------|-------|
| Data models | S | Straightforward Kotlin data classes |
| Business logic (calculators, validators) | S | Pure Kotlin, direct port |
| Repository pattern | S | Same patterns in Kotlin |
| Navigation concepts | S | Voyager/Decompose handle similarly |
| Unit test logic | S | kotlin.test is similar to xUnit |
| DI patterns | S | Koin is straightforward |

### Medium Risk (Requires Adaptation)

| Component | Effort | Notes |
|-----------|--------|-------|
| LLM SDK integration | M | Different APIs, same concepts |
| Database layer (SQLDelight) | M | Different query patterns than EF Core |
| UI layouts (XAML → Compose) | M | Learning curve, but well-documented |
| Secure storage | M | Platform-specific implementations |
| State management | M | Different reactive patterns |
| JSON serialization | S | kotlinx.serialization is excellent |

### High Risk (Significant Effort)

| Component | Effort | Risk Factors |
|-----------|--------|--------------|
| Camera capture | L | Platform-specific, complex interop |
| Audio recording | L | iOS Kotlin/Native interop challenging |
| Background services | L | Very different patterns per platform |
| Share intent processing | M | Android-specific, redesign for others |
| iOS interop (AVFoundation) | L | ObjC/Swift interop complexity |
| Photo resizing | M | Platform-specific image APIs |

### Potential Blockers

| Issue | Mitigation |
|-------|------------|
| Compose Multiplatform desktop maturity | Target Android/iOS first, desktop later |
| iOS camera/audio interop | Consider CocoaPods Swift wrapper |
| Code coverage tooling maturity | Kover is improving rapidly |
| Gemini SDK not multiplatform | Use Ktor HTTP client directly |
| Large binary size on iOS | Kotlin/Native optimizations, proguard |

---

## 11. Project Structure

```
wellnesswingman-kmp/
├── gradle/
│   └── libs.versions.toml              # Version catalog
├── build.gradle.kts                     # Root build config
├── settings.gradle.kts
│
├── shared/                              # Shared KMP module
│   ├── build.gradle.kts
│   └── src/
│       ├── commonMain/kotlin/com/wellnesswingman/
│       │   ├── data/
│       │   │   ├── db/                  # SQLDelight schemas (.sq files)
│       │   │   ├── repository/          # Repository interfaces & impls
│       │   │   │   ├── TrackedEntryRepository.kt
│       │   │   │   ├── EntryAnalysisRepository.kt
│       │   │   │   ├── DailySummaryRepository.kt
│       │   │   │   └── AppSettingsRepository.kt
│       │   │   └── model/               # Data models
│       │   │       ├── TrackedEntry.kt
│       │   │       ├── EntryAnalysis.kt
│       │   │       ├── DailySummary.kt
│       │   │       ├── EntryType.kt
│       │   │       ├── ProcessingStatus.kt
│       │   │       └── analysis/
│       │   │           ├── MealAnalysisResult.kt
│       │   │           ├── ExerciseAnalysisResult.kt
│       │   │           └── SleepAnalysisResult.kt
│       │   ├── domain/
│       │   │   ├── analysis/
│       │   │   │   ├── AnalysisOrchestrator.kt
│       │   │   │   ├── UnifiedAnalysisApplier.kt
│       │   │   │   └── BackgroundAnalysisService.kt
│       │   │   ├── summary/
│       │   │   │   ├── DailySummaryService.kt
│       │   │   │   ├── DailyTotalsCalculator.kt
│       │   │   │   └── WeekSummaryBuilder.kt
│       │   │   └── llm/
│       │   │       ├── LlmClient.kt
│       │   │       ├── LlmClientFactory.kt
│       │   │       ├── OpenAiLlmClient.kt
│       │   │       └── GeminiLlmClient.kt
│       │   ├── platform/                # expect declarations
│       │   │   ├── CameraCaptureService.kt
│       │   │   ├── AudioRecordingService.kt
│       │   │   ├── PhotoResizer.kt
│       │   │   ├── BackgroundScheduler.kt
│       │   │   ├── SecureSettings.kt
│       │   │   └── FileSystem.kt
│       │   └── util/
│       │       ├── DateTimeConverter.kt
│       │       └── JsonParser.kt
│       ├── androidMain/kotlin/          # Android actual implementations
│       │   └── com/wellnesswingman/platform/
│       │       ├── AndroidCameraCaptureService.kt
│       │       ├── AndroidAudioRecordingService.kt
│       │       ├── AndroidPhotoResizer.kt
│       │       ├── AndroidBackgroundScheduler.kt
│       │       ├── AndroidSecureSettings.kt
│       │       └── AndroidFileSystem.kt
│       ├── iosMain/kotlin/              # iOS actual implementations
│       │   └── com/wellnesswingman/platform/
│       │       ├── IOSCameraCaptureService.kt
│       │       ├── IOSAudioRecordingService.kt
│       │       ├── IOSPhotoResizer.kt
│       │       ├── IOSBackgroundScheduler.kt
│       │       ├── IOSSecureSettings.kt
│       │       └── IOSFileSystem.kt
│       ├── desktopMain/kotlin/          # Desktop actual implementations
│       │   └── com/wellnesswingman/platform/
│       │       └── Desktop*.kt
│       └── commonTest/kotlin/           # Shared tests
│           └── com/wellnesswingman/
│               ├── data/repository/
│               ├── domain/
│               └── util/
│
├── composeApp/                          # Compose UI module
│   ├── build.gradle.kts
│   └── src/
│       ├── commonMain/kotlin/com/wellnesswingman/ui/
│       │   ├── App.kt                   # Root composable
│       │   ├── navigation/
│       │   │   ├── AppNavigation.kt
│       │   │   └── NavigationGraph.kt
│       │   ├── screens/
│       │   │   ├── main/
│       │   │   │   ├── MainScreen.kt
│       │   │   │   └── MainViewModel.kt
│       │   │   ├── settings/
│       │   │   │   ├── SettingsScreen.kt
│       │   │   │   └── SettingsViewModel.kt
│       │   │   ├── detail/
│       │   │   │   ├── MealDetailScreen.kt
│       │   │   │   ├── ExerciseDetailScreen.kt
│       │   │   │   └── SleepDetailScreen.kt
│       │   │   ├── photo/
│       │   │   │   └── PhotoReviewScreen.kt
│       │   │   ├── summary/
│       │   │   │   └── DailySummaryScreen.kt
│       │   │   └── calendar/
│       │   │       ├── WeekViewScreen.kt
│       │   │       ├── MonthViewScreen.kt
│       │   │       ├── YearViewScreen.kt
│       │   │       └── DayDetailScreen.kt
│       │   ├── components/
│       │   │   ├── EntryCard.kt
│       │   │   ├── NutritionSummary.kt
│       │   │   ├── LoadingIndicator.kt
│       │   │   └── ErrorMessage.kt
│       │   └── theme/
│       │       ├── Theme.kt
│       │       ├── Color.kt
│       │       └── Typography.kt
│       ├── androidMain/
│       ├── iosMain/
│       └── desktopMain/
│
├── androidApp/                          # Android application
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       └── kotlin/com/wellnesswingman/
│           ├── MainActivity.kt
│           ├── WellnessWingmanApp.kt
│           └── worker/
│               └── AnalysisWorker.kt
│
├── iosApp/                              # iOS application (Xcode)
│   ├── iosApp.xcodeproj/
│   └── iosApp/
│       ├── AppDelegate.swift
│       ├── ContentView.swift
│       └── Info.plist
│
├── desktopApp/                          # Desktop JVM application
│   ├── build.gradle.kts
│   └── src/main/kotlin/
│       └── Main.kt
│
└── maestro/                             # E2E tests
    └── flows/
        ├── smoke_test.yaml
        ├── create_entry.yaml
        ├── navigation.yaml
        └── settings.yaml
```

---

## 12. Build & Tooling

### Version Catalog (`gradle/libs.versions.toml`)

```toml
[versions]
kotlin = "1.9.22"
compose = "1.6.0"
compose-compiler = "1.5.8"
koin = "3.5.3"
sqldelight = "2.0.1"
ktor = "2.3.8"
coroutines = "1.8.0"
openai = "3.7.0"
napier = "2.7.1"
kover = "0.7.5"
mockk = "1.13.9"

[libraries]
# Compose
compose-runtime = { module = "org.jetbrains.compose.runtime:runtime", version.ref = "compose" }
compose-foundation = { module = "org.jetbrains.compose.foundation:foundation", version.ref = "compose" }
compose-material3 = { module = "org.jetbrains.compose.material3:material3", version.ref = "compose" }
compose-ui-tooling = { module = "org.jetbrains.compose.ui:ui-tooling", version.ref = "compose" }

# Koin
koin-core = { module = "io.insert-koin:koin-core", version.ref = "koin" }
koin-android = { module = "io.insert-koin:koin-android", version.ref = "koin" }
koin-compose = { module = "io.insert-koin:koin-compose", version.ref = "koin" }

# SQLDelight
sqldelight-driver-android = { module = "app.cash.sqldelight:android-driver", version.ref = "sqldelight" }
sqldelight-driver-native = { module = "app.cash.sqldelight:native-driver", version.ref = "sqldelight" }
sqldelight-driver-jvm = { module = "app.cash.sqldelight:sqlite-driver", version.ref = "sqldelight" }
sqldelight-coroutines = { module = "app.cash.sqldelight:coroutines-extensions", version.ref = "sqldelight" }

# Ktor
ktor-client-core = { module = "io.ktor:ktor-client-core", version.ref = "ktor" }
ktor-client-cio = { module = "io.ktor:ktor-client-cio", version.ref = "ktor" }
ktor-client-darwin = { module = "io.ktor:ktor-client-darwin", version.ref = "ktor" }
ktor-client-content-negotiation = { module = "io.ktor:ktor-client-content-negotiation", version.ref = "ktor" }
ktor-serialization-json = { module = "io.ktor:ktor-serialization-kotlinx-json", version.ref = "ktor" }

# Coroutines
coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "coroutines" }
coroutines-android = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "coroutines" }
coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines" }

# Other
openai-kotlin = { module = "com.aallam.openai:openai-client", version.ref = "openai" }
napier = { module = "io.github.aakira:napier", version.ref = "napier" }
kotlinx-datetime = { module = "org.jetbrains.kotlinx:kotlinx-datetime", version = "0.5.0" }
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version = "1.6.2" }

# Testing
mockk = { module = "io.mockk:mockk", version.ref = "mockk" }
turbine = { module = "app.cash.turbine:turbine", version = "1.0.0" }

[plugins]
kotlin-multiplatform = { id = "org.jetbrains.kotlin.multiplatform", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
compose-multiplatform = { id = "org.jetbrains.compose", version.ref = "compose" }
sqldelight = { id = "app.cash.sqldelight", version.ref = "sqldelight" }
kover = { id = "org.jetbrains.kotlinx.kover", version.ref = "kover" }
```

### Root Build Script

```kotlin
// build.gradle.kts
plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.sqldelight) apply false
    alias(libs.plugins.kover)
}

allprojects {
    repositories {
        google()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}
```

---

## 13. Migration Order Recommendation

### Phase 1: Foundation (Weeks 1-2)
1. Set up KMP project structure
2. Configure Gradle with version catalog
3. Implement data models (Kotlin data classes)
4. Set up SQLDelight schemas and migrations
5. Implement repository layer

### Phase 2: Core Business Logic (Weeks 3-4)
1. Port pure business logic (calculators, validators)
2. Implement LLM client interfaces
3. Create OpenAI client with openai-kotlin
4. Create Gemini client with Ktor
5. Implement analysis orchestrator
6. Port daily summary service

### Phase 3: Platform Services (Weeks 5-7)
1. Implement secure storage (Android first)
2. Implement file system access
3. Implement camera capture (Android first)
4. Implement audio recording (Android first)
5. Implement background processing (WorkManager)
6. Port iOS platform implementations

### Phase 4: UI Layer (Weeks 8-10)
1. Set up Compose Multiplatform
2. Implement theme and design system
3. Create MainScreen with entry list
4. Create SettingsScreen
5. Create detail screens (Meal, Exercise, Sleep)
6. Implement navigation with Voyager
7. Create calendar views (Week, Month, Year)

### Phase 5: Testing & Polish (Weeks 11-12)
1. Port unit tests to kotlin.test
2. Set up Kover for coverage
3. Create Maestro E2E flows
4. Performance optimization
5. iOS-specific polish
6. Desktop support (if needed)

---

## 14. Conclusion

Migrating WellnessWingman from .NET MAUI to Kotlin Multiplatform is feasible but requires significant effort, particularly around platform-specific features like camera capture, audio recording, and background processing.

**Estimated Total Effort:** 10-14 weeks for a single developer

**Key Advantages of Migration:**
- Native Android performance with Kotlin
- Better iOS interop (vs .NET's AOT)
- Growing KMP ecosystem and JetBrains support
- Compose UI is modern and declarative
- Shared business logic across all platforms

**Key Challenges:**
- iOS platform services require Kotlin/Native interop
- Compose Multiplatform desktop is less mature than MAUI
- Some dependencies need Ktor-based implementations
- Testing tooling is still maturing

**Recommendation:** Start with Android as the primary target, ensure feature parity, then progressively add iOS and desktop support. This reduces risk and allows for iterative learning with KMP.
