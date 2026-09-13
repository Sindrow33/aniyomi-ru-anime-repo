package eu.kanade.tachiyomi.animeextension.ru.anime365

import eu.kanade.tachiyomi.animesource.model.SAnime
import kotlinx.serialization.Serializable

@Serializable
class ListResponse<T>(
    val data: List<T> = emptyList(),
)

@Serializable
class ObjectResponse<T>(
    val data: T? = null,
)

@Serializable
class AccessTokenDto(
    val access_token: String? = null,
)

@Serializable
class ErrorEnvelope(
    val error: ErrorDto? = null,
)

@Serializable
class ErrorDto(
    val code: Int? = null,
    val message: String? = null,
)

@Serializable
class TitlesDto(
    val ru: String? = null,
    val romaji: String? = null,
    val en: String? = null,
    val ja: String? = null,
)

@Serializable
class GenreDto(
    val id: Int? = null,
    val title: String? = null,
)

@Serializable
class DescriptionDto(
    val source: String? = null,
    val value: String? = null,
)

@Serializable
class SeriesDto(
    val id: Int = 0,
    val titles: TitlesDto? = null,
    val title: String? = null,
    val posterUrl: String? = null,
    val posterUrlSmall: String? = null,
    val year: Int? = null,
    val season: String? = null,
    val type: String? = null,
    val typeTitle: String? = null,
    val numberOfEpisodes: Int? = null,
    val isAiring: Int? = null,
    val isActive: Int? = null,
    val isHentai: Int? = null,
    val myAnimeListScore: Double? = null,
    val genres: List<GenreDto> = emptyList(),
    val descriptions: List<DescriptionDto> = emptyList(),
    val episodes: List<EpisodeDto> = emptyList(),
) {
    private fun displayTitle(): String = titles?.ru
        ?: titles?.romaji
        ?: titles?.en
        ?: title?.substringBefore(" / ")
        ?: id.toString()

    fun toSAnime() = SAnime.create().apply {
        url = id.toString()
        title = displayTitle()
        thumbnail_url = posterUrlSmall ?: posterUrl
    }

    fun toSAnimeDetails() = toSAnime().apply {
        genre = genres.mapNotNull { it.title }.joinToString()
        status = when {
            isAiring == 1 -> SAnime.ONGOING
            numberOfEpisodes != null && numberOfEpisodes > 0 -> SAnime.COMPLETED
            else -> SAnime.UNKNOWN
        }
        description = buildString {
            descriptions.firstOrNull { !it.value.isNullOrBlank() }?.value?.let {
                appendLine(it.trim())
                appendLine()
            }
            titles?.romaji?.let { appendLine("Ромадзи: $it") }
            titles?.en?.let { appendLine("Английское название: $it") }
            typeTitle?.let { appendLine("Тип: $it") }
            year?.let { appendLine("Год: $it") }
            season?.let { appendLine("Сезон: $it") }
            numberOfEpisodes?.takeIf { it > 0 }?.let { appendLine("Всего эпизодов: $it") }
            myAnimeListScore?.takeIf { it > 0 }?.let { appendLine("Оценка MyAnimeList: $it") }
        }.trim()
    }
}

@Serializable
class EpisodeDto(
    val id: Int = 0,
    val seriesId: Int? = null,
    val episodeFull: String? = null,
    val episodeInt: Float? = null,
    val episodeTitle: String? = null,
    val episodeType: String? = null,
    val firstUploadedDateTime: String? = null,
    val isActive: Int? = null,
    val translations: List<TranslationDto> = emptyList(),
)

@Serializable
class TranslationDto(
    val id: Int = 0,
    val authorsSummary: String? = null,
    val authorsList: List<String> = emptyList(),
    val type: String? = null,
    val typeKind: String? = null,
    val typeLang: String? = null,
    val qualityType: String? = null,
    val priority: Long? = null,
    val isActive: Int? = null,
    val height: Int? = null,
    val width: Int? = null,
) {
    val author: String
        get() = authorsSummary?.takeIf { it.isNotBlank() }
            ?: authorsList.joinToString().takeIf { it.isNotBlank() }
            ?: "Неизвестно"

    val kindLabel: String
        get() = when (typeKind) {
            "voice" -> "озвучка"
            "sub" -> "субтитры"
            "raw" -> "оригинал"
            else -> typeKind.orEmpty()
        }
}

@Serializable
class EmbedDto(
    val embedUrl: String? = null,
    val subtitlesUrl: String? = null,
    val subtitlesVttUrl: String? = null,
    val stream: List<StreamDto> = emptyList(),
    val download: List<DownloadDto> = emptyList(),
)

@Serializable
class StreamDto(
    val height: Int? = null,
    val urls: List<String> = emptyList(),
)

@Serializable
class DownloadDto(
    val height: Int? = null,
    val url: String? = null,
)
