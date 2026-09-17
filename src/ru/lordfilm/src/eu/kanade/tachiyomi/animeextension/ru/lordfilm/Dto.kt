package eu.kanade.tachiyomi.animeextension.ru.lordfilm

import kotlinx.serialization.Serializable

/** One playable episode as the balancer's catalogue API reports it. */
@Serializable
class EpisodeDto(
    val id: Long = 0,
    val order: Int = 1,
    val season: SeasonDto? = null,
    val episodeVariants: List<EpisodeVariantDto> = emptyList(),
)

@Serializable
class SeasonDto(
    val id: Long = 0,
    val order: Int = 1,
)

/** `filepath` is a signed directory; appending `master.m3u8` yields the playlist. */
@Serializable
class EpisodeVariantDto(
    val filepath: String? = null,
    val duration: Int = 0,
    /** Dub/voice-over label, e.g. "MOST UZ"; "Default" means the original track. */
    val title: String? = null,
)
