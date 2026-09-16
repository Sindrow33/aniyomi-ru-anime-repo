package eu.kanade.tachiyomi.animeextension.all.eporner

import kotlinx.serialization.Serializable

@Serializable
class SearchResponse(
    val count: Int = 0,
    val page: Int = 1,
    val per_page: Int = 30,
    val total_count: Int = 0,
    val total_pages: Int = 0,
    val videos: List<VideoItem> = emptyList(),
)

@Serializable
class VideoItem(
    val id: String = "",
    val title: String = "",
    val keywords: String? = null,
    val views: Long = 0,
    val rate: String? = null,
    val url: String = "",
    val added: String? = null,
    val length_min: String? = null,
    val default_thumb: Thumb? = null,
)

@Serializable
class Thumb(
    val src: String? = null,
)

@Serializable
class PlayerResponse(
    val vid: String = "",
    val available: Boolean = false,
    val code: Int = 0,
    val message: String = "",
    val sources: Sources = Sources(),
)

@Serializable
class Sources(
    val mp4: Map<String, Mp4Source> = emptyMap(),
)

@Serializable
class Mp4Source(
    val labelShort: String? = null,
    val src: String = "",
    val default: Boolean = false,
)
