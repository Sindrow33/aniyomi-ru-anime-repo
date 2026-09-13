package eu.kanade.tachiyomi.animeextension.ru.anilibria

import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList

object AniLibriaFilters {

    class CheckBoxVal(name: String, state: Boolean = false) : AnimeFilter.CheckBox(name, state)

    open class CheckBoxGroup(name: String, values: List<CheckBoxVal>) : AnimeFilter.Group<AnimeFilter.CheckBox>(name, values)

    class GenresFilter : CheckBoxGroup("Жанр", GENRES.map { CheckBoxVal(it.first) })
    class TypesFilter : CheckBoxGroup("Тип", TYPES.map { CheckBoxVal(it.first) })
    class SeasonsFilter : CheckBoxGroup("Сезон", SEASONS.map { CheckBoxVal(it.first) })
    class AgeRatingsFilter : CheckBoxGroup("Возрастной рейтинг", AGE_RATINGS.map { CheckBoxVal(it.first) })

    class YearsFilter : AnimeFilter.Text("Год или диапазон (2024 или 2020,2024)")

    class SortFilter : AnimeFilter.Select<String>("Сортировать по", SORTING.map { it.first }.toTypedArray(), 0)

    val FILTER_LIST
        get() = AnimeFilterList(
            SortFilter(),
            GenresFilter(),
            TypesFilter(),
            SeasonsFilter(),
            AgeRatingsFilter(),
            YearsFilter(),
        )

    data class SearchParams(
        val sorting: String = SORTING[0].second,
        val genres: List<String> = emptyList(),
        val types: List<String> = emptyList(),
        val seasons: List<String> = emptyList(),
        val ageRatings: List<String> = emptyList(),
        val years: List<String> = emptyList(),
    )

    private inline fun <reified R> AnimeFilterList.firstOrNullAs(): R? = filterIsInstance<R>().firstOrNull()

    private fun AnimeFilter.Group<AnimeFilter.CheckBox>.selected(options: List<Pair<String, String>>): List<String> = state
        .filter { it.state }
        .mapNotNull { box -> options.firstOrNull { it.first == box.name }?.second }

    fun getSearchParameters(filters: AnimeFilterList): SearchParams {
        if (filters.isEmpty()) return SearchParams()

        val sortIndex = filters.firstOrNullAs<SortFilter>()?.state ?: 0

        return SearchParams(
            sorting = SORTING.getOrElse(sortIndex) { SORTING[0] }.second,
            genres = filters.firstOrNullAs<GenresFilter>()?.selected(GENRES).orEmpty(),
            types = filters.firstOrNullAs<TypesFilter>()?.selected(TYPES).orEmpty(),
            seasons = filters.firstOrNullAs<SeasonsFilter>()?.selected(SEASONS).orEmpty(),
            ageRatings = filters.firstOrNullAs<AgeRatingsFilter>()?.selected(AGE_RATINGS).orEmpty(),
            years = filters.firstOrNullAs<YearsFilter>()?.state
                ?.split(',')
                ?.mapNotNull { it.trim().toIntOrNull()?.toString() }
                .orEmpty(),
        )
    }

    private val SORTING = listOf(
        "Популярные" to "RATING_DESC",
        "Обновлены недавно" to "FRESH_AT_DESC",
        "Обновлены давно" to "FRESH_AT_ASC",
        "Сначала новые" to "YEAR_DESC",
        "Сначала старые" to "YEAR_ASC",
        "По алфавиту (А-Я)" to "NAME_ASC",
        "По алфавиту (Я-А)" to "NAME_DESC",
    )

    private val TYPES = listOf(
        "ТВ" to "TV",
        "ONA" to "ONA",
        "WEB" to "WEB",
        "OVA" to "OVA",
        "OAD" to "OAD",
        "Фильм" to "MOVIE",
        "Дорама" to "DORAMA",
        "Спешл" to "SPECIAL",
    )

    private val SEASONS = listOf(
        "Зима" to "winter",
        "Весна" to "spring",
        "Лето" to "summer",
        "Осень" to "autumn",
    )

    private val AGE_RATINGS = listOf(
        "0+" to "R0_PLUS",
        "6+" to "R6_PLUS",
        "12+" to "R12_PLUS",
        "16+" to "R16_PLUS",
        "18+" to "R18_PLUS",
    )

    private val GENRES = listOf(
        "Боевые искусства" to "15",
        "Вампиры" to "24",
        "Гарем" to "32",
        "Демоны" to "16",
        "Детектив" to "25",
        "Дзёсей" to "33",
        "Драма" to "8",
        "Игры" to "17",
        "Исекай" to "34",
        "Исторический" to "26",
        "Киберпанк" to "30",
        "Комедия" to "1",
        "Магия" to "18",
        "Меха" to "2",
        "Мистика" to "9",
        "Музыка" to "19",
        "Пародия" to "36",
        "Повседневность" to "10",
        "Приключения" to "27",
        "Психологическое" to "3",
        "Романтика" to "11",
        "Сверхъестественное" to "28",
        "Сёдзе" to "20",
        "Сёдзе-ай" to "31",
        "Сейнен" to "5",
        "Сёнен" to "4",
        "Спорт" to "12",
        "Супер сила" to "21",
        "Триллер" to "6",
        "Ужасы" to "13",
        "Фантастика" to "22",
        "Фэнтези" to "29",
        "Школа" to "7",
        "Экшен" to "14",
        "Этти" to "23",
    )
}
