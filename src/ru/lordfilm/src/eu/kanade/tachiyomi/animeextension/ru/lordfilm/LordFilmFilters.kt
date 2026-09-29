package eu.kanade.tachiyomi.animeextension.ru.lordfilm

import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList

/**
 * Новый сайт (lordfilmonline.cc и родственные зеркала) использует DLE-роутинг
 * с одним путём-категорией: фильмы — `/filmy/`, сериалы — `/serial/`,
 * а жанр/страна/год — это отдельные категории `/film_<key>/` и `/serial_<key>/`.
 * Умных фильтров `/sf/` (как на старом зеркале) больше нет, поэтому фильтры
 * сводятся к выбору одной категории (приоритет: жанр → страна → год).
 */
object LordFilmFilters {

    class SectionFilter : AnimeFilter.Select<String>("Раздел", arrayOf("Фильмы", "Сериалы"), 0)
    class GenreFilter : AnimeFilter.Select<String>("Жанр", GENRE_NAMES, 0)
    class CountryFilter : AnimeFilter.Select<String>("Страна", COUNTRY_NAMES, 0)
    class YearFilter : AnimeFilter.Select<String>("Год", YEAR_NAMES, 0)

    val FILTER_LIST: AnimeFilterList
        get() = AnimeFilterList(
            AnimeFilter.Header("Фильтры работают, когда строка поиска пуста"),
            SectionFilter(),
            AnimeFilter.Header("Ниже выбирается одна категория (жанр → страна → год)"),
            GenreFilter(),
            CountryFilter(),
            YearFilter(),
        )

    data class SearchParams(val path: String)

    fun getSearchParameters(filters: AnimeFilterList): SearchParams {
        val isSerial = (filters.filterIsInstance<SectionFilter>().firstOrNull()?.state ?: 0) == 1
        val prefix = if (isSerial) "serial_" else "film_"

        val genre = filters.filterIsInstance<GenreFilter>().firstOrNull()?.state ?: 0
        val country = filters.filterIsInstance<CountryFilter>().firstOrNull()?.state ?: 0
        val year = filters.filterIsInstance<YearFilter>().firstOrNull()?.state ?: 0

        val path = when {
            genre > 0 -> "/$prefix${GENRE_KEYS[genre - 1]}"
            country > 0 -> "/$prefix${COUNTRY_KEYS[country - 1]}"
            year > 0 -> "/$prefix${YEAR_VALUES[year - 1]}"
            isSerial -> "/serial"
            else -> "/filmy"
        }

        return SearchParams(path)
    }

    private val GENRE_NAMES = arrayOf(
        "Нет",
        "биография", "боевик", "вестерн", "военный", "детектив", "детский",
        "для взрослых", "документальный", "история", "драма", "комедия",
        "короткометражка", "криминал", "мелодрама", "музыка", "мюзикл",
        "приключения", "семейный", "спорт", "триллер", "ужасы", "фантастика",
        "фильм-нуар", "фэнтези",
    )

    private val GENRE_KEYS = arrayOf(
        "biography", "action", "western", "war", "detective", "kids",
        "adult", "documentary", "history", "drama", "comedy",
        "short", "crime", "melodrama", "music", "musical",
        "adventure", "family", "sport", "thriller", "horror", "fantasy",
        "noir", "fantasy_magic",
    )

    private val COUNTRY_NAMES = arrayOf(
        "Нет",
        "Россия", "США", "СССР", "Австралия", "Австрия", "Аргентина",
        "Беларусь", "Бельгия", "Болгария", "Бразилия", "Великобритания",
        "Венгрия", "Германия (ФРГ)", "Гонконг", "Греция", "Дания", "Египет",
        "Казахстан", "Камбоджа", "Китай", "Колумбия", "Конго",
        "Корея Южная", "Латвия", "Литва", "Люксембург", "Македония",
        "Малайзия", "Марокко", "Мексика", "Нидерланды", "Новая Зеландия",
        "Норвегия", "ОАЭ", "Остров Мэн", "Пакистан", "Польша", "Португалия",
        "Пуэрто Рико", "Реюньон", "Румыния", "Сербия", "Сирия", "Словения",
        "Таджикистан", "Таиланд", "Тайвань", "Украина", "Финляндия",
        "Франция", "Хорватия", "Чехия", "Чехословакия", "Чили", "Швейцария",
        "Швеция", "Эстония", "ЮАР", "Югославия", "Япония",
    )

    private val COUNTRY_KEYS = arrayOf(
        "russia", "usa", "ussr", "australia", "austria", "argentina",
        "belarus", "belgium", "bulgaria", "brazil", "uk",
        "hungary", "west_germany", "hong_kong", "greece", "denmark", "egypt",
        "kazakhstan", "cambodia", "china", "colombia", "congo",
        "south_korea", "latvia", "lithuania", "luxembourg", "macedonia",
        "malaysia", "morocco", "mexico", "netherlands", "new_zealand",
        "norway", "uae", "isle_of_man", "pakistan", "poland", "portugal",
        "puerto_rico", "reunion", "romania", "serbia", "syria", "slovenia",
        "tajikistan", "thailand", "taiwan", "ukraine", "finland",
        "france", "croatia", "czech_republic", "czechoslovakia", "chile",
        "switzerland", "sweden", "estonia", "south_africa", "yugoslavia",
        "japan",
    )

    private val YEAR_NAMES = arrayOf("Нет") + (2025 downTo 1950).map { it.toString() }.toTypedArray()

    private val YEAR_VALUES = (2025 downTo 1950).map { it.toString() }.toTypedArray()
}
