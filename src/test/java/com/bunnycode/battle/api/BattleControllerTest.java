package com.bunnycode.battle.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;

import com.bunnycode.battle.application.BattleQueryService;
import com.bunnycode.battle.application.BattleQueryService.SearchField;
import com.bunnycode.battle.domain.BattleStatus;
import com.bunnycode.battle.domain.QuestionLevel;
import java.util.Set;
import com.bunnycode.shared.security.WebSecurityTestConfiguration;
import com.bunnycode.shared.web.PageResponse;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(BattleController.class)
@ActiveProfiles("test")
@Import(WebSecurityTestConfiguration.class)
@DisplayName("Battle HTTP API")
class BattleControllerTest {

    @Autowired
    private MockMvcTester mvc;
    @MockitoBean
    private BattleQueryService queries;

    @Test
    @DisplayName("the lobby is public; with no filters every status and level is asked for, searched by name")
    void lobby() {
        given(queries.list(isNull(), isNull(), isNull(), eq(SearchField.NAME), anyInt(), anyInt()))
                .willReturn(new PageResponse<>(List.of(), 0, 6, 0, 0));

        assertThat(mvc.get().uri("/api/v2/battles")).hasStatusOk();
    }

    @Test
    @DisplayName("status (comma-separated) and level filters bind to their enums")
    void filters() {
        given(queries.list(eq(Set.of(BattleStatus.RUNNING, BattleStatus.WAITING)), eq(QuestionLevel.HARD), isNull(),
                eq(SearchField.NAME), anyInt(), anyInt()))
                .willReturn(new PageResponse<>(List.of(), 0, 6, 0, 0));

        assertThat(mvc.get().uri("/api/v2/battles?status=RUNNING,WAITING&level=HARD")).hasStatusOk();
        assertThat(mvc.get().uri("/api/v2/battles?level=IMPOSSIBLE")).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("name availability answers true/false instead of v1's 400-for-taken")
    void nameAvailability() {
        given(queries.isNameAvailable("duel")).willReturn(false);

        assertThat(mvc.get().uri("/api/v2/battles/name-availability?name=duel")).hasStatusOk()
                .bodyJson().extractingPath("$.available").isEqualTo(false);
        assertThat(mvc.get().uri("/api/v2/battles/name-availability?name=bad name!")).hasStatus(HttpStatus.BAD_REQUEST);
    }
}
