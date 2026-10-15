package com.bunnycode.battle.api;

import com.bunnycode.battle.application.BattleQueryService;
import com.bunnycode.battle.application.BattleQueryService.SearchField;
import com.bunnycode.battle.application.BattleViews.BattleDetailView;
import com.bunnycode.battle.application.BattleViews.BattleSummaryView;
import com.bunnycode.battle.domain.BattleStatus;
import com.bunnycode.battle.domain.QuestionLevel;
import com.bunnycode.shared.validation.ResourceName;
import com.bunnycode.shared.web.PageResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.Set;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/battles")
class BattleController {

    private final BattleQueryService queries;

    BattleController(BattleQueryService queries) {
        this.queries = queries;
    }

    /**
     * @param status any of WAITING, RUNNING, FINISHED, TERMINATED (repeat or comma-separate);
     *               omitted = all but TERMINATED
     * @param level  EASY, MEDIUM or HARD; omitted = any
     */
    @GetMapping
    PageResponse<BattleSummaryView> list(
            @RequestParam(required = false) Set<BattleStatus> status,
            @RequestParam(required = false) QuestionLevel level,
            @RequestParam(required = false) @Size(max = 50) String keyword,
            @RequestParam(defaultValue = "NAME") SearchField searchBy,
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(defaultValue = "6") @Min(1) @Max(50) int size) {
        return queries.list(status, level, keyword, searchBy, page, size);
    }

    /** Lets the launch form check a name before inviting (v1 answered "taken" with a 400). */
    @GetMapping("/name-availability")
    NameAvailability nameAvailability(@RequestParam @ResourceName String name) {
        return new NameAvailability(name, queries.isNameAvailable(name));
    }

    @GetMapping("/{battleId}")
    BattleDetailView detail(@PathVariable @Positive long battleId) {
        return queries.detail(battleId);
    }

    record NameAvailability(String name, boolean available) {
    }
}
