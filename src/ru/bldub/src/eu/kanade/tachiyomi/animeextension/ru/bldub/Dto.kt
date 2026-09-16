package eu.kanade.tachiyomi.animeextension.ru.bldub

import kotlinx.serialization.Serializable

@Serializable
class TitlesResponse(
    val total: Int = 0,
    val page: Int = 0,
    val perpage: Int = 20,
    val result: List<TitleItem> = emptyList(),
)

@Serializable
class TitleItem(
    val id: Int = 0,
    val data: TitleData = TitleData(),
)

@Serializable
class TitleData(
    val name: String = "",
    val aka: String? = null,
    val image: String? = null,
    val year: Int? = null,
    val country: String? = null,
    val genre: String? = null,
    val type: String? = null,
    val status: String? = null,
)

@Serializable
class TitleDetail(
    val id: Int = 0,
    val title: String = "",
    val altTitle: String? = null,
    val poster: String? = null,
    val rating: Double? = null,
    val ratingType: String? = null,
    val access: String? = null,
    val translations: List<String> = emptyList(),
    val status: String? = null,
    val episodes: String? = null,
    val totalEpisodes: Int? = null,
    val type: String? = null,
    val country: String? = null,
    val year: Int? = null,
    val genres: List<String> = emptyList(),
    val couple: String? = null,
    val description: String? = null,
    val isPremium: Boolean = false,
)

@Serializable
class EpisodeItem(
    val id: Int = 0,
    val translation: String = "",
    val episode: Int = 0,
    val premium: Int = 0,
    val player: String? = null,
)
