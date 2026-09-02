package org.electrifyingaustralia.fieldops.dto.response;

import java.util.List;
import org.springframework.data.domain.Page;

public record PagedResponse<T>(
        List<T> items,
        PageMetadata page
) {
    public static <T> PagedResponse<T> from(Page<T> source) {
        return new PagedResponse<>(
                source.getContent(),
                new PageMetadata(
                        source.getNumber(),
                        source.getSize(),
                        source.getTotalElements(),
                        source.getTotalPages()
                )
        );
    }

    public record PageMetadata(
            int number,
            int size,
            long totalElements,
            int totalPages
    ) {
    }
}
