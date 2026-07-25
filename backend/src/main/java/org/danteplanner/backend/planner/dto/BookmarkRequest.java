package org.danteplanner.backend.planner.dto;

/**
 * Request DTO for the bookmark endpoint.
 *
 * <p>Names the desired bookmark state. A request that omits {@code bookmarked} is the legacy toggle
 * shape kept for tabs running a previously cached bundle.</p>
 *
 * @param bookmarked the desired bookmark state; absent means the legacy toggle
 */
public record BookmarkRequest(Boolean bookmarked) {

    /**
     * Whether the request names an explicit bookmark state rather than asking for a toggle.
     */
    public boolean namesState() {
        return bookmarked != null;
    }
}
