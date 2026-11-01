package org.trfl.app

import java.security.MessageDigest

/** Local contract only. No remote field names, CAPTCHA tokens or endpoints are assumed. */
data class PostingTarget(val board: String, val threadId: Long? = null)
data class PostingAttachment(val filename: String, val mediaType: String, val bytes: ByteArray)
data class PostingRequest(
    val target: PostingTarget,
    val comment: String,
    val subject: String = "",
    val name: String = "",
    val options: String = "",
    val attachment: PostingAttachment? = null
)

/** Must come from reviewed board metadata; defaults never authorize a board. */
data class PostingBoardRules(
    val maxCommentChars: Int,
    val maxAttachmentBytes: Int,
    val allowedMediaTypes: Set<String>,
    val newThreadRequiresAttachment: Boolean = true,
    val postingEnabled: Boolean = true
)

class PostingPolicy(
    private val rules: Map<String, PostingBoardRules> = emptyMap(),
    private val restricted: Boolean = BuildConfig.SFW_ONLY
) {
    fun validate(request: PostingRequest): List<String> = buildList {
        val target = request.target
        if (!target.board.matches(Regex("[a-z0-9]{1,8}"))) add("Invalid board")
        if (!BoardPolicy.allows(target.board, restricted || BuildConfig.SFW_ONLY)) add("Board unavailable in this build")
        if (target.threadId != null && target.threadId <= 0) add("Invalid thread")
        val rule = rules[target.board]
        if (rule == null || !rule.postingEnabled) { add("Board posting rules unverified"); return@buildList }
        if (rule.maxCommentChars <= 0 || rule.maxAttachmentBytes < 0) add("Invalid board rules")
        if (request.comment.length > rule.maxCommentChars) add("Comment too long")
        if (request.subject.length > 256 || request.name.length > 256 || request.options.length > 256) add("Field too long")
        if (listOf(request.comment, request.subject, request.name, request.options).any { '\u0000' in it }) add("Invalid text")
        if (request.comment.isBlank() && request.attachment == null) add("Post is empty")
        if (target.threadId == null && rule.newThreadRequiresAttachment && request.attachment == null) add("New thread requires attachment")
        request.attachment?.let {
            if (it.bytes.isEmpty() || it.bytes.size > rule.maxAttachmentBytes) add("Invalid attachment size")
            if (it.mediaType !in rule.allowedMediaTypes) add("Unsupported attachment type")
            if (it.filename.isBlank() || it.filename.any { c -> c < ' ' || c in "\"\\/" }) add("Invalid attachment filename")
        }
    }
}

sealed interface PostingResult {
    data class ConfirmedSuccess(val postId: Long) : PostingResult
    data class Rejected(val reason: String) : PostingResult
    data class Cooldown(val retryAtMillis: Long) : PostingResult
    data class Ambiguous(val reason: String = "Outcome unknown; inspect the official thread before taking further action") : PostingResult
    data class Unsupported(val reason: String) : PostingResult
    data class Invalid(val errors: List<String>) : PostingResult
    data object Duplicate : PostingResult
    data object InFlight : PostingResult
}

fun interface PostingTransport {
    /** Exactly one attempt. Never retry internally. Success requires a verified server receipt. */
    fun submit(request: PostingRequest): PostingResult
}

/** The official user-solved CAPTCHA and submission contract are not locally documented.
 * A UI may open the official board separately, but cannot infer submission success from a handoff.
 */
class GatedPostingTransport : PostingTransport {
    override fun submit(request: PostingRequest): PostingResult = PostingResult.Unsupported(
        "Direct posting unavailable: official user-solved CAPTCHA and submission protocol require verification. Use the official board manually."
    )
}

/** Own one instance for the app lifetime. No scheduling or automatic retries.
 * Persistence across process death must be supplied by the caller before enabling a real adapter.
 * The caller must retain its draft unless ConfirmedSuccess matches the captured revision.
 */
class PostingSender(
    private val policy: PostingPolicy,
    private val transport: PostingTransport = GatedPostingTransport(),
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    private val lock = Any()
    private var sending = false
    private val blocked = mutableSetOf<String>()
    private val cooldowns = mutableMapOf<String, Long>()

    /** Call only from an explicit user Send action, never from composition, recovery or timers. */
    fun sendExplicit(request: PostingRequest): PostingResult {
        // Freeze mutable attachment bytes before validation and dispatch.
        val snapshot = request.copy(attachment = request.attachment?.let { it.copy(bytes = it.bytes.copyOf()) })
        val errors = policy.validate(snapshot)
        if (errors.isNotEmpty()) return PostingResult.Invalid(errors)
        val fingerprint = fingerprint(snapshot)
        synchronized(lock) {
            if (sending) return PostingResult.InFlight
            if (fingerprint in blocked) return PostingResult.Duplicate
            cooldowns[snapshot.target.board]?.let { if (it > nowMillis()) return PostingResult.Cooldown(it) }
            sending = true
            // Reserve before crossing the transport boundary; exceptions are ambiguous, not retryable failures.
            blocked.add(fingerprint)
        }
        val result = try {
            when (val receipt = transport.submit(snapshot)) {
                is PostingResult.ConfirmedSuccess -> if (receipt.postId > 0) receipt else PostingResult.Ambiguous("Invalid server receipt")
                else -> receipt
            }
        } catch (_: Exception) {
            PostingResult.Ambiguous()
        }
        synchronized(lock) {
            sending = false
            if (result !is PostingResult.ConfirmedSuccess && result !is PostingResult.Ambiguous) blocked.remove(fingerprint)
            if (result is PostingResult.Cooldown) cooldowns[snapshot.target.board] = result.retryAtMillis
        }
        return result
    }

    private fun fingerprint(request: PostingRequest): String {
        val digest = MessageDigest.getInstance("SHA-256")
        fun field(value: ByteArray) {
            digest.update(java.nio.ByteBuffer.allocate(4).putInt(value.size).array())
            digest.update(value)
        }
        listOf(request.target.board, request.target.threadId?.toString().orEmpty(), request.comment,
            request.subject, request.name, request.options, request.attachment?.filename.orEmpty(),
            request.attachment?.mediaType.orEmpty()).forEach { field(it.toByteArray(Charsets.UTF_8)) }
        field(request.attachment?.bytes ?: byteArrayOf())
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
