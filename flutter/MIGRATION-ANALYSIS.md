# WellnessWingman Flutter Migration Analysis

## Executive Summary

This document provides a comprehensive analysis for migrating the WellnessWingman application from .NET MAUI to Flutter. WellnessWingman is a privacy-first wellness tracking application that uses LLM-powered image analysis for meal, exercise, and sleep tracking with AI-generated daily summaries.

### Current Technology Stack
| Component | Technology |
|-----------|------------|
| Framework | .NET MAUI 10.0 |
| Language | C# 12+ |
| UI Markup | XAML |
| Database | Entity Framework Core 10.0 + SQLite |
| State Management | MVVM Community Toolkit 8.4.0 |
| LLM Providers | OpenAI (GPT-4V), Google Gemini (1.5 Flash) |
| Platforms | Android, iOS, macOS, Windows |

### Migration Complexity Assessment
- **Overall Complexity**: High
- **Estimated Duration**: 19-22 weeks (with buffer)
- **Risk Level**: Medium (well-documented patterns, clear architecture)

---

## 1. Technology Mapping

### 1.1 Core Framework Components

| .NET MAUI | Flutter Equivalent | Package/Version |
|-----------|-------------------|-----------------|
| `ContentPage` | `Scaffold`/`StatelessWidget` | Built-in |
| `CollectionView` | `ListView.builder`/`SliverList` | Built-in |
| `Shell` navigation | `go_router` | ^14.0.0 |
| `ObservableObject` | Riverpod Notifiers | flutter_riverpod ^2.5.0 |
| `RelayCommand` | Methods in Notifier | Built-in |
| `DataTemplate` | Builder patterns | Built-in |
| `BindingContext` | `ref.watch()`/`ref.read()` | Riverpod |

### 1.2 Data Layer

| .NET MAUI | Flutter Equivalent | Package/Version | Rationale |
|-----------|-------------------|-----------------|-----------|
| Entity Framework Core | Drift | ^2.15.0 | Type-safe SQL, migrations support |
| SQLite | sqlite3_flutter_libs | ^0.5.0 | Native SQLite bundling |
| SecureStorage | flutter_secure_storage | ^9.0.0 | Platform-native encryption |
| JSON serialization | freezed + json_serializable | Latest | Code generation, immutable models |

### 1.3 Platform Services

| .NET MAUI Service | Flutter Equivalent | Package |
|-------------------|-------------------|---------|
| Camera (MediaStore) | camera + image_picker | ^0.10.0 / ^1.0.0 |
| Audio Recording | record | ^5.0.0 |
| File System | path_provider | ^2.1.0 |
| Share Intent (out) | share_plus | ^7.0.0 |
| Share Intent (in) | receive_sharing_intent | ^1.6.0 |
| Permissions | permission_handler | ^11.0.0 |
| Background Tasks | workmanager | ^0.5.0 |
| Notifications | flutter_local_notifications | ^17.0.0 |

### 1.4 LLM/AI Integration

| Feature | Flutter Approach | Package |
|---------|-----------------|---------|
| OpenAI GPT-4V | REST API (no official SDK) | http ^1.2.0 or dio ^5.0.0 |
| Google Gemini | Official SDK | google_generative_ai ^0.4.0 |
| Audio Transcription | OpenAI Whisper API / Gemini | REST API |

---

## 2. Architecture Migration

### 2.1 Current Architecture (MAUI)

```
Pages (XAML) → PageModels (ViewModels) → Services → Repositories → SQLite
                    ↓
              LLM Clients (Factory Pattern)
```

**Key Patterns:**
- MVVM with `ObservableObject` and `RelayCommand`
- Repository pattern for data access
- Factory pattern for LLM client selection
- Dependency injection via `MauiProgram.cs`
- Background processing service for analysis

### 2.2 Target Architecture (Flutter)

```
Widgets (Dart) → Riverpod Notifiers → Services → Repositories → Drift
                    ↓
              LLM Clients (Provider-based Factory)
```

**Pattern Mapping:**

| MAUI Pattern | Flutter/Riverpod Equivalent |
|--------------|----------------------------|
| `IServiceScopeFactory` | `ProviderContainer` with `ProviderScope` |
| `ObservableObject` | `@riverpod` generated notifiers |
| `[ObservableProperty]` | State class fields with Freezed |
| `[RelayCommand]` | Methods in notifier class |
| Constructor injection | `ref.read(provider)` / `ref.watch(provider)` |

### 2.3 ViewModel Migration Example

