package com.bunnycode.battle.api;

import com.bunnycode.battle.application.BattleQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Headline numbers for the home page. Each module publishes its own under {@code /api/v2/stats}. */
@RestController
class BattleStatsController {

    private final BattleQueryService queries;

    BattleStatsController(BattleQueryService queries) {
        this.queries = queries;
    }

    @GetMapping("/api/v2/stats/battles")
    BattleStats stats() {
        return new BattleStats(queries.countListedBattles());
    }

    record BattleStats(long battles) {
    }
}
