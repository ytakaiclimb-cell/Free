# Pocket Classic

Galaxy Z Fold 向けの、iPod classic 風ホームアプリ（ランチャー）です。
画面下のクリックホイールで操作します。

## APK のダウンロード（スマホだけで完結します）

**[最新ビルドをダウンロード](https://github.com/ytakaiclimb-cell/Free/releases/latest/download/pocket-classic.apk)**

このリンクは常に最新のビルドを指します。ブックマークしておけば、更新のたびに
同じリンクから落とせます。

1. 上のリンクをタップして `pocket-classic.apk` をダウンロード
2. 通知からファイルを開いてインストール
3. 初回のみ「提供元不明のアプリ」のインストール許可を求められるので許可
4. ホームアプリに設定する場合は、アプリ内メインメニューの **「既定のホームアプリ」** から設定

APK は固定のデバッグ鍵で署名しているので、2 回目以降は
**アンインストールせずに上書きインストール**できます。

> 署名鍵 `app/debug.keystore` はリポジトリに入っています。Android SDK に同梱されている
> デバッグ鍵と同じ扱いで、Play ストア公開用ではありません。

## 操作方法

| 操作 | 動作 |
|---|---|
| ホイールをなぞる | 選択を上下に移動（1 ノッチごとに軽く振動） |
| 中央ボタン | 決定（アプリ起動 / メニューを開く） |
| MENU | 1 つ戻る |
| ⏮ / ⏭ | 3 行ぶん移動 |
| ⏯ | 時計モードの切り替え |
| 画面下の時計マーク | 時計モードに入る／出る |
| **時計モード中に中央ボタンを長押し** | **端末の時計アプリを開く**（振動あり）。戻ってくると通常モードに戻る |

時計アプリは Samsung 純正 → Google 時計 → AOSP 時計 の順に探し、
いずれも無ければ標準のアラーム画面を開きます。

## 開発（スマホだけで進める場合）

- `main` 以外を含むどのブランチに push しても GitHub Actions が APK をビルドします
- ビルドが通ると `latest` リリースの APK が差し替わります（上のリンクが常に最新）
- ビルド状況は [Actions タブ](https://github.com/ytakaiclimb-cell/Free/actions) で確認できます

## 構成

```
app/src/main/java/com/pocketlauncher/classic/
├── MainActivity.kt          ホームアプリ本体。onResume で時計モードを抜ける
├── data/AppRepository.kt    インストール済みアプリの一覧・アイコン・起動
├── util/SystemApps.kt       時計アプリ / 設定の起動
└── ui/
    ├── LauncherState.kt     モード・ページ・選択位置。ホイールの操作先
    ├── ClassicApp.kt        画面 + ドック + ホイールのレイアウト
    ├── Display.kt           液晶パネル（タイトルバー・メニュー・スクロールバー）
    ├── ClockFace.kt         時計モード（アナログ＋デジタル）
    ├── ClickWheel.kt        クリックホイール。回転検出と中央ボタンの長押し
    ├── Dock.kt              画面下のショートカット（時計マークはここ）
    └── Palette.kt           配色
```

## ビルド要件

- JDK 17
- Android SDK (compileSdk 35) / minSdk 29
- Gradle は wrapper 同梱（`./gradlew assembleDebug`）
