package eu.kanade.tachiyomi.animeextension.ru.anilibria

import eu.kanade.tachiyomi.animesource.model.SAnime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class PaginatedDto<T>(
    val data: List<T> = emptyList(),
    val meta: MetaDto? = null,
)

@Serializable
class MetaDto(
    val pagination: PaginationDto? = null,
)

@Serializable
class PaginationDto(
    @SerialName("current_page") val currentPage: Int? = null,
    @SerialName("total_pages") val totalPages: Int? = null,
)

@Serializable
class ValueDto(
    val value: String? = null,
    val description: String? = null,
    val label: String? = null,
)

@Serializable
class NameDto(
    val main: String? = null,
    val english: String? = null,
)

@Serializable
class OptimizedDto(
    val src: String? = null,
    val preview: String? = null,
    val thumbnail: String? = null,
)

@Serializable
class PosterDto(
    val src: String? = null,
    val preview: String? = null,
    val thumbnail: String? = null,
    val optimized: OptimizedDto? = null,
) {
    fun bestUrl(): String? = optimized?.src ?: src ?: preview ?: thumbnail
}

@Serializable
class GenreDto(
    val id: Int? = null,
    val name: String? = null,
)

@Serializable
class MemberDto(
    val nickname: String? = null,
    val role: ValueDto? = null,
)

@Serializable
class ReleaseDto(
    val id: Int,
    val name: NameDto? = null,
    val alias: String? = null,
    val year: Int? = null,
    val type: ValueDto? = null,
    val season: ValueDto? = null,
    val poster: PosterDto? = null,
    val description: String? = null,
    val genres: List<GenreDto>? = null,
    val members: List<MemberDto>? = null,
    @SerialName("is_ongoing") val isOngoing: Boolean? = null,
    @SerialName("is_in_production") val isInProduction: Boolean? = null,
    @SerialName("age_rating") val ageRating: ValueDto? = null,
    @SerialName("episodes_total") val episodesTotal: Int? = null,
    val episodes: List<EpisodeDto>? = null,
) {
    fun toSAnime(baseImageUrl: String) = SAnime.create().apply {
        url = id.toString()
        title = name?.main ?: name?.english ?: alias ?: id.toString()
        thumbnail_url = poster?.bestUrl()?.let { if (it.startsWith("http")) it else baseImageUrl + it }
    }

    fun toSAnimeDetails(baseImageUrl: String) = toSAnime(baseImageUrl).apply {
        description = buildString {
            this@ReleaseDto.description?.let { appendLine(it.trim()); appendLine() }
            name?.english?.let { appendLine("Английское название: $it") }
            type?.description?.let { appendLine("Тип: $it") }
            year?.let { appendLine("Год: $it") }
            season?.description?.let { appendLine("Сезон: $it") }
            episodesTotal?.let { appendLine("Всего эпизодов: $it") }
            ageRating?.label?.let { appendLine("Возрастной рейтинг: $it") }
        }.trim()
        genre = genres?.mapNotNull { it.name }?.joinToString()
        author = members?.filter { it.role?.value == "voicing" }
            ?.mapNotNull { it.nickname }
            ?.joinToString()
            ?.takeIf { it.isNotBlank() }
        status = when {
            isOngoing == true -> SAnime.ONGOING
            isInProduction == true -> SAnime.ONGOING
            else -> SAnime.COMPLETED
        }
    }
}

@Serializable
class EpisodeDto(
    val id: String,
    val name: String? = null,
    val ordinal: Float? = null,
    val duration: Int? = null,
    @SerialName("name_english") val nameEnglish: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("hls_480") val hls480: String? = null,
    @SerialName("hls_720") val hls720: String? = null,
    @SerialName("hls_1080") val hls1080: String? = null,
) {
    fun hlsList(): List<Pair<String, String>> = listOfNotNull(
        hls1080?.let { "1080p" to it },
        hls720?.let { "720p" to it },
        hls480?.let { "480p" to it },
    )
}
