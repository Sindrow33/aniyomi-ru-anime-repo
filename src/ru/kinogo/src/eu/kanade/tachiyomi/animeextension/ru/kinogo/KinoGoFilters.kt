package eu.kanade.tachiyomi.animeextension.ru.kinogo

import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList

object KinoGoFilters {

    class SectionFilter : AnimeFilter.Select<String>("Раздел", SECTIONS.map { it.first }.toTypedArray(), 0)
    class GenreFilter : AnimeFilter.Select<String>("Жанр", GENRES.map { it.first }.toTypedArray(), 0)
    class YearFilter : AnimeFilter.Select<String>("Год", YEARS.map { it.first }.toTypedArray(), 0)

    val FILTER_LIST
        get() = AnimeFilterList(
            AnimeFilter.Header("Фильтры работают, когда строка поиска пуста"),
            AnimeFilter.Header("Действует только самый нижний заполненный фильтр"),
            SectionFilter(),
            GenreFilter(),
            YearFilter(),
        )

    data class SearchParams(
        val section: String = "",
        val genre: String = "",
        val year: String = "",
    ) {
        /** The site has no combined filter page, so the most specific pick wins. */
        val path: String
            get() = listOf(year, genre, section).firstOrNull { it.isNotBlank() } ?: ""
    }

    private inline fun <reified R> AnimeFilterList.firstOrNullAs(): R? = filterIsInstance<R>().firstOrNull()

    fun getSearchParameters(filters: AnimeFilterList): SearchParams {
        if (filters.isEmpty()) return SearchParams()

        fun pick(state: Int, values: List<Pair<String, String>>) = values.getOrElse(state) { values[0] }.second

        return SearchParams(
            section = pick(filters.firstOrNullAs<SectionFilter>()?.state ?: 0, SECTIONS),
            genre = pick(filters.firstOrNullAs<GenreFilter>()?.state ?: 0, GENRES),
            year = pick(filters.firstOrNullAs<YearFilter>()?.state ?: 0, YEARS),
        )
    }

    private val SECTIONS = listOf(
        "Всё" to "",
        "Новинки" to "/v1new",
        "Фильмы" to "/filmy",
        "Сериалы" to "/serialy",
    )

    private val GENRES = listOf(
        "Любой" to "",
        "Боевик" to "/boevik",
        "Драма" to "/drama",
        "Комедия" to "/komedia",
        "Криминал" to "/kriminal",
        "Мелодрама" to "/melodrama",
        "Приключения" to "/prikluchenia",
        "Триллер" to "/triller",
        "Фантастика" to "/fantastika",
        "Фэнтези" to "/fentezi",
    )

    private val YEARS = (2026 downTo 2015).map { "$it" to "/xfsearch/year-teg-xfsearch/$it" }
        .let { listOf("Любой" to "") + it }
}
