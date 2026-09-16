package eu.kanade.tachiyomi.animeextension.ru.lordfilm

import kotlinx.serialization.Serializable

@Serializable
class Season(
    val season: Int = 0,
    val blocked: Boolean = false,
    val episodes: List<EpisodeDto> = emptyList(),
)

@Serializable
class EpisodeDto(
    val episode: String = "",
    val hls: String = "",
    val title: String = "",
    val duration: Int = 0,
)
