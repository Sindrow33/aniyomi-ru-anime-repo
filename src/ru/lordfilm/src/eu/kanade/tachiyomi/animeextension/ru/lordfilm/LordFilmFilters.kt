package eu.kanade.tachiyomi.animeextension.ru.lordfilm

import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList

/**
 * Фильтры строятся под DLE-роутинг сайта:
 *  - раздел/подборка — обычный путь (`/serialy/`, `/podborki/doramy/`),
 *    страницы через `/page/N/`;
 *  - жанры/страны/годы — «умный фильтр» `/sf/<key>:<value>/`, несколько
 *    значений одного ключа перечисляются через `;`, ключи комбинируются
 *    подряд, страницы через `/page:N/`.
 *
 * Поэтому жанры и страны — это CheckBox-группы (можно выбрать несколько),
 * а не один Select, как было раньше.
 */
object LordFilmFilters {

    class CheckBoxVal(name: String, state: Boolean = false) : AnimeFilter.CheckBox(name, state)

    open class CheckBoxGroup(name: String, values: List<CheckBoxVal>) : AnimeFilter.Group<AnimeFilter.CheckBox>(name, values)

    class SectionFilter : AnimeFilter.Select<String>("Раздел", SECTIONS.map { it.first }.toTypedArray(), 0)
    class CollectionFilter : AnimeFilter.Select<String>("Подборка", COLLECTIONS.map { it.first }.toTypedArray(), 0)

    class GenreFilter : CheckBoxGroup("Жанры", GENRES.map { CheckBoxVal(it) })
    class CountryFilter : CheckBoxGroup("Страны", COUNTRIES.map { CheckBoxVal(it) })

    class YearFromFilter : AnimeFilter.Text("Год от")
    class YearToFilter : AnimeFilter.Text("Год до")

    val FILTER_LIST
        get() = AnimeFilterList(
            AnimeFilter.Header("Фильтры работают, когда строка поиска пуста"),
            AnimeFilter.Header("Жанры и страны можно отмечать по нескольку"),
            GenreFilter(),
            CountryFilter(),
            YearFromFilter(),
            YearToFilter(),
            AnimeFilter.Separator(),
            AnimeFilter.Header("Раздел и подборка игнорируют фильтры выше"),
            SectionFilter(),
            CollectionFilter(),
        )

    data class SearchParams(
        val section: String = "",
        val collection: String = "",
        val genres: List<String> = emptyList(),
        val countries: List<String> = emptyList(),
        val yearFrom: String = "",
        val yearTo: String = "",
    ) {
        /** true — «умный фильтр» /sf/..., у него своя схема пагинации. */
        val isSmartFilter: Boolean
            get() = smartSegments.isNotEmpty()

        private val smartSegments: List<String>
            get() = buildList {
                if (genres.isNotEmpty()) add("genre:" + genres.joinToString(";"))
                if (countries.isNotEmpty()) add("country:" + countries.joinToString(";"))
                yearRange()?.let { add("year:$it") }
            }

        private fun yearRange(): String? {
            val from = yearFrom.toIntOrNull()
            val to = yearTo.toIntOrNull()
            if (from == null && to == null) return null
            val start = from ?: YEAR_MIN
            val end = to ?: YEAR_MAX

            return if (start <= end) "$start;$end" else "$end;$start"
        }

        /** Путь без завершающего слэша; пустой — главная выборка «Фильмы». */
        val path: String
            get() = when {
                isSmartFilter -> "/sf/" + smartSegments.joinToString("/")
                collection.isNotBlank() -> collection
                section.isNotBlank() -> section
                else -> SECTION_FILMS
            }
    }

    private inline fun <reified R> AnimeFilterList.firstOrNullAs(): R? = filterIsInstance<R>().firstOrNull()

    private fun AnimeFilter.Group<AnimeFilter.CheckBox>.checked(): List<String> = state.filter { it.state }.map { it.name }

    fun getSearchParameters(filters: AnimeFilterList): SearchParams {
        if (filters.isEmpty()) return SearchParams()

        fun pick(state: Int, values: List<Pair<String, String>>) = values.getOrElse(state) { values[0] }.second

        return SearchParams(
            section = pick(filters.firstOrNullAs<SectionFilter>()?.state ?: 0, SECTIONS),
            collection = pick(filters.firstOrNullAs<CollectionFilter>()?.state ?: 0, COLLECTIONS),
            genres = filters.firstOrNullAs<GenreFilter>()?.checked().orEmpty(),
            countries = filters.firstOrNullAs<CountryFilter>()?.checked().orEmpty(),
            yearFrom = filters.firstOrNullAs<YearFromFilter>()?.state?.trim().orEmpty(),
            yearTo = filters.firstOrNullAs<YearToFilter>()?.state?.trim().orEmpty(),
        )
    }

