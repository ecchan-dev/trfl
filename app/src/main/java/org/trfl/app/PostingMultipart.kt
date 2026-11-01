package org.trfl.app

import java.io.ByteArrayOutputStream

/** Generic RFC multipart framing, deliberately not a mapping to an unverified posting protocol. */
data class PostingMultipartPart(val name: String, val bytes: ByteArray, val filename: String? = null, val mediaType: String? = null)
data class PostingMultipartBody(val contentType: String, val bytes: ByteArray)

object PostingMultipart {
    fun encode(parts: List<PostingMultipartPart>, boundary: String): PostingMultipartBody {
        require(boundary.matches(Regex("[A-Za-z0-9_-]{1,70}"))) { "Invalid boundary" }
        require(parts.size <= 32) { "Too many parts" }
        val marker = "--$boundary".toByteArray(Charsets.US_ASCII)
        val out = ByteArrayOutputStream()
        fun text(value: String) { out.write(value.toByteArray(Charsets.UTF_8)) }
        for (part in parts) {
            require(part.name.matches(Regex("[A-Za-z0-9_-]{1,80}"))) { "Invalid field name" }
            part.filename?.let { name ->
                require(name.isNotBlank() && name.length <= 255 && name.all { it in ' '..'~' && it !in "\"\\/" }) { "Invalid filename" }
            }
            part.mediaType?.let { require(it.matches(Regex("[A-Za-z0-9!#$&^_.+-]+/[A-Za-z0-9!#$&^_.+-]+"))) { "Invalid media type" } }
            require(!contains(part.bytes, marker)) { "Boundary collides with payload" }
            text("--$boundary\r\nContent-Disposition: form-data; name=\"${part.name}\"")
            part.filename?.let { text("; filename=\"$it\"") }
            text("\r\n")
            part.mediaType?.let { text("Content-Type: $it\r\n") }
            text("\r\n"); out.write(part.bytes); text("\r\n")
        }
        text("--$boundary--\r\n")
        return PostingMultipartBody("multipart/form-data; boundary=$boundary", out.toByteArray())
    }

    private fun contains(bytes: ByteArray, marker: ByteArray): Boolean =
        bytes.size >= marker.size && (0..bytes.size - marker.size).any { start -> marker.indices.all { bytes[start + it] == marker[it] } }
}
