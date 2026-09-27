"""私専用アシスタント AI のチャット CLI。

使い方:
    export ANTHROPIC_API_KEY=sk-ant-...
    python assistant.py
"""

import os
import shutil
import sys
from pathlib import Path

import anthropic

from tools import ALL_TOOLS, DATA_DIR, load_memories

MODEL = os.environ.get("ASSISTANT_MODEL", "claude-opus-5")
EFFORT = os.environ.get("ASSISTANT_EFFORT", "medium")
MAX_PAUSE_RESTARTS = 5

HERE = Path(__file__).parent
PROFILE_PATH = DATA_DIR / "profile.md"
PROFILE_TEMPLATE = HERE / "profile.example.md"

BASE_PROMPT = """\
あなたは、ここに書かれたプロフィールの持ち主ただ一人のための専属アシスタントです。
持ち主の好みや状況を踏まえ、秘書・相談相手・調べ物係として手助けしてください。

- 日本語で、簡潔かつ親しみやすく話してください。
- 会話の中で持ち主について長く役立つ事実 (好み、予定、目標、人間関係など) が分かったら、remember ツールで保存してください。一時的な話題は保存しないでください。
- 保存済みの記憶と食い違う情報が出たら、forget で古いものを消してから remember し直してください。
- やることは add_todo、あとで読み返したい文章は add_note に保存してください。
- 「今日」「来週」などの日付は get_current_time で確認してから解釈してください。
- 最新情報や事実確認が必要なときは web_search を使い、出典を添えてください。
- 分からないことは推測で埋めず、そう伝えてください。
"""


def ensure_profile() -> str:
    DATA_DIR.mkdir(exist_ok=True)
    if not PROFILE_PATH.exists():
        shutil.copy(PROFILE_TEMPLATE, PROFILE_PATH)
    return PROFILE_PATH.read_text(encoding="utf-8")


def build_system_prompt() -> str:
    # 会話中は変えない (プロンプトキャッシュを効かせるため)。新しい記憶は /reset で反映される。
    memories = load_memories()
    memory_text = "\n".join(f"- [{m['id']}] {m['fact']}" for m in memories) or "(まだありません)"
    return (
        f"{BASE_PROMPT}\n"
        f"<profile>\n{ensure_profile()}\n</profile>\n\n"
        f"<memories>\n{memory_text}\n</memories>"
    )


def run_turn(client: anthropic.Anthropic, system: str, messages: list) -> None:
    """1 回のユーザー発話に答える。messages はその場で更新される。"""
    for _ in range(MAX_PAUSE_RESTARTS + 1):
        runner = client.beta.messages.tool_runner(
            model=MODEL,
            max_tokens=16000,
            system=system,
            tools=ALL_TOOLS,
            messages=messages,
            thinking={"type": "adaptive"},
            output_config={"effort": EFFORT},
            cache_control={"type": "ephemeral"},
            betas=["server-side-fallback-2026-07-01"],
            fallbacks="default",
        )
        last = None
        for message in runner:
            last = message
            # ランナーは履歴を公開しないので、こちらでも同じ履歴を保持する
            messages.append({"role": "assistant", "content": message.content})
            if message.stop_reason == "refusal":
                print("\n(この依頼にはお答えできませんでした)")
                return
            for block in message.content:
                if block.type == "text":
                    print(block.text, end="", flush=True)
                elif block.type == "tool_use":
                    print(f"\n  ⚙ {block.name}", flush=True)
            tool_response = runner.generate_tool_call_response()
            if tool_response is not None:
                messages.append(tool_response)
        if last is None or last.stop_reason != "pause_turn":
            break
        # Web 検索が長引いて一時停止した。履歴の末尾が停止したターンなので、そのまま再開する
    print()


def main() -> None:
    client = anthropic.Anthropic()
    system = build_system_prompt()
    messages: list = []

    print("アシスタント起動。/reset で会話をリセット、/memory で記憶一覧、/exit で終了。")
    while True:
        try:
            user_input = input("\nあなた> ").strip()
        except (EOFError, KeyboardInterrupt):
            print()
            break
        if not user_input:
            continue
        if user_input in ("/exit", "/quit"):
            break
        if user_input == "/reset":
            system = build_system_prompt()
            messages = []
            print("会話をリセットしました (プロフィールと記憶を読み直しました)")
            continue
        if user_input == "/memory":
            memories = load_memories()
            print("\n".join(f"[{m['id']}] {m['fact']}" for m in memories) or "記憶はまだありません")
            continue

        checkpoint = len(messages)
        messages.append({"role": "user", "content": user_input})
        print("\nアシスタント> ", end="", flush=True)
        try:
            run_turn(client, system, messages)
        except anthropic.AuthenticationError:
            sys.exit("API キーが無効です。ANTHROPIC_API_KEY を確認してください。")
        except anthropic.RateLimitError:
            print("\n混み合っています。少し待ってからもう一度どうぞ。")
            del messages[checkpoint:]
        except anthropic.APIStatusError as e:
            print(f"\nAPI エラー ({e.status_code}): {e.message}")
            del messages[checkpoint:]
        except anthropic.APIConnectionError:
            print("\nAPI に接続できませんでした。ネットワークを確認してください。")
            del messages[checkpoint:]


if __name__ == "__main__":
    main()
