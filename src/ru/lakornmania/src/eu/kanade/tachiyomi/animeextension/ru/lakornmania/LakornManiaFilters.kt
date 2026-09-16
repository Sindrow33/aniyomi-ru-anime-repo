package eu.kanade.tachiyomi.animeextension.ru.lakornmania

import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList

object LakornManiaFilters {

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
        "Все" to "",
        "Новые серии" to "/ongoing",
        "Корейские" to "/korea",
        "Китайские" to "/china",
        "Японские" to "/japan",
        "Тайские" to "/thailand",
        "Тайваньские" to "/taiwan",
        "Фильмы" to "/films",
        "Сериалы" to "/serials",
    )

    private val GENRES = listOf(
        "Любой" to "",
        "Боевики" to "/action",
        "Драмы" to "/dramy",
        "Комедии" to "/komedii",
        "Детективы" to "/detektivy",
        "Исторические" to "/istorija",
        "Криминальные" to "/kriminal",
        "Приключения" to "/prikljuchenija",
        "Мелодрамы" to "/romantika",
        "Фантастика" to "/fantastika",
        "Триллеры" to "/trillery",
        "Мистика" to "/mistika",
        "Фэнтези" to "/fehntezi",
        "Ужасы" to "/uzhasy",
    )

    private val YEARS = listOf(
        "Любой" to "",
        "2026" to "/2026-god",
        "2025" to "/2025-god",
        "2024" to "/2024-god",
        "2023" to "/2023-god",
        "2022" to "/2022-god",
        "2021" to "/2021-god",
    )
}
