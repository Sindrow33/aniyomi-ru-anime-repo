package eu.kanade.tachiyomi.animeextension.all.xhamster

import kotlinx.serialization.Serializable

@Serializable
class WatchInitials(
    val videoModel: VideoModel = VideoModel(),
    val xplayerSettings: XplayerSettings = XplayerSettings(),
)

@Serializable
class VideoModel(
    val id: Long = 0,
    val title: String = "",
    val description: String = "",
    val pageURL: String = "",
    val thumbURL: String = "",
    val duration: Int = 0,
    val views: Long = 0,
    val created: Long = 0,
    val isVR: Boolean = false,
    val resolution: List<Int> = emptyList(),
    val rating: Rating? = null,
    val author: Author? = null,
)

@Serializable
class Rating(
    val value: Int = 0,
)

@Serializable
class Author(
    val name: String = "",
)

@Serializable
class XplayerSettings(
    val sources: Sources = Sources(),
)

@Serializable
class Sources(
    val standard: Standard = Standard(),
    val hls: Map<String, HlsSource> = emptyMap(),
)

@Serializable
class Standard(
    val h264: List<H264Source> = emptyList(),
)

@Serializable
class H264Source(
    val url: String = "",
    val fallback: String = "",
    val quality: String = "",
    val label: String = "",
)

@Serializable
class HlsSource(
    val url: String = "",
)

@Serializable
class TagsComponent(
    val tags: List<Tag> = emptyList(),
)

@Serializable
class Tag(
    val name: String = "",
    val isCategory: Boolean = false,
    val isTag: Boolean = false,
    val isPornstar: Boolean = false,
)
