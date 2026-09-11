package eu.kanade.tachiyomi.data.discovery

import tachiyomi.domain.discovery.model.DiscoveryRowType

data class DiscoveryMixQuotas(
    val similar: Int = 10,
    val taste: Int = 8,
    val fresh: Int = 6,
    val source: Int = 6,
) {
    val total: Int get() = similar + taste + fresh + source
}

/**
 * Смешанный поток: round-robin по квотам сигналов (LIKE → TASTE → TREND → SOURCE),
 * внутри сигнала — порядок ряда (скор). Квота пустого сигнала перераспределяется
 * автоматически (round-robin просто пропускает пустой пул), остаток до [total]
 * добивается по убыванию скора из всех сигналов.
 */
internal fun interleaveMix(
    rows: Map<DiscoveryRowType, List<DiscoveryRowItem>>,
    quotas: DiscoveryMixQuotas = DiscoveryMixQuotas(),
    total: Int = quotas.total,
): List<DiscoveryRowItem> {
    val quotaOf = mapOf(
        DiscoveryRowType.LIKE to quotas.similar,
        DiscoveryRowType.TASTE to quotas.taste,
        DiscoveryRowType.TREND to quotas.fresh,
        DiscoveryRowType.SOURCE to quotas.source,
    )
    val pools = DiscoveryRowType.entries.associateWith { type ->
        ArrayDeque(rows[type].orEmpty())
    }
    val out = mutableListOf<DiscoveryRowItem>()
    val seen = mutableSetOf<String>()
    val taken = mutableMapOf<DiscoveryRowType, Int>()
    var progressed = true
    while (out.size < total && progressed) {
        progressed = false
        for (type in DiscoveryRowType.entries) {
            if (out.size >= total) break
            if ((taken[type] ?: 0) >= (quotaOf[type] ?: 0)) continue
            val pool = pools[type] ?: continue
            var candidate: DiscoveryRowItem? = null
            while (pool.isNotEmpty()) {
                val next = pool.removeFirst()
                if (next.cleanTitle !in seen) {
                    candidate = next
                    break
                }
            }
            if (candidate == null) continue
            out += candidate
            seen += candidate.cleanTitle
            taken[type] = (taken[type] ?: 0) + 1
            progressed = true
        }
    }
    if (out.size < total) {
        pools.values.flatten()
            .filterNot { it.cleanTitle in seen }
            .distinctBy { it.cleanTitle }
            .sortedByDescending { it.score }
            .take(total - out.size)
            .forEach { item ->
                out += item
                seen += item.cleanTitle
            }
    }
    return out
}

/**
 * Комбинаторика пересечений: cleanTitle, рекомендованный k разными сидами,
 * получает score = maxScore * (1 + overlapMultiplier * (k - 1)); в reason-payload
 * (seedTitle) остаются до 2 сид-тайтлов. Из каждого сида берётся не больше
 * [perSeedCap] карточек.
 */
internal fun mergeSeedResults(
    perSeed: List<List<DiscoveryRowItem>>,
    perSeedCap: Int = 4,
    overlapMultiplier: Double = 0.5,
): List<DiscoveryRowItem> {
    class Acc(val template: DiscoveryRowItem) {
        var maxScore: Double = template.score
        val seeds = linkedSetOf<String>()
    }

    val byTitle = LinkedHashMap<String, Acc>()
    perSeed.forEach { seedItems ->
        seedItems.take(perSeedCap).forEach { item ->
            val seedTitle = item.seedTitle ?: return@forEach
            val acc = byTitle.getOrPut(item.cleanTitle) { Acc(item) }
            acc.maxScore = maxOf(acc.maxScore, item.score)
            acc.seeds += seedTitle
        }
    }
    return byTitle.values
        .map { acc ->
            val k = acc.seeds.size
            acc.template.copy(
                score = acc.maxScore * (1.0 + overlapMultiplier * (k - 1)),
                seedTitle = acc.seeds.take(2).joinToString(", "),
            )
        }
        .sortedByDescending { it.score }
}

/**
 * Жанровый профиль: топ-[topN] жанров библиотеки/истории за [windowDays] дней,
 * вес жанра = сумма freshness по тайтлам (freshness = 1 / (1 + возраст_в_днях / 30)).
 */
