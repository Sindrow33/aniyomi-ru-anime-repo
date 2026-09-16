package eu.kanade.tachiyomi.animeextension.ru.doramyclub

import kotlinx.serialization.Serializable

@Serializable
class FilterResponse(
    val success: Boolean = false,
    val items: List<FilterItem> = emptyList(),
    val page: Int = 1,
    val total: Int = 0,
    val hasMore: Boolean = false,
)

@Serializable
class SearchResponse(
    val success: Boolean = false,
    val items: List<FilterItem> = emptyList(),
)

@Serializable
class FilterItem(
    val title: String = "",
    val url: String = "",
    val poster: String? = null,
    val year: String? = null,
    val country: String? = null,
    val genre: String? = null,
    val type: String? = null,
    val original: String? = null,
) {
    /** The site sometimes returns mangled UTF-8 in these fields; drop the replacement marks. */
    val subtitle: String
        get() = listOfNotNull(
            country?.clean(),
            year?.take(4)?.clean(),
            genre?.clean(),
        ).filter { it.isNotBlank() }.joinToString(", ")

    private fun String.clean(): String = replace("??", "").trim()
}

@Serializable
class KodikUrlParams(
    val d: String = "",
    val d_sign: String = "",
    val pd: String = "",
    val pd_sign: String = "",
    val ref: String = "",
    val ref_sign: String = "",
)

@Serializable
class KodikFtorResponse(
    val links: Map<String, List<KodikLinkDto>> = emptyMap(),
)

@Serializable
class KodikLinkDto(
    val src: String = "",
    val type: String? = null,
)
