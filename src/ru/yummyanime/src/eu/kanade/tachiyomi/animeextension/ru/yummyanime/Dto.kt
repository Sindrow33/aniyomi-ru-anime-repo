package eu.kanade.tachiyomi.animeextension.ru.yummyanime

import eu.kanade.tachiyomi.animesource.model.SAnime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive

@Serializable
class YummyResponse<T>(
    val response: T? = null,
)

@Serializable
class YummyAnimeDto(
    private val title: String? = null,
    @SerialName("anime_url") private val animeUrl: String? = null,
    private val poster: YummyPosterDto? = null,
    private val genres: List<YummyNamedDto>? = null,
    private val year: Int? = null,
    @SerialName("anime_status") private val status: YummyStatusDto? = null,
    private val type: YummyNamedDto? = null,
) {
    fun toSAnime() = SAnime.create().apply {
        title = this@YummyAnimeDto.title ?: ""
        url = animeUrl ?: ""
        thumbnail_url = poster?.bestUrl()
        // Жанры и тип приходят уже в каталоге — заполняем сразу, чтобы теги
        // были видны до открытия карточки.
        genre = listOfNotNull(
            type?.title ?: type?.name,
            year?.toString(),
        ).plus(genres.orEmpty().mapNotNull { it.title })
            .filter { it.isNotBlank() }
            .joinToString(", ")
        status = this@YummyAnimeDto.status?.toSAnimeStatus() ?: SAnime.UNKNOWN
    }
}

@Serializable
class YummyPosterDto(
    val big: String? = null,
    val huge: String? = null,
    val mega: String? = null,
    val fullsize: String? = null,
) {
    /** Prefer the largest variant; the API repeats the same file when it has no bigger one. */
    fun bestUrl(): String? = (fullsize ?: mega ?: huge ?: big)
        ?.let { if (it.startsWith("//")) "https:$it" else it }
}

@Serializable
class YummyDetailsDto(
    val title: String? = null,
    val description: String? = null,
    val genres: List<YummyNamedDto>? = null,
    @SerialName("anime_status") val status: YummyStatusDto? = null,
    val studios: List<YummyNamedDto>? = null,
    val poster: YummyPosterDto? = null,
    val type: YummyNamedDto? = null,
    val videos: List<YummyVideoDto>? = null,
    val year: Int? = null,
    val duration: Int? = null,
    val episodes: YummyEpisodesDto? = null,
    val rating: YummyRatingDto? = null,
    @SerialName("min_age") val minAge: YummyNamedDto? = null,
    @SerialName("other_titles") val otherTitles: List<String>? = null,
    val creators: List<YummyNamedDto>? = null,
)

@Serializable
class YummyEpisodesDto(
    val count: Int? = null,
    val aired: Int? = null,
)

@Serializable
class YummyRatingDto(
    val average: Double? = null,
    val counters: Int? = null,
)

@Serializable
class YummyNamedDto(
    val title: String? = null,
    val alias: String? = null,
    /** У типа тайтла человекочитаемое значение лежит в `name`, а не в `title`. */
    val name: String? = null,
    @SerialName("title_long") val titleLong: String? = null,
) {
    fun titleLongOrTitle(): String? = (titleLong ?: title)?.takeIf { it.isNotBlank() }
}

@Serializable
class YummyStatusDto(
    val value: JsonPrimitive? = null,
    val alias: String? = null,
) {
    /** 0/"released" — вышел, 1/"ongoing" — онгоинг. */
    fun toSAnimeStatus(): Int = when {
        alias == "ongoing" || value?.content == "1" -> SAnime.ONGOING
        alias == "released" || value?.content == "0" -> SAnime.COMPLETED
        else -> SAnime.UNKNOWN
    }
}

@Serializable
class YummyVideoDto(
    val number: JsonPrimitive? = null,
    val data: YummyVideoDataDto? = null,
    @SerialName("iframe_url") val iframeUrl: String? = null,
)

@Serializable
class YummyVideoDataDto(
    val dubbing: String? = null,
    val player: String? = null,
)

@Serializable
class KodikFormData(
    val d: String = "",
    @SerialName("d_sign") val dSign: String = "",
    val pd: String = "",
    @SerialName("pd_sign") val pdSign: String = "",
    val ref: String = "",
    @SerialName("ref_sign") val refSign: String = "",
)

@Serializable
class KodikVideoInfo(val src: String)

@Serializable
class KodikVideoQuality(
    @SerialName("360") val ugly: List<KodikVideoInfo> = emptyList(),
    @SerialName("480") val bad: List<KodikVideoInfo> = emptyList(),
    @SerialName("720") val good: List<KodikVideoInfo> = emptyList(),
)

@Serializable
class KodikData(val links: KodikVideoQuality)
