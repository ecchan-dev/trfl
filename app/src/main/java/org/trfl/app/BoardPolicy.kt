package org.trfl.app

/** Fail closed: new boards require review before becoming available in the Play build.
 * Based on the official boards.json ws_board flags, checked 2026-10-30.
 * Board classification is not a guarantee about individual user-generated posts.
 */
object BoardPolicy {
    private val safeBoards = setOf(
        "3", "a", "adv", "an", "biz", "c", "cgl", "ck", "cm", "co", "diy", "fa",
        "fit", "g", "gd", "his", "int", "jp", "k", "lgbt", "lit", "m", "mlp", "mu",
        "n", "news", "o", "out", "p", "po", "pw", "qst", "sci", "sp", "tg", "toy",
        "trv", "tv", "v", "vg", "vip", "vm", "vmg", "vp", "vr", "vrpg", "vst", "vt",
        "w", "wsg", "wsr", "x", "xs"
    )

    fun allows(board: String, restricted: Boolean = BuildConfig.SFW_ONLY): Boolean =
        !restricted || board in safeBoards

    fun allowsPath(path: String, restricted: Boolean = BuildConfig.SFW_ONLY): Boolean =
        path == "boards.json" || allows(path.substringBefore('/'), restricted)
}
