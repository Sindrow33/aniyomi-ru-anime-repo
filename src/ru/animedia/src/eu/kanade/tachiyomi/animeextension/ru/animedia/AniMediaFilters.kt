package eu.kanade.tachiyomi.animeextension.ru.animedia

import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList

object AniMediaFilters {

    class SectionFilter : AnimeFilter.Select<String>("Раздел", SECTIONS.map { it.first }.toTypedArray(), 0)
    class GenreFilter : AnimeFilter.Select<String>("Жанр", GENRES.map { it.first }.toTypedArray(), 0)
    class YearFilter : AnimeFilter.Select<String>("Год", YEARS.map { it.first }.toTypedArray(), 0)

    val FILTER_LIST
        get() = AnimeFilterList(
            AnimeFilter.Header("Жанр и год имеют приоритет над разделом"),
            SectionFilter(),
            GenreFilter(),
            YearFilter(),
        )

    data class SearchParams(
        val path: String = SECTIONS[0].second,
    )

    private inline fun <reified R> AnimeFilterList.firstOrNullAs(): R? = filterIsInstance<R>().firstOrNull()

    fun getSearchParameters(filters: AnimeFilterList): SearchParams {
        if (filters.isEmpty()) return SearchParams()

        val genre = filters.firstOrNullAs<GenreFilter>()?.state ?: 0
        if (genre > 0) return SearchParams(GENRES[genre].second)

        val year = filters.firstOrNullAs<YearFilter>()?.state ?: 0
        if (year > 0) return SearchParams(YEARS[year].second)

        val section = filters.firstOrNullAs<SectionFilter>()?.state ?: 0
        return SearchParams(SECTIONS.getOrElse(section) { SECTIONS[0] }.second)
    }

    private val SECTIONS = listOf(
        "Онгоинги" to "/ongoing/",
        "ТВ сериалы" to "/tv-series/",
        "OVA" to "/ova/",
        "Китайское аниме" to "/kitajskoe-anime/",
    )

    private val YEARS = listOf(
        "Любой" to "",
        "2026" to "/anime-2026/",
    )

    private val GENRES = listOf(
        "Любой" to "",
        "Авангард" to "/avangard/",
        "Гурман" to "/gurman/",
        "Детектив" to "/tajna/",
        "Драма" to "/drama/",
        "Комедия" to "/comedy/",
        "Повседневность" to "/povsednevnost/",
        "Приключения" to "/adventure/",
        "Романтика" to "/romance/",
        "Сверхъестественное" to "/supernatural/",
        "Спорт" to "/sport/",
        "Триллер" to "/thriller/",
        "Ужасы" to "/horror/",
        "Фантастика" to "/sci-fi/",
        "Фэнтези" to "/fantasy/",
        "Экшен" to "/action/",
        "Этти" to "/ecchi/",
    )
}
