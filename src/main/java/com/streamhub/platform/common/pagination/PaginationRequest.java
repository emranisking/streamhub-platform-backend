package com.streamhub.platform.common.pagination;

import lombok.Getter;
import lombok.Setter;

/**
 * Common inbound pagination parameters accepted by every list endpoint.
 * Clients may pass either:
 *  - `page` + `limit` directly (first page / simple use), or
 *  - `cursor` (signed token from a previous {@link PageResponse#getNextCursor()})
 *    which takes precedence and is verified via {@link CursorService}.
 */
@Getter
@Setter
public class PaginationRequest {
    private Integer page;
    private Integer limit;
    private String cursor;
}
