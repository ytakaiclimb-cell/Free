package com.pop.insta.core

/** The three sizes Instagram actually posts at, all 1080 px wide. */
enum class PostFormat(
    val ratio: String,
    val label: String,
    val width: Int,
    val height: Int,
) {
    SQUARE("1:1", "正方形", 1080, 1080),
    PORTRAIT("4:5", "縦長", 1080, 1350),
    STORY("9:16", "ストーリー", 1080, 1920);

    /** Width over height, the way Compose's aspectRatio wants it. */
    val aspect: Float get() = width.toFloat() / height.toFloat()

    val pixels: String get() = "${width}×${height}"
}

/** What to do with A4 paper that does not match the frame. */
enum class FitMode(val label: String, val hint: String) {
    CONTAIN("全体を入れる", "文字が切れない"),
    COVER("切り抜く", "枠いっぱいに"),
}

/** What fills the space the POP does not cover. */
enum class Backdrop(val label: String) {
    PAPER("紙色"),
    WHITE("白"),
    CREAM("生成り"),
    INK("黒"),
    BLUR("ぼかし"),
}
