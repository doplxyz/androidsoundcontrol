# 設計書: Android Sound Control

Android 16 (API 36) ネイティブ対応の軽量ボリュームコントロールアプリ。

## 1. 背景と目的

長年使っていたボリュームコントロールアプリが Android のバージョンアップで動作しなくなった。
その代替として、**Android 16 を第一級のターゲット**とし、OS のバージョンアップに強い
(= 非公開 API に依存しない)、軽量で堅牢な音量調整アプリを自作する。

## 2. 要件

### 2.1 機能要件

| ID | 要件 | 詳細 |
|----|------|------|
| F1 | スライダーによる音量調整 | ゲージ(バー)を指でつまんでドラッグし、各ストリームの音量を連続的に大小できる |
| F2 | ボタンによる微調整 | ⬆️ / ⬇️ ボタンで音量を 1 ステップずつ細かく調整できる |
| F3 | リンガーモード切替 | デフォルト(通常)/ マナー(バイブ)/ サイレント をワンタップで切り替えられる |
| F4 | 複数ストリーム対応 | メディア / 着信音 / 通知 / アラーム / 通話 の各音量を個別に制御できる |
| F5 | 外部変化への追従 | ハードウェアキー等で音量が変わったとき、UI が自動で追従する |

### 2.2 非機能要件

| ID | 要件 | 方針 |
|----|------|------|
| N1 | 軽量 | APK 1〜2MB 目標。サードパーティ依存ゼロ(AndroidX 最小限のみ)。ネットワーク通信なし、広告なし、解析なし |
| N2 | 堅牢 | 公開 API のみ使用。端末差(音量固定端末、DND ポリシー)を考慮したフォールバック。クラッシュゼロを目標 |
| N3 | Android 16 ネイティブ | compileSdk / targetSdk = 36。Android 16 実機で動作確認 |
| N4 | プライバシー | INTERNET パーミッションを要求しない。データ収集なし |

## 3. 技術選定

### 3.1 言語・プラットフォーム: **Kotlin(Android SDK 直接利用)**

ユーザー要望は「C / Rust / Zig でもよい」だが、以下の理由で **Kotlin を採用**する。

- Android の音量制御は `android.media.AudioManager` という **Java/Kotlin フレームワーク API 経由でしか行えない**。
  NDK(C/C++)や Rust/Zig には音量・リンガーモードを操作する公開 API が存在しない。
- C/Rust/Zig で書いた場合でも、結局 JNI 経由で `AudioManager` を呼ぶラッパーが必要になり、
  コード量・バイナリサイズ・クラッシュ経路(JNI 境界)がすべて増える。**「軽量・堅牢」の要件に反する**。
- 本アプリは計算負荷がほぼゼロ(API 呼び出しと UI のみ)であり、ネイティブ言語の性能メリットが出る場面がない。
- Kotlin + 依存最小構成なら APK は 1MB 台に収まり、Play 系ライブラリも不要。

> つまり「軽量・堅牢」を実現する最短経路が Kotlin である、という判断。

### 3.2 UI フレームワーク: **クラシック View(XML レイアウト)**

- Jetpack Compose はランタイムだけで APK が +2MB 以上になるため不採用(N1 軽量要件)。
- `SeekBar`(または Material `Slider`)+ `ImageButton` で要件 F1/F2 は完全に満たせる。
- 依存は `androidx.core-ktx` と `com.google.android.material`(テーマ・Slider 用)のみ。
  さらに削る場合は Material も外しプラットフォーム標準 `SeekBar` + プラットフォームテーマで構成可能。

### 3.3 SDK バージョン

| 項目 | 値 | 理由 |
|------|----|------|
| compileSdk | 36 | Android 16 ネイティブ対応 |
| targetSdk | 36 | 同上 |
| minSdk | 31 (Android 12) | 互換コード分岐をほぼゼロにしつつ、近年の端末をカバー |

## 4. アーキテクチャ

小規模アプリのため、レイヤーを増やしすぎない **単一 Activity + 薄い制御層** とする。

```
┌─────────────────────────────────────┐
│ MainActivity (単一 Activity)          │
│  - 画面構築、リスナー登録              │
│  - onResume/onPause でのライフサイクル │
├─────────────────────────────────────┤
│ VolumeController                     │
│  - AudioManager の薄いラッパー         │
│  - get/set/adjust、リンガーモード切替  │
│  - 端末差・例外のフォールバック処理     │
├─────────────────────────────────────┤
│ VolumeWatcher                        │
│  - 音量/モードの外部変化を監視 (F5)     │
│  - BroadcastReceiver +               │
│    ContentObserver(settings)         │
└─────────────────────────────────────┘
```

- **DI フレームワーク、Coroutines 以外の非同期基盤、DB は使わない**(状態は常に OS 側が正:
  アプリは AudioManager から読むだけで、自前の永続化を持たない → 堅牢)。
- ViewModel も不要(画面回転時は AudioManager から再読込すれば済む)。

### 4.1 主要 API(すべて公開 API)

| 機能 | API |
|------|-----|
| 音量取得 | `AudioManager.getStreamVolume(stream)` / `getStreamMaxVolume` / `getStreamMinVolume` |
| 音量設定 (F1) | `AudioManager.setStreamVolume(stream, index, flags)` |
| 1 ステップ調整 (F2) | `AudioManager.adjustStreamVolume(stream, ADJUST_RAISE / ADJUST_LOWER, flags)` |
| リンガーモード (F3) | `AudioManager.setRingerMode(RINGER_MODE_NORMAL / VIBRATE / SILENT)` |
| モード変化検知 (F5) | `AudioManager.ACTION_RINGER_MODE_CHANGED` ブロードキャスト |
| 音量変化検知 (F5) | `Settings.System` への `ContentObserver`(ポーリング併用のフォールバックあり) |
| 音量固定端末の検出 | `AudioManager.isVolumeFixed` |

