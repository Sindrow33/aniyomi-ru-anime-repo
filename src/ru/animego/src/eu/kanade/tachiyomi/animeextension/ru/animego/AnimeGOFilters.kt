package eu.kanade.tachiyomi.animeextension.ru.animego

import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList

object AnimeGOFilters {

    class CheckBoxVal(name: String, state: Boolean = false) : AnimeFilter.CheckBox(name, state)

    open class CheckBoxGroup(name: String, values: List<CheckBoxVal>) : AnimeFilter.Group<AnimeFilter.CheckBox>(name, values)

    class GenreFilter : CheckBoxGroup("Жанр", GENRES.map { CheckBoxVal(it.first) })
    class KindFilter : CheckBoxGroup("Тип", KINDS.map { CheckBoxVal(it.first) })
    class StatusFilter : CheckBoxGroup("Статус", STATUSES.map { CheckBoxVal(it.first) })
    class RatingFilter : CheckBoxGroup("Возрастной рейтинг", RATINGS.map { CheckBoxVal(it.first) })
    class DurationFilter : CheckBoxGroup("Длительность эпизода", DURATIONS.map { CheckBoxVal(it.first) })

    class StrictGenresFilter : AnimeFilter.CheckBox("Строгое совпадение жанров", false)

    class YearFromFilter : AnimeFilter.Text("Год от")
    class YearToFilter : AnimeFilter.Text("Год до")

    class SortFilter : AnimeFilter.Select<String>("Сортировать по", SORTS.map { it.first }.toTypedArray(), 0)
    class DirectionFilter : AnimeFilter.Select<String>("Порядок", arrayOf("По убыванию", "По возрастанию"), 0)

    val FILTER_LIST
        get() = AnimeFilterList(
            SortFilter(),
            DirectionFilter(),
            GenreFilter(),
            StrictGenresFilter(),
            KindFilter(),
            StatusFilter(),
            RatingFilter(),
            DurationFilter(),
            YearFromFilter(),
            YearToFilter(),
        )

    data class SearchParams(
        val sort: String = SORTS[0].second,
        val direction: String = "desc",
        val genres: List<String> = emptyList(),
        val strictGenres: Boolean = false,
        val kinds: List<String> = emptyList(),
        val statuses: List<String> = emptyList(),
        val ratings: List<String> = emptyList(),
        val durations: List<String> = emptyList(),
        val yearFrom: String = "",
        val yearTo: String = "",
    )

    private inline fun <reified R> AnimeFilterList.firstOrNullAs(): R? = filterIsInstance<R>().firstOrNull()

    private fun AnimeFilter.Group<AnimeFilter.CheckBox>.selected(options: List<Pair<String, String>>): List<String> = state
        .filter { it.state }
        .mapNotNull { box -> options.firstOrNull { it.first == box.name }?.second }

    fun getSearchParameters(filters: AnimeFilterList): SearchParams {
        if (filters.isEmpty()) return SearchParams()

        val sortIndex = filters.firstOrNullAs<SortFilter>()?.state ?: 0
        val directionIndex = filters.firstOrNullAs<DirectionFilter>()?.state ?: 0

        return SearchParams(
            sort = SORTS.getOrElse(sortIndex) { SORTS[0] }.second,
            direction = if (directionIndex == 1) "asc" else "desc",
            genres = filters.firstOrNullAs<GenreFilter>()?.selected(GENRES).orEmpty(),
            strictGenres = filters.firstOrNullAs<StrictGenresFilter>()?.state == true,
            kinds = filters.firstOrNullAs<KindFilter>()?.selected(KINDS).orEmpty(),
            statuses = filters.firstOrNullAs<StatusFilter>()?.selected(STATUSES).orEmpty(),
            ratings = filters.firstOrNullAs<RatingFilter>()?.selected(RATINGS).orEmpty(),
            durations = filters.firstOrNullAs<DurationFilter>()?.selected(DURATIONS).orEmpty(),
            yearFrom = filters.firstOrNullAs<YearFromFilter>()?.state?.trim()?.takeIf { it.toIntOrNull() != null }.orEmpty(),
            yearTo = filters.firstOrNullAs<YearToFilter>()?.state?.trim()?.takeIf { it.toIntOrNull() != null }.orEmpty(),
        )
    }

    private val SORTS = listOf(
        "Дате добавления" to "createdAt",
        "Популярности" to "popular",
        "Рейтингу" to "rating",
        "Дате выхода" to "aired",
        "Алфавиту" to "name",
    )

    private val KINDS = listOf(
        "Сериал" to "tv",
        "Фильм" to "movie",
        "OVA" to "ova",
        "ONA" to "ona",
        "Спешл" to "special",
    )

    private val STATUSES = listOf(
        "Онгоинг" to "ongoing",
        "Вышел" to "released",
        "Анонс" to "anons",
    )

    private val RATINGS = listOf(
        "G — без ограничений" to "G",
        "PG — детям" to "PG",
        "PG-13 — от 13 лет" to "PG-13",
        "R-17 — от 17 лет" to "R-17",
        "R+ — от 18 лет" to "R+",
    )

    private val DURATIONS = listOf(
        "Меньше 10 минут" to "short",
        "10-30 минут" to "medium",
        "30-60 минут" to "long",
        "Больше 60 минут" to "huge",
    )

    private val GENRES = listOf(
        "Безумие" to "5",
        "Боевые искусства" to "17",
        "Вампиры" to "32",
        "Военное" to "38",
        "Гарем" to "35",
        "Городское фэнтези" to "197",
        "Гурман" to "543",
        "Демоны" to "6",
        "Детектив" to "7",
        "Детское" to "15",
        "Дзёсей" to "43",
        "Драма" to "8",
        "Изобразительное искусство" to "108",
        "Игры" to "11",
        "Исторический" to "13",
        "Исэкай" to "130",
        "Комедия" to "4",
        "Командный спорт" to "102",
        "Космос" to "29",
        "Культура отаку" to "137",
        "Магия" to "16",
        "Машины" to "3",
        "Меха" to "18",
        "Музыка" to "19",
        "Пародия" to "20",
        "Повседневность" to "36",
        "Полиция" to "39",
        "Приключения" to "2",
        "Психологическое" to "40",
        "Работа" to "541",
        "Реинкарнация" to "106",
        "Романтика" to "22",
        "Самураи" to "21",
        "Сверхъестественное" to "37",
        "Сёдзё" to "25",
        "Сёдзё-ай" to "26",
        "Сёнен" to "27",
        "Сёнен-ай" to "28",
        "Спорт" to "30",
        "Супер сила" to "31",
        "Сэйнэн" to "42",
        "Триллер" to "41",
        "Ужасы" to "14",
        "Фантастика" to "24",
        "Фэнтези" to "10",
        "Школа" to "23",
        "Экшен" to "1",
        "Этти" to "9",
        "Юри" to "34",
        "Яой" to "33",
    )
}
