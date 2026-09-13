package eu.kanade.tachiyomi.animeextension.ru.anime365

import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList

object Anime365Filters {

    class CheckBoxVal(name: String, state: Boolean = false) : AnimeFilter.CheckBox(name, state)

    open class CheckBoxGroup(name: String, values: List<CheckBoxVal>) : AnimeFilter.Group<AnimeFilter.CheckBox>(name, values)

    class GenreFilter : CheckBoxGroup("Жанр", GENRES.map { CheckBoxVal(it.first) })
    class TypeFilter : CheckBoxGroup("Тип", TYPES.map { CheckBoxVal(it.first) })

    class StrictGenresFilter : AnimeFilter.CheckBox("Все выбранные жанры одновременно", true)
    class AiringFilter : AnimeFilter.CheckBox("Только онгоинги", false)

    class YearFilter : AnimeFilter.Text("Год выхода (например 2024)")

    val FILTER_LIST
        get() = AnimeFilterList(
            AnimeFilter.Header("Фильтры не действуют при текстовом поиске"),
            GenreFilter(),
            StrictGenresFilter(),
            TypeFilter(),
            AiringFilter(),
            YearFilter(),
        )

    data class SearchParams(
        val genres: List<String> = emptyList(),
        val strictGenres: Boolean = true,
        val types: List<String> = emptyList(),
        val onlyAiring: Boolean = false,
        val year: String = "",
    )

    private inline fun <reified R> AnimeFilterList.firstOrNullAs(): R? = filterIsInstance<R>().firstOrNull()

    private fun AnimeFilter.Group<AnimeFilter.CheckBox>.selected(options: List<Pair<String, String>>): List<String> = state
        .filter { it.state }
        .mapNotNull { box -> options.firstOrNull { it.first == box.name }?.second }

    fun getSearchParameters(filters: AnimeFilterList): SearchParams {
        if (filters.isEmpty()) return SearchParams()

        return SearchParams(
            genres = filters.firstOrNullAs<GenreFilter>()?.selected(GENRES).orEmpty(),
            strictGenres = filters.firstOrNullAs<StrictGenresFilter>()?.state != false,
            types = filters.firstOrNullAs<TypeFilter>()?.selected(TYPES).orEmpty(),
            onlyAiring = filters.firstOrNullAs<AiringFilter>()?.state == true,
            year = filters.firstOrNullAs<YearFilter>()?.state?.trim()?.takeIf { it.toIntOrNull() != null }.orEmpty(),
        )
    }

    private val TYPES = listOf(
        "ТВ сериал" to "tv",
        "Фильм" to "movie",
        "OVA" to "ova",
        "ONA" to "ona",
        "Спешл" to "special",
        "Клип" to "music",
    )

    private val GENRES = listOf(
        "Антропоморфизм" to "51",
        "Безумие" to "5",
        "Боевые искусства" to "17",
        "Вампиры" to "32",
        "Взрослые персонажи" to "50",
        "Видеоигры" to "79",
        "Военное" to "38",
        "Выживание" to "76",
        "Гарем" to "35",
        "Городское фэнтези" to "82",
        "Гурман" to "47",
        "Гэг юмор" to "57",
        "Демоны" to "6",
        "Детектив" to "7",
        "Детское" to "15",
        "Дзёсей" to "43",
        "Драма" to "8",
        "Злодейки" to "83",
        "Идолы (девушки)" to "60",
        "Идолы (парни)" to "61",
        "Игра с высокими ставками" to "59",
        "Игры" to "11",
        "Изобразительное искусство" to "80",
        "Исекай" to "62",
        "Исполнительское искусство" to "70",
        "Исторический" to "13",
        "Ияшикей" to "63",
        "Комедия" to "4",
        "Командный спорт" to "77",
        "Космос" to "29",
        "Кровь" to "58",
        "Культура отаку" to "69",
        "Любовный многоугольник" to "64",
        "Махо-сёдзё" to "66",
        "Машины" to "3",
        "Медицина" to "67",
        "Меха" to "18",
        "Музыка" to "19",
        "Образовательное" to "56",
        "Организованная преступность" to "68",
        "Пародия" to "20",
        "Питомцы" to "71",
        "Повседневность" to "36",
        "Полиция" to "39",
        "Правонарушители" to "55",
        "Приключения" to "2",
        "Психологическое" to "40",
        "Работа" to "48",
        "Реинкарнация" to "72",
        "Романтика" to "22",
        "Романтический подтекст" to "74",
        "Самураи" to "21",
        "Сверхъестественное" to "37",
        "Сейнен" to "42",
        "Сёдзе" to "25",
        "Сёнен" to "27",
        "Спорт" to "30",
        "Спортивные единоборства" to "54",
        "Супер сила" to "31",
        "Триллер" to "41",
        "Удостоено наград" to "46",
        "Ужасы" to "14",
        "Уход за детьми" to "53",
        "Фантастика" to "24",
        "Фэнтези" to "10",
        "Школа" to "23",
        "Шоу-бизнес" to "75",
        "Экшен" to "1",
        "Этти" to "9",
        "CGDCT" to "52",
    )
}