internal fun buildTasteProfile(
    candidates: List<DiscoverySeedInput>,
    nowMs: Long = System.currentTimeMillis(),
    topN: Int = 6,
    windowDays: Long = 90,
): List<Pair<String, Double>> {
    val windowStart = nowMs - windowDays * DAY_MS
    val weights = mutableMapOf<String, Double>()
    candidates.forEach { candidate ->
        val interaction = (candidate.lastInteraction ?: candidate.dateAdded)
        if (interaction < windowStart) return@forEach
        val ageDays = ((nowMs - interaction) / DAY_MS).coerceAtLeast(0)
        val freshness = 1.0 / (1.0 + ageDays / 30.0)
        candidate.genres.forEach { genre ->
            weights[genre] = (weights[genre] ?: 0.0) + freshness
        }
    }
    return weights.entries
        .sortedByDescending { it.value }
        .take(topN)
        .map { it.key to it.value }
}

/**
 * Lowercase-множество имён жанров плюс RU↔EN переводы (карта
 * [eu.kanade.tachiyomi.data.suggestions.MultilingualQueryHelper.getGenreTranslations]):
 * профиль вкуса строится из жанров библиотеки (часто русских), а теги внешних
 * провайдеров — английские.
 */
internal fun expandGenreSet(genres: List<String>): Set<String> {
    val out = mutableSetOf<String>()
    genres.forEach { genre ->
        val key = genre.trim().lowercase()
        if (key.isEmpty()) return@forEach
        out += key
        eu.kanade.tachiyomi.data.suggestions.MultilingualQueryHelper.getGenreTranslations(genre).forEach { variant ->
            val v = variant.trim().lowercase()
            if (v.isNotEmpty()) out += v
        }
    }
    return out
}

private fun genreVariants(genre: String): Set<String> = expandGenreSet(listOf(genre))

/** Веса профиля, развёрнутые по всем языковым вариантам каждого жанра. */
internal fun expandedTasteWeights(profile: List<Pair<String, Double>>): Map<String, Double> {
    val map = mutableMapOf<String, Double>()
    profile.forEach { (genre, weight) ->
        genreVariants(genre).forEach { variant ->
            map[variant] = maxOf(map[variant] ?: 0.0, weight)
        }
    }
    return map
}

/** Скор кандидата по профилю вкуса: сумма весов совпавших жанров с учётом RU↔EN переводов. */
internal fun tasteScore(itemGenres: List<String>, profile: List<Pair<String, Double>>): Double {
    val weights = expandedTasteWeights(profile)
    return itemGenres.mapTo(HashSet()) { it.trim().lowercase() }
        .sumOf { weights[it] ?: 0.0 }
}

/**
 * Жанры кандидата, совпавшие с профилем (топ-2 для обоснования).
 * Spelling — из профиля (язык библиотеки пользователя), порядок — по жанрам кандидата.
 */
internal fun matchedGenres(itemGenres: List<String>, profile: List<Pair<String, Double>>): List<String> {
    return itemGenres.mapNotNull { itemGenre ->
        val key = itemGenre.trim().lowercase()
        if (key.isEmpty()) return@mapNotNull null
        profile.firstOrNull { (genre, _) -> key in genreVariants(genre) }?.first
    }.distinct().take(2)
}

private const val DAY_MS = 24L * 60 * 60 * 1000

/**
 * Кросс-рядовой дедуп по cleanTitle: приоритет = [DiscoveryRowType.ordinal]
 * (LIKE > TASTE > TREND > SOURCE). Упавший ряд сохраняет устаревший кэш, и тот же
 * тайтл может появиться в свежем ряду другого типа — оставляем копию приоритетного ряда.
 * Сортировка стабильна: порядок position внутри ряда сохраняется.
 */
internal fun dedupeCrossRow(
    items: List<tachiyomi.domain.discovery.model.DiscoverySuggestion>,
): List<tachiyomi.domain.discovery.model.DiscoverySuggestion> = items
    .sortedBy { it.rowType.ordinal }
    .distinctBy { it.cleanTitle }