### 4.2 対象ストリーム

| 表示名 | ストリーム |
|--------|-----------|
| メディア | `STREAM_MUSIC` |
| 着信音 | `STREAM_RING` |
| 通知 | `STREAM_NOTIFICATION` |
| アラーム | `STREAM_ALARM` |
| 通話 | `STREAM_VOICE_CALL` |

## 5. 権限設計

| 権限 | 用途 | 備考 |
|------|------|------|
| `ACCESS_NOTIFICATION_POLICY` | サイレントモードへの切替 (F3) | Android N 以降、DND(通知の鳴動制限)アクセスがないと `setRingerMode(SILENT)` 等が `SecurityException` になる。**初回にアクセス許可画面へ誘導**し、未許可の間はサイレントボタンを「許可が必要」状態で表示する |
| `VIBRATE` | モード切替時の触覚フィードバック(任意) | なくても動作する |

- INTERNET は**要求しない**(N4)。
- 権限がない状態でもクラッシュせず、できる操作だけ有効化する(堅牢性)。

## 6. UI 設計

```
┌──────────────────────────────┐
│  Sound Control               │
│                              │
│  モード:  [🔔通常] [📳マナー] [🔕ｻｲﾚﾝﾄ] │  ← F3: セグメントボタン
│                              │
│  🎵 メディア          7 / 15  │
│  ⬇️ ━━━━━●━━━━━━━ ⬆️        │  ← F1: スライダー / F2: ボタン
│                              │
│  📞 着信音            3 / 7   │
│  ⬇️ ━━━●━━━━━━━━━ ⬆️        │
│                              │
│  🔔 通知              3 / 7   │
│  ⬇️ ━━━●━━━━━━━━━ ⬆️        │
│                              │
│  ⏰ アラーム          5 / 7   │
│  ⬇️ ━━━━━●━━━━━━━ ⬆️        │
│                              │
│  🗣 通話              4 / 5   │
│  ⬇️ ━━━━━━●━━━━━━ ⬆️        │
└──────────────────────────────┘
```

- 各行 = 1 ストリーム。**スライダー(つまんでドラッグ)** と **⬇️/⬆️ ボタン(1 ステップ)** を併設。
- 現在値 / 最大値を数値でも表示(微調整の視認性)。
- モード切替は最上部に 3 択セグメント。現在モードをハイライト。
- ダークテーマ対応(DayNight テーマ)。
- 着信音・通知はサイレント/マナー時に自動で 0 になるため、モードと連動して無効化表示にする。

## 7. 堅牢性設計(エラー処理)

| ケース | 対応 |
|--------|------|
| DND アクセス未許可で SILENT/VIBRATE 切替 | 事前に `NotificationManager.isNotificationPolicyAccessGranted` を確認。未許可なら設定画面 (`ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS`) へ誘導 |
| `setStreamVolume` の `SecurityException`(DND 中の RING 操作等) | try-catch で握り、UI を実値に戻す + トースト表示 |
| 音量固定端末 (`isVolumeFixed`) | スライダー・ボタンを無効化し理由を表示 |
| ストリームの min > 0(通話音量など) | `getStreamMinVolume` をスライダー下限に反映 |
| 外部(ハードキー)での変更 | Watcher で検知して UI 再同期。取りこぼし対策として `onResume` でも全再読込 |

## 8. プロジェクト構成(予定)

```
androidsoundcontrol/
├── CLAUDE.md              # 開発方針(本設計に基づく)
├── docs/
│   └── DESIGN.md          # 本設計書
├── app/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── kotlin/dev/dopl/soundcontrol/
│       │   │   ├── MainActivity.kt
│       │   │   ├── VolumeController.kt
│       │   │   └── VolumeWatcher.kt
│       │   └── res/       # layout / values / drawable
│       └── test/          # VolumeController の単体テスト
├── build.gradle.kts
├── settings.gradle.kts
└── gradle/ (wrapper)
```

## 9. テスト方針

- `VolumeController` は AudioManager をインターフェースで抽象化し、JVM 単体テスト(Robolectric 不使用、モックのみ)。
- UI は実機(Android 16)での手動確認をリリース条件とする。確認項目チェックリストを docs に置く。
- CI(GitHub Actions)で `assembleDebug` + 単体テスト + lint を回す。

## 10. マイルストーン

1. **M1: 骨組み** — Gradle プロジェクト作成、CI、空の MainActivity がビルドできる
2. **M2: コア機能** — VolumeController + メディア音量のスライダー/⬆️⬇️ボタン (F1, F2)
3. **M3: 全ストリーム + モード切替** — 5 ストリーム対応、リンガーモード切替と DND 許可フロー (F3, F4)
4. **M4: 追従と堅牢化** — VolumeWatcher (F5)、エラー処理、音量固定端末対応
5. **M5: 仕上げ** — ダークテーマ、アイコン、APK サイズ確認、Android 16 実機テスト

## 11. 将来拡張(スコープ外だが設計上考慮)

- クイック設定タイル(`TileService`)からのモード切替
- ホーム画面ウィジェット
- 音量プリセット(ワンタップで複数ストリームを一括設定)
