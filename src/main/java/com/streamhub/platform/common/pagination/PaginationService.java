package com.streamhub.platform.common.pagination;

import com.streamhub.platform.common.exception.BadRequestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

/**
 * Base pagination service used identically by every module (video, playlist,
 * watch history, likes, analytics, etc). Resolves the effective page/limit
 * from either raw query params or a signed cursor, and clamps limit to the
 * configured maximum to protect the database from abusive requests.
 */
@Service
public class PaginationService {

    private final CursorService cursorService;
    private final int defaultPage;
    private final int defaultLimit;
    private final int maxLimit;

    public PaginationService(CursorService cursorService,
                              @Value("${app.pagination.default-page}") int defaultPage,
                              @Value("${app.pagination.default-limit}") int defaultLimit,
                              @Value("${app.pagination.max-limit}") int maxLimit) {
        this.cursorService = cursorService;
        this.defaultPage = defaultPage;
        this.defaultLimit = defaultLimit;
        this.maxLimit = maxLimit;
    }

    public Pageable resolve(PaginationRequest request) {
        return resolve(request, Sort.unsorted());
    }

    public Pageable resolve(PaginationRequest request, Sort sort) {
        int page = defaultPage;
        int limit = defaultLimit;

        if (request.getCursor() != null && !request.getCursor().isBlank()) {
            CursorService.DecodedCursor decoded = cursorService.decode(request.getCursor());
            page = decoded.page();
            limit = decoded.limit();
        } else {
            if (request.getPage() != null) {
                page = request.getPage();
            }
            if (request.getLimit() != null) {
                limit = request.getLimit();
            }
        }

        if (page < 0) {
            throw new BadRequestException("page must be >= 0");
        }
        if (limit < 1) {
            throw new BadRequestException("limit must be >= 1");
        }
        if (limit > maxLimit) {
            limit = maxLimit;
        }

        return sort.isSorted() ? PageRequest.of(page, limit, sort) : PageRequest.of(page, limit);
    }

    public CursorService getCursorService() {
        return cursorService;
    }
}
