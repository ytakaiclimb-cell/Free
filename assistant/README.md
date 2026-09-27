# 私専用アシスタント AI

Claude API (Anthropic) で動く、自分ひとりのためのチャットアシスタントです。
プロフィールと会話から覚えたことをもとに、秘書・相談相手・調べ物係として手伝ってくれます。

## できること

| 機能 | 内容 |
|---|---|
| **長期記憶** | 会話の中で分かった好みや予定を自動で覚え、次回以降の会話に活かす |
| **ToDo** | 「来週水曜に歯医者」と言えば期限付きで登録。一覧・完了も会話で |
| **メモ** | アイデアや調べた結果を保存・検索 |
| **Web 検索** | 最新情報を調べて出典付きで回答 |
| **日時の理解** | 「明日」「来週」を日本時間で正しく解釈 |

データはすべて `data/` フォルダ (JSON / Markdown) に保存され、Git には含まれません。

## セットアップ

1. [Anthropic Console](https://console.anthropic.com/) で API キーを発行
2. 以下を実行

```bash
cd assistant
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
export ANTHROPIC_API_KEY=sk-ant-...
python assistant.py
```

3. 初回起動で `data/profile.md` が作られるので、自分の情報に書き換えて `/reset`

## 使い方

```
あなた> 私はコーヒーはブラック派で、朝型です
アシスタント>
  ⚙ remember
  ⚙ remember
覚えました！朝のうちに集中作業を入れる提案もできますよ。

あなた> 来週水曜に歯医者
アシスタント>
  ⚙ get_current_time
  ⚙ add_todo
10/7 (水) に「歯医者」を登録しました。
```

| コマンド | 動作 |
|---|---|
| `/memory` | 覚えていることの一覧 |
| `/reset` | 会話をリセット (プロフィール・記憶を読み直す) |
| `/exit` | 終了 |

## カスタマイズ

- **性格・口調**: `assistant.py` の `BASE_PROMPT` を編集
- **モデル**: 環境変数 `ASSISTANT_MODEL` (既定 `claude-opus-5`)
- **考える深さ**: 環境変数 `ASSISTANT_EFFORT` (`low` / `medium` / `high` など。既定 `medium`)
- **ツールの追加**: `tools.py` に `@beta_tool` 付きの関数を書き、`ALL_TOOLS` に加える

Claude が回答を断った場合は、サーバー側のフォールバック (`fallbacks: "default"`) で別モデルが自動で引き継ぎます。
