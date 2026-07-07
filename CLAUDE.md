# CLAUDE.md

このリポジトリは **Android 16 ネイティブ対応の軽量ボリュームコントロールアプリ** を開発するプロジェクト。
詳細な設計は [docs/DESIGN.md](docs/DESIGN.md) を参照。本ファイルは開発時に常に守るべき方針を定める。

## プロジェクト概要

- 各音量ストリーム(メディア / 着信音 / 通知 / アラーム / 通話)を
  **スライダー(ドラッグ)** と **⬆️⬇️ボタン(1 ステップ微調整)** で制御する。
- リンガーモード(通常 / マナー / サイレント)をワンタップで切り替える。
- ハードウェアキー等による外部変更に UI が追従する。

## 技術スタック(決定事項)

| 項目 | 決定 | 理由 |
|------|------|------|
| 言語 | Kotlin | 音量制御は `AudioManager`(フレームワーク API)経由でしか不可能。C/Rust/Zig は JNI ラッパーが必要になり軽量・堅牢の両方を損なうため不採用 |
| UI | クラシック View(XML) | Jetpack Compose はランタイムで APK +2MB。SeekBar/Slider で要件を満たせる |
| compileSdk / targetSdk | 36 (Android 16) | ネイティブ対応要件 |
| minSdk | 31 (Android 12) | 互換分岐を最小化 |
| アーキテクチャ | 単一 Activity + `VolumeController` + `VolumeWatcher` | 小規模アプリに過剰な層を作らない。ViewModel/DI/DB は使わない |

## 絶対に守る方針

1. **依存最小主義**: 依存は `androidx.core-ktx` と `material` のみ。
   新しいライブラリの追加は原則禁止。追加する場合は APK サイズへの影響を明記して判断する。
2. **公開 API のみ**: リフレクションや hidden API は使わない。
   OS バージョンアップで壊れないことがこのプロジェクトの存在理由。
3. **ネットワークなし**: `INTERNET` パーミッションを要求しない。データ収集・広告・解析なし。
4. **状態の正は常に OS**: 音量・モードの値をアプリ側で永続化しない。
   常に `AudioManager` から読み、表示前に再同期する(`onResume` で全再読込)。
5. **権限がなくても落ちない**: DND アクセス(`ACCESS_NOTIFICATION_POLICY`)未許可の場合、
   サイレント切替は「許可導線を表示」に切り替え、他機能はそのまま動かす。
   `SecurityException` は必ず catch して UI を実値に戻す。
6. **端末差への防御**: `isVolumeFixed`、`getStreamMinVolume`(下限が 0 でないストリーム)、
   DND 中の RING 操作拒否を必ず考慮する。

## コーディング規約

- Kotlin 公式スタイル(`kotlin.code.style=official`)。
- パッケージは `dev.dopl.soundcontrol`。
- `AudioManager` を直接触ってよいのは `VolumeController` / `VolumeWatcher` のみ。
  Activity からは必ずこの 2 クラスを経由する。
- 文字列はすべて `strings.xml`(日本語をデフォルト、英語 `values-en` は任意)。
- マジックナンバー禁止。ストリーム定義は enum(表示名・アイコン・stream 定数を持つ)に集約。

## ビルド・テスト

```bash
./gradlew assembleDebug      # デバッグビルド
./gradlew test               # JVM 単体テスト(VolumeController 中心)
./gradlew lint               # lint
```

- `VolumeController` は AudioManager をインターフェースで抽象化してモックテストする(Robolectric 不使用)。
- UI の最終確認は Android 16 実機。リリース前チェックリストは docs 配下に置く。

## 実装ロードマップ(進捗はここを更新する)

- [x] M1: Gradle プロジェクト骨組み + CI(assembleDebug / test / lint)
- [x] M2: VolumeController + メディア音量のスライダー / ⬆️⬇️ボタン
- [x] M3: 全 5 ストリーム + リンガーモード切替 + DND 許可フロー
- [ ] M4: VolumeWatcher(外部変更追従)+ 堅牢化(例外・音量固定端末)
- [ ] M5: ダークテーマ・アイコン・APK サイズ確認・実機テスト

## スコープ外(勝手に実装しない)

- クイック設定タイル、ウィジェット、音量プリセット → 将来拡張として設計書に記載済み。
  着手する場合はユーザーの指示を待つ。
