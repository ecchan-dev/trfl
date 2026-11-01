package org.trfl.app

// Deliberately narrow HTML subset: never execute markup or fetch remote prompt images.
internal data class CaptchaPrompt(val text: String?, val image: ByteArray?)

internal fun parseCaptchaPrompt(raw: String, decodeImage: (String) -> ByteArray): CaptchaPrompt {
    require(raw.length <= 300_000)
    val text = StringBuilder()
    var image: ByteArray? = null
    val stack = mutableListOf<Pair<String, Boolean>>()
    val tags = Regex("<[^>]*>|[^<]+")
    var consumed = 0
    for (match in tags.findAll(raw)) {
        require(match.range.first == consumed)
        consumed = match.range.last + 1
        val part = match.value
        if (!part.startsWith('<')) {
            if (stack.none { it.second }) text.append(decodePromptEntities(part))
            continue
        }
        val closing = Regex("</\\s*(b|strong|span|div)\\s*>", RegexOption.IGNORE_CASE).matchEntire(part)
        if (closing != null) {
            require(stack.isNotEmpty() && stack.last().first.equals(closing.groupValues[1], true))
            stack.removeAt(stack.lastIndex)
            continue
        }
        if (part.matches(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE))) {
            if (stack.none { it.second }) text.append('\n')
            continue
        }
        val tag = Regex("<(b|strong|span|div)(?:\\s+style=\"([^\"]*)\")?\\s*>", RegexOption.IGNORE_CASE).matchEntire(part)
        if (tag != null) {
            var hidden = false
            for (declaration in tag.groupValues[2].split(';').filter { it.isNotBlank() }) {
                val pair = declaration.split(':', limit = 2); require(pair.size == 2)
                val key = pair[0].trim().lowercase(); val value = pair[1].trim().lowercase()
                when (key) {
                    "display" -> { require(value.matches(Regex("[a-z-]+"))); if (value == "none") hidden = true }
                    "opacity" -> { val n = value.toDoubleOrNull(); require(n != null && n in 0.0..1.0); if (n == 0.0) hidden = true }
                    "width" -> { val n = value.removeSuffix("px").toIntOrNull(); require(value.endsWith("px") && n != null && n in 0..1024); if (n < 3) hidden = true }
                    "float" -> require(value in listOf("left", "right", "none"))
                    "margin" -> require(value.matches(Regex("[0-9px .-]+")))
                    else -> error("Unsupported prompt style")
                }
            }
            require(stack.size < 32)
            stack.add(tag.groupValues[1] to hidden)
            continue
        }
        val img = Regex("<img\\s+src=\"data:image/(?:png|gif|jpeg|webp);base64,([A-Za-z0-9+/=\\r\\n]+)\"(?:\\s+style=\"(?:float:(?:left|right);?|margin:[0-9px .-]+;?)*\")?\\s*/?>", RegexOption.IGNORE_CASE).matchEntire(part)
        require(img != null && image == null && stack.none { it.second })
        image = decodeImage(img.groupValues[1])
    }
    require(consumed == raw.length && stack.isEmpty())
    val result = text.toString().trim().takeIf { it.isNotBlank() }
    require(result == null || result.length <= 500)
    return CaptchaPrompt(result, image)
}

private fun decodePromptEntities(value: String): String = Regex("&([^;]{1,12});").replace(value) { match ->
    val entity = match.groupValues[1]
    when (entity) {
        "amp" -> "&"; "lt" -> "<"; "gt" -> ">"; "quot" -> "\""; "apos", "#39" -> "'"; "nbsp" -> " "
        else -> {
            val code = if (entity.startsWith("#x", true)) entity.drop(2).toIntOrNull(16) else if (entity.startsWith('#')) entity.drop(1).toIntOrNull() else null
            require(code != null && code in 32..0x10ffff && code !in 0xd800..0xdfff)
            String(Character.toChars(code))
        }
    }
}.also { require(!it.contains('&') || !Regex("&(?:#|[A-Za-z])[^;\\s]*$").containsMatchIn(it)) }
