package com.cloudforge.common.api;

import java.util.Set;

import com.cloudforge.common.error.InvalidRequestException;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Restricts client-supplied sorting to an explicit list of properties per endpoint. Without this a
 * client could sort on any entity attribute (for example a password hash, which leaks information
 * through ordering) or on unindexed columns.
 */
public final class SortablePageRequest {

    private SortablePageRequest() {
    }

    public static Pageable restrict(Pageable pageable, Set<String> allowedProperties) {
        for (Sort.Order order : pageable.getSort()) {
            if (!allowedProperties.contains(order.getProperty())) {
                throw new InvalidRequestException("UNSUPPORTED_SORT",
                        "Sorting by '" + order.getProperty() + "' is not supported. Allowed: " + allowedProperties);
            }
        }
        return pageable;
    }
}
