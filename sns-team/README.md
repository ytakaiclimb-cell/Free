# ボルダリングジム SNS運用チーム

Claude Code のサブエージェントで構成したSNS運用チームです。定義は `.claude/agents/sns-*.md` にあります。

## メンバー
| エージェント | 役割 |
|---|---|
| `sns-director` | 編集長。依頼の入口。各メンバーに振り分けて投稿案をまとめる |
| `sns-planner` | 投稿カレンダー、キャンペーン・イベント企画 |
| `sns-copywriter` | キャプション、ハッシュタグ、媒体別の書き分け |
| `sns-visual-director` | 写真・リールの構成、撮影指示 |
| `sns-community-manager` | コメント・DM・口コミ返信、UGC活用 |
| `sns-safety-reviewer` | 公開前チェック（安全・肖像権・景表法・著作権・事実） |
| `sns-analyst` | インサイト分析、週次・月次レポート |

## 使い始める前に
`gym-profile.md` の `（未記入）` を埋めてください。料金や営業時間などの事実は、ここに書かれた内容だけが使われます。

## 使い方の例
- 「sns-director で来月の投稿カレンダーを作って」
- 「来週のセット替えを告知するリールを作って。公開前チェックまでやって」
- 「この口コミへの返信案を sns-community-manager で」
- 「先月のインサイトCSVを貼るので sns-analyst で振り返って」

## フォルダ
- `posts/` 完成した投稿案（`templates/post.md` 形式）
- `reports/` 分析レポート
- `templates/` テンプレート
