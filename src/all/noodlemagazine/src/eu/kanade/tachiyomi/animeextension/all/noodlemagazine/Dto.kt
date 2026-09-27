package eu.kanade.tachiyomi.animeextension.all.noodlemagazine

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `window.playlist` со страницы просмотра — плеер JW получает из него
 * готовые прямые ссылки на mp4 по качествам.
 */
@Serializable
class PlaylistDto(
    val image: String? = null,
    val sources: List<SourceDto> = emptyList(),
    val tracks: List<TrackDto> = emptyList(),
)

@Serializable
class SourceDto(
    val file: String = "",
    /** Высота кадра числом: "720", "480", … */
    val label: String = "",
    val type: String? = null,
)

/**
 * Разметка schema.org VideoObject со страницы просмотра — единственный
 * надёжный источник длительности и даты: блок `.m_time` на странице видео
 * относится к плиткам «похожего», а не к самому ролику.
 */
@Serializable
class VideoObjectDto(
    val name: String? = null,
    val description: String? = null,
    val duration: String? = null,
    val uploadDate: String? = null,
    val thumbnailUrl: String? = null,
    val keywords: String? = null,
)

@Serializable
class TrackDto(
    val file: String = "",
    val kind: String? = null,
    @SerialName("label") val label: String? = null,
)