**Current (C# MAUI):**
```csharp
public partial class EntryLogViewModel : ObservableObject
{
    private readonly ITrackedEntryRepository _entryRepository;

    [ObservableProperty]
    private DailySummaryCard? summaryCard;

    [ObservableProperty]
    private ObservableCollection<TrackedEntryCard> entries = new();

    [RelayCommand]
    private async Task ReloadEntriesAsync()
    {
        var dayEntries = await _entryRepository.GetEntriesForDayAsync(CurrentDate);
        Entries = new ObservableCollection<TrackedEntryCard>(dayEntries.Select(MapToCard));
    }
}
```

**Target (Dart/Riverpod):**
```dart
@riverpod
class EntryLogNotifier extends _$EntryLogNotifier {
  @override
  EntryLogState build() => const EntryLogState();

  Future<void> reloadEntries() async {
    final repository = ref.read(trackedEntryRepositoryProvider);
    final entries = await repository.getEntriesForDay(state.currentDate);
    state = state.copyWith(
      entries: entries.map((e) => e.toCard()).toList(),
    );
  }
}

@freezed
class EntryLogState with _$EntryLogState {
  const factory EntryLogState({
    DailySummaryCard? summaryCard,
    @Default([]) List<TrackedEntryCard> entries,
    @Default(DateTime.now()) DateTime currentDate,
  }) = _EntryLogState;
}
```

---

## 3. Feature-by-Feature Analysis

### 3.1 Core Features

| Feature | Current Implementation | Flutter Approach |
|---------|----------------------|------------------|
| **Meal Photo Capture** | AndroidCameraCaptureService + MediaStore | camera package + custom capture screen |
| **LLM Image Analysis** | OpenAiLlmClient / GeminiLlmClient | REST API + google_generative_ai |
| **Nutritional Tracking** | EntryAnalysis with MealAnalysisResult | Same model structure with Freezed |
| **Exercise Logging** | TrackedEntry with ExercisePayload | Direct port with Drift tables |
| **Sleep Tracking** | TrackedEntry with SleepPayload | Direct port with Drift tables |
| **Daily Summaries** | DailySummaryService + LLM | Same service pattern |
| **Voice Notes** | AudioRecordingService + Whisper | record package + OpenAI API |

### 3.2 UI Screens to Recreate

| Screen | MAUI File | Complexity | Notes |
|--------|-----------|------------|-------|
| MainPage | MainPage.xaml | Medium | Entry list, photo button, daily totals |
| PhotoReviewPage | PhotoReviewPage.xaml | Medium | Photo preview, voice notes, submit |
| MealDetailPage | MealDetailPage.xaml | Medium | Nutritional breakdown, corrections |
| ExerciseDetailPage | ExerciseDetailPage.xaml | Low | Exercise metrics display |
| SleepDetailPage | SleepDetailPage.xaml | Low | Sleep quality display |
| DailySummaryPage | DailySummaryPage.xaml | Medium | AI insights, recommendations |
| DayDetailPage | DayDetailPage.xaml | Medium | Timeline of day's entries |
| WeekViewPage | WeekViewPage.xaml | Medium | 7-day calendar grid |
| MonthViewPage | MonthViewPage.xaml | Medium | Monthly overview |
| YearViewPage | YearViewPage.xaml | Medium | Yearly trends |
| SettingsPage | SettingsPage.xaml | Low | Provider selection, API keys |
| ShareEntryPage | ShareEntryPage.xaml | Low | Export entry data |

### 3.3 LLM Integration Details

**OpenAI Client Migration:**
```dart
class OpenAiLlmClient implements LlmClient {
  final String apiKey;
  final http.Client _httpClient;

  @override
  Future<AnalysisResult> invokeAnalysis({
    required TrackedEntry entry,
    required LlmRequestContext context,
  }) async {
    final imageBytes = await File(entry.blobPath).readAsBytes();
    final base64Image = base64Encode(imageBytes);

    final response = await _httpClient.post(
      Uri.parse('https://api.openai.com/v1/chat/completions'),
      headers: {
        'Authorization': 'Bearer $apiKey',
        'Content-Type': 'application/json',
      },
      body: jsonEncode({
        'model': context.modelId,
        'messages': [
          {
            'role': 'user',
            'content': [
              {'type': 'text', 'text': _buildAnalysisPrompt(entry)},
              {
                'type': 'image_url',
                'image_url': {'url': 'data:image/jpeg;base64,$base64Image'}
              }
            ]
          }
        ],
        'response_format': {'type': 'json_object'},
      }),
    );

    return _parseResponse(response);
  }
}
```

**Gemini Client Migration:**
```dart
class GeminiLlmClient implements LlmClient {
  final GenerativeModel _model;

  @override
  Future<AnalysisResult> invokeAnalysis({
    required TrackedEntry entry,
    required LlmRequestContext context,
  }) async {
    final imageBytes = await File(entry.blobPath).readAsBytes();

    final content = [
      Content.multi([
        TextPart(_buildAnalysisPrompt(entry)),
        DataPart('image/jpeg', imageBytes),
      ])
    ];

    final response = await _model.generateContent(content);
    return _parseResponse(response.text ?? '');
  }
}
```

---

## 4. Data Layer Migration

### 4.1 Database Schema

**Current EF Core Migrations:**
1. `InitialCreate` - Core tables
2. `AddSchemaVersionToEntryAnalysis` - Schema versioning
3. `AddProcessingStatusToTrackedEntry` - Processing workflow
4. `AddCapturedAtTimeZoneMetadata` - Timezone tracking
5. `AddUserNotesToTrackedEntry` - User corrections

**Drift Table Definitions:**

```dart
// tracked_entries.dart
class TrackedEntries extends Table {
  IntColumn get entryId => integer().autoIncrement()();
  TextColumn get externalId => text().nullable()();
  TextColumn get entryType => textEnum<EntryType>()();
  DateTimeColumn get capturedAt => dateTime()();
  TextColumn get capturedAtTimeZoneId => text().nullable()();
  IntColumn get capturedAtOffsetMinutes => integer().nullable()();
  TextColumn get blobPath => text().nullable()();
  TextColumn get dataPayload => text()(); // JSON
  IntColumn get dataSchemaVersion => integer().withDefault(const Constant(1))();
  TextColumn get processingStatus => textEnum<ProcessingStatus>()();
  TextColumn get userNotes => text().nullable()();
}

// entry_analyses.dart
class EntryAnalyses extends Table {
  IntColumn get analysisId => integer().autoIncrement()();
  IntColumn get entryId => integer().references(TrackedEntries, #entryId)();
  TextColumn get externalId => text().nullable()();
  TextColumn get providerId => text()();
  TextColumn get model => text()();
  DateTimeColumn get capturedAt => dateTime()();
  TextColumn get insightsJson => text()();
  IntColumn get schemaVersion => integer().withDefault(const Constant(1))();
}

// daily_summaries.dart
class DailySummaries extends Table {
  IntColumn get summaryId => integer().autoIncrement()();
  TextColumn get externalId => text().nullable()();
  DateTimeColumn get summaryDate => dateTime()();
  TextColumn get highlights => text()(); // JSON
  TextColumn get recommendations => text()(); // JSON
}
```

**Drift Migration Strategy:**
```dart
@DriftDatabase(tables: [TrackedEntries, EntryAnalyses, DailySummaries])
class AppDatabase extends _$AppDatabase {
  AppDatabase() : super(_openConnection());

  @override
  int get schemaVersion => 5;

  @override
  MigrationStrategy get migration => MigrationStrategy(
    onCreate: (m) => m.createAll(),
    onUpgrade: (m, from, to) async {
      if (from < 2) {
        await m.addColumn(entryAnalyses, entryAnalyses.schemaVersion);
      }
      if (from < 3) {
        await m.addColumn(trackedEntries, trackedEntries.processingStatus);
      }
      if (from < 4) {
        await m.addColumn(trackedEntries, trackedEntries.capturedAtTimeZoneId);
        await m.addColumn(trackedEntries, trackedEntries.capturedAtOffsetMinutes);
      }
      if (from < 5) {
        await m.addColumn(trackedEntries, trackedEntries.userNotes);
      }
    },
  );
}
```

### 4.2 Repository Pattern Preservation

```dart
// Interface (matches current ITrackedEntryRepository)
abstract class TrackedEntryRepository {
  Future<TrackedEntry?> getByIdAsync(int entryId);
  Future<List<TrackedEntry>> getEntriesForDayAsync(DateTime date);
  Future<List<TrackedEntry>> getEntriesForWeekAsync(DateTime weekStart);
  Future<int> addAsync(TrackedEntry entry);
  Future<void> updateAsync(TrackedEntry entry);
  Future<void> deleteAsync(int entryId);
  Future<void> updateProcessingStatusAsync(int entryId, ProcessingStatus status);
}

// Implementation with Drift
class DriftTrackedEntryRepository implements TrackedEntryRepository {
  final AppDatabase _db;

  DriftTrackedEntryRepository(this._db);

  @override
  Future<List<TrackedEntry>> getEntriesForDayAsync(DateTime date) async {
    final startOfDay = DateTime(date.year, date.month, date.day);
    final endOfDay = startOfDay.add(const Duration(days: 1));

    final results = await (_db.select(_db.trackedEntries)
      ..where((t) => t.capturedAt.isBetweenValues(startOfDay, endOfDay))
      ..orderBy([(t) => OrderingTerm.desc(t.capturedAt)]))
      .get();

    return results.map(_mapToModel).toList();
  }
}
```

### 4.3 Secure Storage Migration

```dart
// Mirrors SecureStorageAppSettingsRepository
class SecureStorageSettingsRepository implements AppSettingsRepository {
  final FlutterSecureStorage _storage;
  static const _settingsKey = 'app_settings';

  @override
  Future<AppSettings> getSettingsAsync() async {
    final json = await _storage.read(key: _settingsKey);
    if (json == null) return AppSettings.defaults();
    return AppSettings.fromJson(jsonDecode(json));
  }

  @override
  Future<void> saveSettingsAsync(AppSettings settings) async {
    await _storage.write(
      key: _settingsKey,
      value: jsonEncode(settings.toJson()),
    );
  }
}
```

---

## 5. Testing Strategy

### 5.1 Current Test Coverage

| Test Type | Framework | Count | Location |
|-----------|-----------|-------|----------|
| Unit Tests | xUnit | ~6 classes | WellnessWingman.Tests/ |
| E2E Tests | Appium + xUnit | ~13 tests | WellnessWingman.UITests/ |

**Current Unit Test Coverage:**
- `SqliteTrackedEntryRepositoryTests` - Repository CRUD operations
- `DailySummaryServiceTests` - Daily summary generation
- `DailyTotalsCalculatorTests` - Nutritional totals calculation
- `UnifiedAnalysisApplierTests` - Entry type conversion
- `WeekSummaryBuilderTests` - Weekly aggregation
- `DateTimeConverterTests` - Timezone handling

**Current E2E Test Coverage:**
- `SmokeTests` - App launch, main page display (4 tests)
- `NavigationTests` - Flyout navigation (1 test)
- `SettingsTests` - Settings page functionality (4 tests)
- `EntryCreationTests` - Photo capture workflow (4 tests)

### 5.2 Flutter Test Strategy

#### Unit Tests (flutter_test + mocktail)

```dart
// Example: DailySummaryService test migration
void main() {
  late AppDatabase database;
  late TrackedEntryRepository entryRepository;
  late MockLlmClient mockLlmClient;
  late DailySummaryService service;

  setUp(() {
    database = AppDatabase(NativeDatabase.memory());
    entryRepository = DriftTrackedEntryRepository(database);
    mockLlmClient = MockLlmClient();
    service = DailySummaryService(entryRepository, mockLlmClient);
  });

  tearDown(() => database.close());

  test('generates daily summary from completed entries', () async {
    // Arrange
    await entryRepository.addAsync(createTestMealEntry());
    when(() => mockLlmClient.invokeDailySummary(any()))
        .thenAnswer((_) async => mockSummaryResult);

    // Act
    final result = await service.generateSummaryAsync(DateTime.now());

    // Assert
    expect(result.isSuccess, true);
    verify(() => mockLlmClient.invokeDailySummary(any())).called(1);
  });
}
```

#### Widget Tests (New Category)

```dart
void main() {
  testWidgets('MainPage shows take photo button', (tester) async {
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          entryLogNotifierProvider.overrideWith(() => MockEntryLogNotifier()),
        ],
        child: const MaterialApp(home: MainPage()),
      ),
    );

    expect(find.byKey(const Key('takePhotoButton')), findsOneWidget);
    expect(find.text('Take Photo'), findsOneWidget);
  });

  testWidgets('MealDetailPage displays nutritional breakdown', (tester) async {
    final testEntry = createTestMealEntry();

    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          mealDetailNotifierProvider(testEntry.entryId)
              .overrideWith(() => MockMealDetailNotifier(testEntry)),
        ],
        child: MaterialApp(home: MealDetailPage(entryId: testEntry.entryId)),
      ),
    );

    expect(find.text('Calories'), findsOneWidget);
    expect(find.text('Protein'), findsOneWidget);
  });
}
```

#### E2E Tests (Patrol)

```dart
// Page Object Pattern preservation
class MainPageObject {
  final PatrolTester $;

  MainPageObject(this.$);

  Finder get takePhotoButton => $(#takePhotoButton);
  Finder get entriesList => $(#entriesList);
  Finder get dailySummaryCard => $(#dailySummaryCard);

  Future<void> tapTakePhotoButton() async {
    await takePhotoButton.tap();
  }

  Future<bool> isDisplayed() async {
    return $(#mainPageTitle).exists;
  }

  Future<void> waitForEntriesLoaded() async {
    await $.waitUntilVisible(entriesList);
  }
}

// Test migration
void main() {
  patrolTest('app launches and shows main page', ($) async {
    final mainPage = MainPageObject($);

    await $.pumpWidgetAndSettle(const MyApp());

    expect(await mainPage.isDisplayed(), true);
    expect(mainPage.takePhotoButton.exists, true);
  });

  patrolTest('can navigate to settings via flyout', ($) async {
    final mainPage = MainPageObject($);
    final settingsPage = SettingsPageObject($);

    await $.pumpWidgetAndSettle(const MyApp());
    await $.native.openDrawer();
    await $(#settingsMenuItem).tap();

    expect(await settingsPage.isDisplayed(), true);
  });

  patrolTest('photo capture workflow completes', ($) async {
    final mainPage = MainPageObject($);
    final photoReviewPage = PhotoReviewPageObject($);

    await $.pumpWidgetAndSettle(const MyApp());
    await mainPage.tapTakePhotoButton();

    // Grant camera permission if prompted
    await $.native.grantPermissionWhenInUse();

    // Take photo (uses mock camera in test)
    await $(#captureButton).tap();

    expect(await photoReviewPage.isDisplayed(), true);
  });
}
```

### 5.3 Mock Services Migration

**Current Mock Services:**
- `MockLlmClient` - Returns hardcoded analysis JSON
- `MockCameraCaptureService` - Copies sample-meal.png
- `MockAudioTranscriptionService` - Returns fixed text

**Flutter Mock Services:**

```dart
// MockLlmClient
class MockLlmClient implements LlmClient {
  @override
  Future<AnalysisResult> invokeAnalysis({
    required TrackedEntry entry,
    required LlmRequestContext context,
    String? existingAnalysisJson,
    String? userProvidedDetails,
  }) async {
    await Future.delayed(const Duration(milliseconds: 500)); // Simulate delay

    return AnalysisResult.success(
      UnifiedAnalysisResult(
        schemaVersion: '1.0',
        entryType: EntryType.meal,
        confidence: 0.95,
        mealAnalysis: MealAnalysisResult(
          foodItems: [
            FoodItem(name: 'Grilled Chicken', portion: '150g'),
            FoodItem(name: 'Brown Rice', portion: '1 cup'),
          ],
          nutrition: NutritionInfo(
            calories: 450,
            protein: 35,
            carbs: 45,
            fat: 12,
          ),
        ),
      ),
    );
  }
}

// MockCameraCaptureService
class MockCameraCaptureService implements CameraCaptureService {
  @override
  Future<String?> capturePhotoAsync() async {
    final directory = await getApplicationDocumentsDirectory();
    final sampleImage = File('${directory.path}/test_assets/sample-meal.png');
    final destPath = '${directory.path}/images/capture_${DateTime.now().millisecondsSinceEpoch}.jpg';

    await sampleImage.copy(destPath);
    return destPath;
  }
}
```

**Mock Service Activation (mirrors marker file approach):**

```dart
// In main.dart or test setup
final useMockServices = Platform.environment['USE_MOCK_SERVICES'] == 'true' ||
    await File('${appDataDir.path}/.use_mock_services').exists();

void main() {
  runApp(
    ProviderScope(
      overrides: useMockServices ? [
        llmClientProvider.overrideWith((ref) => MockLlmClient()),
        cameraCaptureServiceProvider.overrideWith((ref) => MockCameraCaptureService()),
        audioTranscriptionServiceProvider.overrideWith((ref) => MockAudioTranscriptionService()),
      ] : [],
      child: const MyApp(),
    ),
  );
}
```

### 5.4 Test Count Targets

| Test Type | Current | Flutter Target |
|-----------|---------|----------------|
| Unit Tests | 6 classes | 10+ classes |
| Widget Tests | N/A | 15+ tests |
| E2E Tests | 13 tests | 15+ tests |
| Integration Tests | In-memory | Repository integration tests |

---

## 6. Platform-Specific Implementations

### 6.1 Android

| Feature | Implementation |
|---------|----------------|
| **Foreground Service** | `workmanager` with `flutter_local_notifications` for ongoing analysis |
| **Camera** | `camera` package with custom viewfinder |
| **Share Intent (receive)** | `receive_sharing_intent` + AndroidManifest intent-filter |
| **Permissions** | Runtime permissions via `permission_handler` |

**Android Foreground Service:**
```dart
@pragma('vm:entry-point')
void callbackDispatcher() {
  Workmanager().executeTask((task, inputData) async {
    final notificationService = FlutterLocalNotificationsPlugin();

    // Show ongoing notification
    await notificationService.show(
      1,
      'Analyzing meal',
      'Processing your photo...',
      const NotificationDetails(
        android: AndroidNotificationDetails(
          'analysis_channel',
          'Analysis',
          ongoing: true,
          showProgress: true,
        ),
      ),
    );

    // Process entry
    final entryId = inputData?['entryId'] as int;
    await _processEntry(entryId);

    // Cancel notification
    await notificationService.cancel(1);
    return true;
  });
}
```

### 6.2 iOS

| Feature | Implementation |
|---------|----------------|
| **Background Tasks** | `workmanager` with BGTaskScheduler (limited to ~30s) |
| **Camera** | `image_picker` or `camera` package |
| **Secure Storage** | Keychain via `flutter_secure_storage` |
| **Audio Recording** | `record` package (AVAudioRecorder under the hood) |

**iOS Background Task Considerations:**
- iOS limits background execution to ~30 seconds
- Consider deferring LLM analysis to when app is in foreground
- Use `BGProcessingTaskRequest` for longer tasks (requires user opt-in)

### 6.3 Platform Channel Requirements

| Feature | Needs Platform Channel |
|---------|----------------------|
| Camera capture | No (use packages) |
| Audio recording | No (use packages) |
| Background tasks | No (use workmanager) |
| Share intents | Partial (receive_sharing_intent handles most) |
| Secure storage | No (use flutter_secure_storage) |

---

## 7. Effort Estimation

### 7.1 Detailed Breakdown

| Component | Complexity | Days | Dependencies |
|-----------|------------|------|--------------|
| **Phase 1: Infrastructure** |
| Project setup + packages | Low | 2 | - |
| Drift database + migrations | Medium | 3 | Project setup |
| Model classes (Freezed) | Medium | 4 | Drift |
| Riverpod providers | Medium | 3 | Models |
| **Subtotal** | | **12** | |
| **Phase 2: Services** |
| Repository implementations | Medium | 4 | Database |
| Secure storage settings | Low | 1 | - |
| LLM client interface | Medium | 2 | - |
| OpenAI client | High | 4 | LLM interface |
| Gemini client | High | 4 | LLM interface |
| Background analysis service | High | 5 | LLM clients, repos |
| Camera service | Medium | 3 | Permissions |
| Audio recording service | Medium | 3 | Permissions |
| Audio transcription | Medium | 3 | LLM clients |
| Navigation (go_router) | Low | 2 | - |
| Share service | Medium | 3 | Platform channels |
| **Subtotal** | | **34** | |
| **Phase 3: UI** |
| MainPage | Medium | 3 | EntryLogNotifier |
| PhotoReviewPage | Medium | 3 | Camera service |
| MealDetailPage | Medium | 3 | Repos, LLM |
| ExerciseDetailPage | Medium | 2 | Repos |
| SleepDetailPage | Medium | 2 | Repos |
| DailySummaryPage | Medium | 3 | Summary service |
| SettingsPage | Low | 2 | Secure storage |
| ShareEntryPage | Low | 2 | Share service |
| WeekViewPage | Medium | 3 | Navigation |
| MonthViewPage | Medium | 3 | Navigation |
| YearViewPage | Medium | 3 | Navigation |
| DayDetailPage | Medium | 2 | Navigation |
| **Subtotal** | | **31** | |
| **Phase 4: Platform** |
| Android foreground service | High | 3 | Background service |
| iOS background optimization | Medium | 2 | Background service |
| Android share intent | Medium | 2 | Share service |
| **Subtotal** | | **7** | |
| **Phase 5: Testing** |
| Unit test infrastructure | Low | 2 | Core complete |
| Unit tests migration | Medium | 5 | Infrastructure |
| Widget tests | Medium | 5 | Pages complete |
| E2E infrastructure (Patrol) | Medium | 3 | Pages complete |
| E2E tests migration | High | 5 | E2E infrastructure |
| **Subtotal** | | **20** | |
| **Total** | | **104 days** | |

### 7.2 Summary by Phase

| Phase | Duration | Calendar Weeks |
|-------|----------|----------------|
| 1. Infrastructure | 12 days | 2.5 |
| 2. Services | 34 days | 7 |
| 3. UI | 31 days | 6 |
| 4. Platform | 7 days | 1.5 |
| 5. Testing | 20 days | 4 |
| **Total** | **104 days** | **~21 weeks** |

**With 20% buffer:** ~25 weeks (6 months)

---

## 8. Migration Phases

### Phase 1: Foundation (Weeks 1-3)
**Goal:** Core infrastructure and data layer

**Deliverables:**
- Flutter project with folder structure mirroring MAUI
- Drift database with all tables and migrations
- Freezed model classes
- Repository implementations
- Riverpod provider architecture
- go_router navigation setup

**Exit Criteria:**
- Database operations work in isolation tests
- Navigation shell functional

### Phase 2: LLM Integration (Weeks 4-7)
**Goal:** AI analysis capabilities

**Deliverables:**
- LlmClient abstraction layer
- OpenAI client (REST-based)
- Gemini client (SDK-based)
- LlmClientFactory provider
- Audio transcription service
- Mock services for testing
- BackgroundAnalysisService

**Exit Criteria:**
- Can analyze images with both providers
- Mock mode works for testing

### Phase 3: Core UI (Weeks 8-14)
**Goal:** Primary user workflows

**Deliverables:**
- MainPage with entry list
- PhotoReviewPage with camera
- MealDetailPage with corrections
- ExerciseDetailPage
- SleepDetailPage
- SettingsPage
- DailySummaryPage

**Exit Criteria:**
- Complete meal tracking workflow functional
- Settings persist correctly

### Phase 4: Historical Views (Weeks 15-17)
**Goal:** Complete feature parity

**Deliverables:**
- DayDetailPage
- WeekViewPage
- MonthViewPage
- YearViewPage
- Share intent handling
- ShareEntryPage

**Exit Criteria:**
- All historical navigation works
- Can share entries externally

### Phase 5: Platform Optimization (Weeks 18-19)
**Goal:** Platform-specific polish

**Deliverables:**
- Android foreground service
- iOS background optimization
- Performance profiling
- Memory optimization for images

**Exit Criteria:**
- Background analysis reliable on both platforms
- No memory leaks with large images

### Phase 6: Testing & QA (Weeks 20-25)
**Goal:** Production readiness

**Deliverables:**
- Unit test suite (10+ classes)
- Widget test suite (15+ tests)
- E2E test suite with Patrol (15+ tests)
- Bug fixes
- Documentation

**Exit Criteria:**
- All tests passing
- Manual QA complete
- Release candidate ready

---

## 9. Challenges and Mitigations

### 9.1 Technical Challenges

| Challenge | Impact | Mitigation |
|-----------|--------|------------|
| **No official OpenAI Flutter SDK** | High | Use REST API directly; abstract behind interface |
| **iOS background execution limits** | High | Queue analysis; process when app active; optimize API calls |
| **Large image handling** | Medium | Compress before upload; stream processing |
| **Foreground service (Android 14+)** | Medium | Proper manifest declarations; notification permissions |
| **Data migration from MAUI** | Medium | Export/import mechanism; version schema |

### 9.2 Platform-Specific Challenges

| Platform | Challenge | Mitigation |
|----------|-----------|------------|
| Android | Scoped storage restrictions | Use app-specific directories |
| Android | Foreground service type declaration | Declare `dataSync` type in manifest |
| iOS | Background task scheduling | Use `BGProcessingTaskRequest` for long tasks |
| iOS | App Store review (API keys) | Document user-provided keys only |

### 9.3 Migration Risks

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| Data loss | Low | High | Backup mechanism; staged rollout |
| Feature gaps | Medium | Medium | Comprehensive checklist; beta testing |
| Performance regression | Medium | Medium | Benchmark critical paths; profile early |
| LLM API changes | Low | Medium | Abstract layer; version prompts |

---

## 10. Recommended Package Dependencies

### pubspec.yaml

```yaml
name: wellness_wingman
description: Privacy-first wellness tracking with AI-powered meal analysis
publish_to: 'none'
version: 1.0.0+1

environment:
  sdk: '>=3.0.0 <4.0.0'
  flutter: '>=3.10.0'

dependencies:
  flutter:
    sdk: flutter

  # State Management
  flutter_riverpod: ^2.5.0
  riverpod_annotation: ^2.3.0

  # Database & Storage
  drift: ^2.15.0
  sqlite3_flutter_libs: ^0.5.0
  path_provider: ^2.1.0
  flutter_secure_storage: ^9.0.0

  # Code Generation Support
  freezed_annotation: ^2.4.0
  json_annotation: ^4.8.0

  # Navigation
  go_router: ^14.0.0

  # Platform Services
  camera: ^0.10.0
  image_picker: ^1.0.0
  record: ^5.0.0
  permission_handler: ^11.0.0
  share_plus: ^7.0.0
  receive_sharing_intent: ^1.6.0

  # Background Processing
  workmanager: ^0.5.0
  flutter_local_notifications: ^17.0.0

  # AI/LLM
  google_generative_ai: ^0.4.0
  http: ^1.2.0

  # UI Components
  flutter_slidable: ^3.0.0
  cached_network_image: ^3.3.0

  # Utilities
  intl: ^0.19.0
  uuid: ^4.0.0
  collection: ^1.18.0

dev_dependencies:
  flutter_test:
    sdk: flutter
  flutter_lints: ^4.0.0

  # Code Generation
  build_runner: ^2.4.0
  drift_dev: ^2.15.0
  freezed: ^2.4.0
  json_serializable: ^6.7.0
  riverpod_generator: ^2.4.0

  # Testing
  mocktail: ^1.0.0
  patrol: ^3.0.0
```

---

## 11. Project Structure

```
wellness_wingman/
├── lib/
│   ├── main.dart
│   ├── app.dart
│   │
│   ├── core/
│   │   ├── providers/           # Riverpod providers
│   │   ├── routing/             # go_router configuration
│   │   ├── theme/               # App theme
│   │   └── utils/               # Utilities (DateTimeConverter, etc.)
│   │
│   ├── data/
│   │   ├── database/
│   │   │   ├── app_database.dart
│   │   │   ├── tables/
│   │   │   └── daos/
│   │   ├── repositories/
│   │   │   ├── tracked_entry_repository.dart
│   │   │   ├── entry_analysis_repository.dart
│   │   │   └── daily_summary_repository.dart
│   │   └── secure_storage/
│   │       └── app_settings_repository.dart
│   │
│   ├── domain/
│   │   ├── models/
│   │   │   ├── tracked_entry.dart
│   │   │   ├── entry_analysis.dart
│   │   │   ├── daily_summary.dart
│   │   │   ├── payloads/
│   │   │   └── analysis_results/
│   │   └── enums/
│   │       ├── entry_type.dart
│   │       └── processing_status.dart
│   │
│   ├── features/
│   │   ├── entry_log/
│   │   │   ├── entry_log_page.dart
│   │   │   └── entry_log_notifier.dart
│   │   ├── photo_review/
│   │   ├── meal_detail/
│   │   ├── exercise_detail/
│   │   ├── sleep_detail/
│   │   ├── daily_summary/
│   │   ├── historical/
│   │   │   ├── day_detail/
│   │   │   ├── week_view/
│   │   │   ├── month_view/
│   │   │   └── year_view/
│   │   ├── settings/
│   │   └── share/
│   │
│   ├── services/
│   │   ├── analysis/
│   │   │   ├── analysis_orchestrator.dart
│   │   │   ├── background_analysis_service.dart
│   │   │   └── daily_summary_service.dart
│   │   ├── llm/
│   │   │   ├── llm_client.dart
│   │   │   ├── openai_llm_client.dart
│   │   │   ├── gemini_llm_client.dart
│   │   │   └── mock_llm_client.dart
│   │   ├── media/
│   │   │   ├── camera_capture_service.dart
│   │   │   └── audio_recording_service.dart
│   │   └── transcription/
│   │       └── audio_transcription_service.dart
│   │
│   └── widgets/                 # Shared widgets
│       ├── entry_card.dart
│       ├── nutrition_summary.dart
│       └── loading_indicator.dart
│
├── test/
│   ├── unit/
│   │   ├── data/
│   │   └── services/
│   ├── widget/
│   │   └── features/
│   └── helpers/
│       └── test_utils.dart
│
├── integration_test/
│   ├── page_objects/
│   ├── tests/
│   └── app_test.dart
│
├── android/
├── ios/
└── pubspec.yaml
```

---

## 12. Critical Reference Files

These files from the current MAUI implementation should be referenced during migration:

| Purpose | File Path |
|---------|-----------|
| LLM Interface | `WellnessWingman/Services/Llm/ILLmClient.cs` |
| Database Schema | `WellnessWingman/Data/WellnessWingmanDbContext.cs` |
| Main ViewModel | `WellnessWingman/PageModels/EntryLogViewModel.cs` |
| Background Processing | `WellnessWingman/Services/Analysis/BackgroundAnalysisService.cs` |
| Camera (Android) | `WellnessWingman/Platforms/Android/Services/AndroidCameraCaptureService.cs` |
| Analysis Orchestrator | `WellnessWingman/Services/Analysis/AnalysisOrchestrator.cs` |
| Mock LLM Client | `WellnessWingman/Services/Llm/MockLlmClient.cs` |
| E2E Page Objects | `WellnessWingman.UITests/PageObjects/` |
| E2E Tests | `WellnessWingman.UITests/Tests/` |

---

## Conclusion

Migrating WellnessWingman to Flutter is feasible with the recommended architecture and packages. The MVVM pattern translates well to Riverpod, and the repository pattern can be preserved with Drift. The main challenges are LLM SDK availability (solved with REST APIs) and iOS background processing limitations.

**Recommended approach:** Follow the phased migration plan, starting with infrastructure and LLM integration before building UI. This ensures the core functionality works before investing in screens.

**Key success factors:**
1. Early validation of LLM integration (both providers)
2. Comprehensive mock services for testing
3. Background task reliability testing on real devices
4. Parallel development of unit and widget tests with features
