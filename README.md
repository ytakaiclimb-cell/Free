# Pocket Classic

Galaxy Z Fold 向けの iPod classic 風ホームアプリ（ランチャー）です。
画面下のクリックホイールで操作します。

## APK のダウンロード（スマホだけで完結します）

**[最新ビルドをダウンロード](https://github.com/ytakaiclimb-cell/Free/releases/latest/download/pocket-classic.apk)**

このリンクは常に最新のビルドを指します。ブックマークしておけば、更新のたびに
同じリンクから落とせます。

1. 上のリンクをタップして `pocket-classic.apk` をダウンロード
2. 通知からファイルを開いてインストール
3. 初回のみ「提供元不明のアプリ」のインストール許可を求められるので許可
4. ホームアプリに設定する場合は、MENU → **Home App** から設定

APK は固定のデバッグ鍵で署名しているので、2 回目以降は
**アンインストールせずに上書きインストール**できます。

> 署名鍵 `app/debug.keystore` はリポジトリに入っています。Android SDK に同梱されている
> デバッグ鍵と同じ扱いで、Play ストア公開用ではありません。

## 画面

| 画面 | 内容 |
|---|---|
| **Apps**（ホーム） | アプリが円環に並ぶ。選択中のアプリ名を大きく表示し、下にサウンドウェーブ |
| **iPod**（メニュー） | Apps / Recents / Favorites / Cover Flow / Clock / All Apps / Notifications / Home App |
| **iPod**（時計モード） | 月カレンダー・デジタル時計・アナログ時計 |
| **All Apps** | 検索 + ページ送りのアプリ一覧 |

## クリックホイール

| 操作 | 動作 |
|---|---|
| なぞる | 選択を移動（1 ノッチごとに振動）。**速く回すと円環が広がり、サウンドウェーブが跳ねる** |
| **少し長押ししてから回す** | **メディア音量の調整**（画面左上にバー表示） |
| 中央ボタン | 決定（アプリ起動 / メニューを開く） |
| **中央ボタン長押し** | **ホームに戻る**。ただし**時計モード中は時計アプリを開く**（戻るとホームに復帰） |
| MENU | 1 つ戻る。ホームでは iPod メニューへ |
| ⏮ / ⏭ | 3 つぶん移動 |
| ⏯ | 時計モードの切り替え |
| 音楽再生中 | ホイールがレコードになり回り続ける |

## フェーダー（ホイール右の 2×2）

- **上下にスワイプ**で、その端に割り当てたアプリを起動（8 枠）
- 割り当ては **All Apps でアプリを長押し** → 枠を選択
- 割り当てたアプリに**通知がある間は色が変わります**
  （MENU → Notifications で通知アクセスを許可した場合のみ）
- **時計モード中は 1 / 3 / 5 / 10 / 15 / 30 / 45 / 60 分のタイマー**になります

## 画面下のスイッチ

| | |
|---|---|
| **Y** | Y Assistant アプリを開く |
| **時計マーク** | 時計モードの切り替え |
| **●** | ダークモードの切り替え（設定は保存されます） |

グレーのバーを**下から上にスワイプ**すると All Apps が開きます。

## 開発（スマホだけで進める場合）

- どのブランチに push しても GitHub Actions が APK をビルドします
- ビルドが通ると `latest` リリースの APK が差し替わります（上のリンクが常に最新）
- ビルド状況は [Actions タブ](https://github.com/ytakaiclimb-cell/Free/actions) で確認できます

## 構成

```
app/src/main/java/com/pocketlauncher/classic/
├── MainActivity.kt              ホームアプリ本体
├── data/
│   ├── AppRepository.kt         インストール済みアプリの一覧・アイコン・起動
│   └── Prefs.kt                 ダークモードとフェーダー割り当ての保存
├── service/
│   ├── BadgeListener.kt         通知アクセス（フェーダーのバッジ用）
│   └── Badges.kt                通知中パッケージの共有状態
├── util/
│   ├── SystemApps.kt            時計 / タイマー / 各種設定画面の起動
│   └── Volume.kt                メディア音量と再生中判定
└── ui/
    ├── Skin.kt                  ライト / ダークの配色
    ├── LauncherState.kt         画面・選択位置・ホイールの勢いなど
    ├── ClassicApp.kt            全体のレイアウト
    ├── StatusBar.kt             上部の時刻・バッテリー
    ├── HomeRing.kt              円環ホーム
    ├── SoundWave.kt             サウンドウェーブ
    ├── MenuScreen.kt            iPod メニュー
    ├── ClockScreen.kt           時計モード
    ├── DrawerScreen.kt          All Apps（検索・ページ・割り当て）
    ├── ClickWheel.kt            クリックホイール
    ├── Faders.kt                フェーダー
    └── BottomBar.kt             下部のスイッチとグラブバー
```

## 未実装

- **Recents / Cover Flow**（メニューに枠だけあります。Recents は使用状況アクセス権限が必要）
- **カレンダーの勤務ドット** — 表示するにはシフトの取得元が必要です

## ビルド要件

- JDK 17
- Android SDK (compileSdk 35) / minSdk 29
- `./gradlew assembleDebug`
