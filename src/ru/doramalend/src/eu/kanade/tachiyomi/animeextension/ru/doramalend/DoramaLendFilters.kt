package eu.kanade.tachiyomi.animeextension.ru.doramalend

import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList

object DoramaLendFilters {

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
        "Новые" to "/doramy-novye/",
        "Лучшие" to "/luchshie-doramy/",
        "Корейские" to "/doramy-korejskie/",
        "Китайские" to "/doramy-kitajskie/",
        "Японские" to "/doramy-japonskie/",
    )

    private val YEARS = listOf(
        "Любой" to "",
        "2026" to "/doramy2026/",
        "2025" to "/doramy2025/",
        "2024" to "/doramy2024/",
    )

    private val GENRES = listOf(
        "Любой" to "",
        "Боевики" to "/boeviki/",
        "Детективы" to "/detektivy/",
        "Драмы" to "/dramy/",
        "Исторические" to "/istoricheskie/",
        "Комедии" to "/komedii/",
        "Криминал" to "/kriminal/",
        "Мелодрамы" to "/melodramy/",
        "Приключения" to "/prikljuchenija/",
        "Триллеры" to "/triller/",
        "Ужасы" to "/uzhasy/",
        "Фантастика" to "/fantastika/",
        "Фэнтези" to "/fjentezi/",
    )
}
