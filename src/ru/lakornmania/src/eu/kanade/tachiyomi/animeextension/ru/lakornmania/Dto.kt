package eu.kanade.tachiyomi.animeextension.ru.lakornmania

import kotlinx.serialization.Serializable

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
