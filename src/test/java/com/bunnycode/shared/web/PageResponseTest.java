package com.bunnycode.shared.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@DisplayName("PageResponse")
class PageResponseTest {

    @Test
    @DisplayName("Given a Spring page, when converted, then page numbers stay zero-based and totals are kept")
    void convertsPage() {
        var page = new PageImpl<>(List.of("a", "b"), PageRequest.of(1, 2), 5);

        PageResponse<String> response = PageResponse.of(page);

        assertThat(response.content()).containsExactly("a", "b");
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(2);
        assertThat(response.totalElements()).isEqualTo(5);
        assertThat(response.totalPages()).isEqualTo(3);
    }

    @Test
    @DisplayName("When mapped, then content is transformed and paging metadata is preserved")
    void maps() {
        var response = new PageResponse<>(List.of(1, 2), 0, 2, 2, 1).map(n -> n * 10);

        assertThat(response).isEqualTo(new PageResponse<>(List.of(10, 20), 0, 2, 2, 1));
    }

    @Test
    @DisplayName("Content is defensively copied and immutable")
    void isImmutable() {
        var source = new ArrayList<>(List.of("a"));
        var response = new PageResponse<>(source, 0, 1, 1, 1);
        source.add("b");

        assertThat(response.content()).containsExactly("a");
        assertThatThrownBy(() -> response.content().add("c")).isInstanceOf(UnsupportedOperationException.class);
    }
}
