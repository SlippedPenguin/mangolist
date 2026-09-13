package com.slippedpenguin.mangolist.data

import com.slippedpenguin.mangolist.data.local.AnimeEntry

/*
 * v1.7: head-to-head ranking session reducer.
 *
 * Revives the README's original vision in lightweight form: the user is
 * shown two titles, taps the one they liked more, and the next pair appears.
 * Each judgment runs through the real EloEngine (K=32, clamp 900–2100):
 *   - winner gets EloEngine.update(winnerElo, loserElo).newWinner when the
 *     candidate wins, newLoser when the opponent wins;
 *   - the candidate then lands in EloEngine.proposeTier(newElo, ranked).
 *
 * Opponents are chosen by EloEngine.pickOpponent — the already-ranked entry
 * whose Elo is closest to the candidate's, so every tap is a genuinely
 * informative comparison instead of a random one.
 *
 * Pure Kotlin (no Android/Compose imports) so the whole session lifecycle —
 * start, choose, skip, exhaustion — is unit-testable in CI. The composable
 * renders `candidate` vs `opponent` and persists `committedRows` verbatim.
 *
 * updatedAt is NEVER touched: tier/elo assignments are local-only and must
 * not flag no-op pushes to AniList (same rule as TierListModel).
 */
class RankSession private constructor(
    private val pool: List<AnimeEntry>,
    private val ranked: List<AnimeEntry>,
    private val committed: List<AnimeEntry>,
    private val judged: Int,
    val candidate: AnimeEntry?,
    val opponent: AnimeEntry?,
) {
    /** Candidates processed so far (chosen, lost, or auto-landed) — UI progress. */
    val judgedCount: Int get() = judged
    val remainingCount: Int get() = pool.size
    val isDone: Boolean get() = candidate == null
    /** Rows produced so far this session — UI persists them via dao.update. */
    val committedRows: List<AnimeEntry> get() = committed

    /**
     * Seed a session from one DAO read. `ranked` = entries with a tier,
     * `unranked` = tier == null (optionally pre-filtered by the UI, e.g.
     * only unscored titles). Pool order = unranked order; the first candidate
     * faces the closest-Elo ranked title. A candidate with no possible
     * opponent (nothing ranked yet) commits immediately at INITIAL_ELO —
     * proposeTier falls back to "B" — because there is nothing to compare.
     */
    companion object {
        fun start(ranked: List<AnimeEntry>, unranked: List<AnimeEntry>): RankSession {
            val session = RankSession(
                pool = unranked,
                ranked = ranked,
                committed = emptyList(),
                judged = 0,
                candidate = null,
                opponent = null,
            )
            return session.advance()
        }
    }

    /**
     * Record one judgment: `candidateWins = true` means the candidate beat
     * the opponent. Advances to the next pair. No-op when the session is done.
     */
    fun choose(candidateWins: Boolean): RankSession {
        val cand = candidate ?: return this
        val opp = opponent ?: return this
        val update = if (candidateWins) {
            EloEngine.update(winnerElo = cand.elo, loserElo = opp.elo)
        } else {
            EloEngine.update(winnerElo = opp.elo, loserElo = cand.elo)
        }
        val newElo = if (candidateWins) update.newWinner else update.newLoser
        // The opponent's Elo also moves in the real engine; the opponent row
        // is updated in the committed set so its display order tracks reality.
        val updatedOpponent = opp.copy(elo = if (candidateWins) update.newLoser else update.newWinner)

        // tierRank stays null: h2h assignments order by the score/elo default
        // inside their tier, exactly like one-tap ranking in v1.5.1.
        // The proposal sees the opponent's UPDATED elo but not the candidate
        // itself (proposing from a population that already contains the
        // candidate would be self-referential).
        val rankedWithOpponentUpdate = ranked.map {
            if (it.anilistId == opp.anilistId) updatedOpponent else it
        }
        val landed = cand.copy(
            tier = EloEngine.proposeTier(newElo, rankedWithOpponentUpdate),
            elo = newElo,
        )
        val nextRanked = rankedWithOpponentUpdate + landed
        return RankSession(
            pool = pool.drop(1),
            ranked = nextRanked,
            committed = committed + listOf(landed, updatedOpponent),
            judged = judged + 1,
            candidate = null,
            opponent = null,
        ).advance()
    }

    /** Defer the current candidate to the end of the pool (can't decide right now). */
    fun skip(): RankSession {
        val cand = candidate ?: return this
        if (pool.size < 2) return this // nothing to defer past — session stays put
        val reordered = pool.drop(1) + cand
        return RankSession(
            pool = reordered,
            ranked = ranked,
            committed = committed,
            judged = judged,
            candidate = null,
            opponent = null,
        ).advance()
    }

    private fun advance(): RankSession {
        val next = pool.firstOrNull() ?: return done()
        val opp = EloEngine.pickOpponent(next.elo, ranked, excludeIds = emptySet())
        return if (opp == null) {
            // Nothing ranked to compare against: land immediately at the
            // proposeTier fallback (B) with INITIAL_ELO.
            val landed = next.copy(tier = EloEngine.proposeTier(next.elo, ranked), elo = next.elo)
            RankSession(
                pool = pool.drop(1),
                ranked = ranked + landed,
                committed = committed + landed,
                judged = judged + 1,
                candidate = null,
                opponent = null,
            ).advance()
        } else {
            RankSession(
                pool = pool,
                ranked = ranked,
                committed = committed,
                judged = judged,
                candidate = next,
                opponent = opp,
            )
        }
    }

    private fun done(): RankSession =
        RankSession(pool, ranked, committed, judged, candidate = null, opponent = null)
}
