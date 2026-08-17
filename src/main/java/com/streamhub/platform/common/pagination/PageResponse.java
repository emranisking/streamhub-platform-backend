package com.streamhub.platform.common.pagination;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Uniform pagination envelope returned by every list endpoint in the system.
 * `nextCursor` is an HMAC-signed opaque token (see {@link CursorService})
 * representing the next page/limit pair - clients are expected to send it
 * back verbatim via the `cursor` query parameter instead of hand-crafting
 * `page`/`limit` values, so requests cannot be tampered with in transit.
 */
@Getter
@Builder
@AllArgsConstructor
public class PageResponse<T> {
    private List<T> content;
    private int page;
    private int limit;
    private long totalElements;
    private int totalPages;
    private boolean hasNext;
    private boolean hasPrevious;
    private String nextCursor;

    public static <E, D> PageResponse<D> from(Page<E> page, Function<E, D> mapper, CursorService cursorService) {
        List<D> mapped = page.getContent().stream().map(mapper).toList();
        String nextCursor = page.hasNext()
                ? cursorService.encode(page.getNumber() + 1, page.getSize())
                : null;
        return PageResponse.<D>builder()
                .content(mapped)
                .page(page.getNumber())
                .limit(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .hasNext(page.hasNext())
                .hasPrevious(page.hasPrevious())
                .nextCursor(nextCursor)
                .build();
    }
}
