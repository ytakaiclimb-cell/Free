"""アシスタントが使うツール群。データはすべて data/ 以下の JSON に保存する。"""

import json
from datetime import datetime
from pathlib import Path
from zoneinfo import ZoneInfo

from anthropic import beta_tool

DATA_DIR = Path(__file__).parent / "data"
TIMEZONE = ZoneInfo("Asia/Tokyo")


def _load(name: str) -> list[dict]:
    path = DATA_DIR / f"{name}.json"
    if not path.exists():
        return []
    return json.loads(path.read_text(encoding="utf-8"))


def _save(name: str, items: list[dict]) -> None:
    DATA_DIR.mkdir(exist_ok=True)
    path = DATA_DIR / f"{name}.json"
    path.write_text(json.dumps(items, ensure_ascii=False, indent=2), encoding="utf-8")


def _next_id(items: list[dict]) -> int:
    return max((item["id"] for item in items), default=0) + 1


def _now() -> str:
    return datetime.now(TIMEZONE).strftime("%Y-%m-%d %H:%M")


def load_memories() -> list[dict]:
    return _load("memories")


# ---- 記憶 ----

@beta_tool
def remember(fact: str) -> str:
    """ユーザーについて長期的に覚えておくべき事実を保存する。好み、予定、人間関係、目標など。

    Args:
        fact: 覚えておく内容を一文で。例: 「コーヒーはブラック派」
    """
    memories = _load("memories")
    item = {"id": _next_id(memories), "fact": fact, "saved_at": _now()}
    memories.append(item)
    _save("memories", memories)
    return f"記憶しました (id={item['id']})"


@beta_tool
def forget(memory_id: int) -> str:
    """保存済みの記憶を削除する。内容が古くなった・間違っていたときに使う。

    Args:
        memory_id: 削除する記憶の id。
    """
    memories = _load("memories")
    kept = [m for m in memories if m["id"] != memory_id]
    if len(kept) == len(memories):
        return f"id={memory_id} の記憶は見つかりません"
    _save("memories", kept)
    return f"id={memory_id} の記憶を削除しました"


@beta_tool
def list_memories() -> str:
    """保存済みの記憶をすべて一覧する。"""
    memories = _load("memories")
    if not memories:
        return "記憶はまだありません"
    return "\n".join(f"[{m['id']}] {m['fact']} ({m['saved_at']})" for m in memories)


# ---- メモ ----

@beta_tool
def add_note(title: str, body: str) -> str:
    """メモを保存する。アイデア、議事録、調べた結果など、あとで読み返す文章に使う。

    Args:
        title: メモのタイトル。
        body: メモの本文。
    """
    notes = _load("notes")
    item = {"id": _next_id(notes), "title": title, "body": body, "created_at": _now()}
    notes.append(item)
    _save("notes", notes)
    return f"メモを保存しました (id={item['id']})"


@beta_tool
def search_notes(keyword: str = "") -> str:
    """メモをキーワードで検索する。空文字なら全件のタイトルを返す。

    Args:
        keyword: タイトルか本文に含まれる語。
    """
    notes = _load("notes")
    hits = [n for n in notes if keyword in n["title"] or keyword in n["body"]]
    if not hits:
        return "該当するメモはありません"
    if not keyword:
        return "\n".join(f"[{n['id']}] {n['title']} ({n['created_at']})" for n in hits)
    return "\n\n".join(
        f"[{n['id']}] {n['title']} ({n['created_at']})\n{n['body']}" for n in hits
    )


# ---- ToDo ----

@beta_tool
def add_todo(task: str, due: str = "") -> str:
    """ToDo を追加する。

    Args:
        task: やること。
        due: 期限 (YYYY-MM-DD)。なければ空文字。
    """
    todos = _load("todos")
    item = {"id": _next_id(todos), "task": task, "due": due, "done": False, "created_at": _now()}
    todos.append(item)
    _save("todos", todos)
    return f"ToDo を追加しました (id={item['id']})"


@beta_tool
def list_todos(include_done: bool = False) -> str:
    """ToDo を一覧する。期限の近い順に並べる。

    Args:
        include_done: 完了済みも含めるなら true。
    """
    todos = [t for t in _load("todos") if include_done or not t["done"]]
    if not todos:
        return "ToDo はありません"
    todos.sort(key=lambda t: t["due"] or "9999-99-99")
    return "\n".join(
        f"[{t['id']}] {'✔' if t['done'] else '□'} {t['task']}"
        + (f" (期限 {t['due']})" if t["due"] else "")
        for t in todos
    )


@beta_tool
def complete_todo(todo_id: int) -> str:
    """ToDo を完了にする。

    Args:
        todo_id: 完了にする ToDo の id。
    """
    todos = _load("todos")
    for t in todos:
        if t["id"] == todo_id:
            t["done"] = True
            _save("todos", todos)
            return f"「{t['task']}」を完了にしました"
    return f"id={todo_id} の ToDo は見つかりません"


# ---- その他 ----

@beta_tool
def get_current_time() -> str:
    """現在の日本時間の日時と曜日を返す。「今日」「明日」などを解釈するときに使う。"""
    now = datetime.now(TIMEZONE)
    weekday = "月火水木金土日"[now.weekday()]
    return now.strftime(f"%Y-%m-%d ({weekday}) %H:%M")


WEB_SEARCH = {"type": "web_search_20260209", "name": "web_search", "max_uses": 5}

ALL_TOOLS = [
    remember,
    forget,
    list_memories,
    add_note,
    search_notes,
    add_todo,
    list_todos,
    complete_todo,
    get_current_time,
    WEB_SEARCH,
]
