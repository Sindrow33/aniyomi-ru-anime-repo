package eu.kanade.tachiyomi.animeextension.ru.kinogo

import kotlinx.serialization.Serializable

@Serializable
class PlaylistResponse(
    val file: String = "",
    val duration: Int = 0,
    val subtitle: String = "",
    val success: Boolean = true,
    val error: String = "",
)
