package eu.kanade.tachiyomi.animeextension.ru.animakima

import kotlinx.serialization.Serializable

@Serializable
class ApiResponse(
    val success: Boolean = false,
    val data: String? = null,
    val message: String? = null,
    val player: String? = null,
    val combined: Boolean? = null,
)

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
