package eu.kanade.tachiyomi.animeextension.ru.justsu

import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList

object JustSuFilters {

    class SectionFilter : AnimeFilter.Select<String>("Раздел", SECTIONS.map { it.first }.toTypedArray(), 0)
    class YearFilter : AnimeFilter.Select<String>("Год", YEARS.map { it.first }.toTypedArray(), 0)

    val FILTER_LIST
        get() = AnimeFilterList(
            AnimeFilter.Header("Год имеет приоритет над разделом"),
            SectionFilter(),
            YearFilter(),
        )

    data class SearchParams(
        val path: String = SECTIONS[0].second,
    )

    private inline fun <reified R> AnimeFilterList.firstOrNullAs(): R? = filterIsInstance<R>().firstOrNull()

    fun getSearchParameters(filters: AnimeFilterList): SearchParams {
        if (filters.isEmpty()) return SearchParams()

        val year = filters.firstOrNullAs<YearFilter>()?.state ?: 0
        if (year > 0) return SearchParams(YEARS[year].second)

        val section = filters.firstOrNullAs<SectionFilter>()?.state ?: 0
        return SearchParams(SECTIONS.getOrElse(section) { SECTIONS[0] }.second)
    }

    private val SECTIONS = listOf(
        "Онгоинги" to "/ongoing/",
        "Вышедшие" to "/vyshlo/",
        "Анонсы" to "/anons/",
    )

    private val YEARS = listOf(
        "Любой" to "",
    ) + (2026 downTo 2010).map { it.toString() to "/god/$it/" }
}