    const val SECTION_FILMS = "/filmy"

    private const val YEAR_MIN = 1920
    private const val YEAR_MAX = 2030

    private val SECTIONS = listOf(
        "Фильмы" to SECTION_FILMS,
        "Сериалы" to "/serialy",
        "Мультфильмы" to "/mult",
        "Аниме" to "/anime",
        "Премьеры" to "/osoboe",
        "Топ 50" to "/top50",
        "Сериалы 2026" to "/serialy/serialy-2026",
        "Сериалы 2025" to "/serialy/serialy-2025",
        "Фильмы 2026" to "/filmy/2026",
        "Фильмы 2025" to "/filmy/2025",
        "Фильмы 2024" to "/filmy/2024",
        "Русские фильмы" to "/filmy/russkie",
        "Зарубежные фильмы" to "/filmy/amerikanskie",
        "Фильмы СССР" to "/filmy/sssr",
    )

    private val COLLECTIONS = listOf(
        "Нет" to "",
        "Дорамы" to "/podborki/doramy",
        "Netflix" to "/podborki/netflix",
        "Apple TV" to "/podborki/apple-tv",
        "Hulu" to "/podborki/hulu",
        "Сериалы HBO" to "/podborki/serialy-hbo",
        "Ситкомы" to "/podborki/sitkomy",
        "Marvel" to "/podborki/filmy-marvel",
        "DC" to "/podborki/filmy-po-dc-komiksam-1",
        "Про супергероев" to "/podborki/filmy-pro-supergeroev",
        "С наградами" to "/podborki/filmy-s-nagradami",
        "Самые кассовые" to "/podborki/samye-kassovye",
        "По книгам" to "/podborki/filmy-po-knigam",
        "Психологические" to "/podborki/psihologicheskie-filmy",
        "Романтические комедии" to "/podborki/romanticheskie-komedii",
        "Новогодние" to "/podborki/novogodnie-filmy",
        "На Хэллоуин" to "/podborki/filmy-na-hjellouin",
        "Ко Дню Победы" to "/podborki/filmy-ko-dnju-pobedy",
        "Катастрофы" to "/podborki/filmy-katastrofy",
        "Про путешествия" to "/podborki/filmy-pro-puteshestvija",
        "Про путешествия во времени" to "/podborki/filmy-pro-puteshestvija-vo-vremeni",
        "Про роботов" to "/podborki/filmy-pro-robotov",
        "Про школу" to "/podborki/filmy-pro-shkolu",
        "Про снайперов" to "/podborki/filmy-pro-snajperov",
        "Про средневековье" to "/podborki/filmy-pro-srednevekove",
        "Про тюрьму" to "/podborki/filmy-pro-tjurmu",
        "Про танки" to "/podborki/filmy-pro-tanki",
        "Про спорт" to "/podborki/filmy-pro-sport",
        "Про футбол" to "/podborki/filmy-pro-futbol",
        "Про баскетбол" to "/podborki/pro-basketbol",
        "Про музыкантов" to "/podborki/pro-muzykantov",
    )

    // Проверено 2026-09-27: каждое значение отдаёт непустую выдачу в /sf/genre:.
    private val GENRES = listOf(
        "Аниме",
        "Биография",
        "Боевик",
        "Вестерн",
        "Военный",
        "Детектив",
        "Детский",
        "Документальный",
        "Драма",
        "Исторический",
        "Комедия",
        "Короткометражка",
        "Криминал",
        "Мелодрама",
        "Музыка",
        "Мультфильм",
        "Мюзикл",
        "Приключения",
        "Семейный",
        "Спорт",
        "Триллер",
        "Ужасы",
        "Фантастика",
        "Фэнтези",
    )

    private val COUNTRIES = listOf(
        "Австралия",
        "Бразилия",
        "Великобритания",
        "Германия",
        "Гонконг",
        "Дания",
        "Индия",
        "Испания",
        "Италия",
        "Канада",
        "Китай",
        "Корея Южная",
        "Мексика",
        "Норвегия",
        "Польша",
        "Россия",
        "СССР",
        "США",
        "Таиланд",
        "Турция",
        "Украина",
        "Франция",
        "Швеция",
        "Япония",
    )
}
