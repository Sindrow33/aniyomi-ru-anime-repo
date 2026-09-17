package eu.kanade.tachiyomi.animeextension.ru.lordfilm

import okhttp3.Headers

/** Short-lived balancer credentials: they expire together with the iframe page. */
class PlayerSession(
    val contentId: String,
    val headers: Headers,
)
