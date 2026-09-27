package eu.kanade.tachiyomi.animeextension.all.noodlemagazine

import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList

/**
 * Сайт не имеет каталога категорий: разделы — это подборки по времени
 * (`/home?range=…`), а «жанры» — обычные поисковые запросы вида
 * `/video/<теги+через+плюс>`. Поэтому жанры сделаны CheckBox-группой:
 * отмеченные значения склеиваются в один запрос, и их можно комбинировать
 * с введённым текстом.
 */
object NoodleMagazineFilters {

    class CheckBoxVal(name: String, state: Boolean = false) : AnimeFilter.CheckBox(name, state)

    open class CheckBoxGroup(name: String, values: List<CheckBoxVal>) : AnimeFilter.Group<AnimeFilter.CheckBox>(name, values)

    class SectionFilter : AnimeFilter.Select<String>("Раздел", SECTIONS.map { it.first }.toTypedArray(), 0)
    class SortFilter : AnimeFilter.Select<String>("Сортировка поиска", SORTS.map { it.first }.toTypedArray(), 0)
    class DurationFilter : AnimeFilter.Select<String>("Длительность", DURATIONS.map { it.first }.toTypedArray(), 0)
    class HdFilter : AnimeFilter.CheckBox("Только HD", false)

    class TagFilter : CheckBoxGroup("Жанры и теги", TAGS.map { CheckBoxVal(it) })

    class ExtraTagsFilter : AnimeFilter.Text("Свои теги (через запятую)")

    val FILTER_LIST
        get() = AnimeFilterList(
            AnimeFilter.Header("Раздел работает, когда поиск и теги пусты"),
            SectionFilter(),
            AnimeFilter.Separator(),
            AnimeFilter.Header("Теги можно отмечать по нескольку — они ищутся вместе"),
            TagFilter(),
            ExtraTagsFilter(),
            AnimeFilter.Separator(),
            SortFilter(),
            DurationFilter(),
            HdFilter(),
        )

    data class SearchParams(
        val section: String = SECTION_DEFAULT,
        val tags: List<String> = emptyList(),
        val sort: String = "",
        val duration: String = "",
        val onlyHd: Boolean = false,
    )

    private inline fun <reified R> AnimeFilterList.firstOrNullAs(): R? = filterIsInstance<R>().firstOrNull()

    fun getSearchParameters(filters: AnimeFilterList): SearchParams {
        if (filters.isEmpty()) return SearchParams()

        fun pick(state: Int, values: List<Pair<String, String>>) = values.getOrElse(state) { values[0] }.second

        val checked = filters.firstOrNullAs<TagFilter>()
            ?.state
            ?.filter { it.state }
            ?.map { it.name }
            .orEmpty()

        val extra = filters.firstOrNullAs<ExtraTagsFilter>()
            ?.state
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            .orEmpty()

        return SearchParams(
            section = pick(filters.firstOrNullAs<SectionFilter>()?.state ?: 0, SECTIONS),
            tags = (checked + extra).distinct(),
            sort = pick(filters.firstOrNullAs<SortFilter>()?.state ?: 0, SORTS),
            duration = pick(filters.firstOrNullAs<DurationFilter>()?.state ?: 0, DURATIONS),
            onlyHd = filters.firstOrNullAs<HdFilter>()?.state == true,
        )
    }

    const val SECTION_DEFAULT = "/home?range=month"

    private val SECTIONS = listOf(
        "Популярное за месяц" to SECTION_DEFAULT,
        "Популярное за неделю" to "/home?range=week",
        "Популярное за день" to "/home?range=day",
        "Недавние" to "/home?range=recent",
        "Обзор (Explore)" to "/home?range=explore",
        "В тренде" to "/popular/trending",
        "Смотрят сейчас" to "/now",
    )

    /** Значения параметра `sort` из фильтров сайта. */
    private val SORTS = listOf(
        "По релевантности" to "",
        "По длительности" to "1",
        "По дате добавления" to "0",
    )

    private val DURATIONS = listOf(
        "Любая" to "",
        "Длинные" to "long",
        "Короткие" to "short",
    )

    /**
     * Теги-подборки, по которым сайт реально отдаёт выдачу (проверено
     * 2026-09-27). Это те же поисковые запросы, что и в облаке тегов сайта.
     */
    private val TAGS = listOf(
        "anime",
        "hentai",
        "хентай",
        "uncensored",
        "3d",
        "cosplay",
        "asian",
        "japanese",
        "korean",
        "chinese",
        "amateur",
        "milf",
        "teen",
        "lesbian",
        "yuri",
        "yaoi",
        "futanari",
        "big tits",
        "big ass",
        "small tits",
        "blowjob",
        "handjob",
        "anal",
        "creampie",
        "cumshot",
        "threesome",
        "group",
        "pov",
        "massage",
        "stockings",
        "maid",
        "school",
        "nurse",
        "office",
        "public",
        "bdsm",
        "femdom",
        "incest",
        "vintage",
        "compilation",
        "solo",
        "masturbation",
        "squirt",
        "webcam",
        "casting",
        "interracial",
        "русское",
        "порно",
        "секс",
        "домашнее",
    )
}
