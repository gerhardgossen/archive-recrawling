package de.l3s.gossen.snapshots;

import java.time.ZonedDateTime;
import java.util.Optional;

public interface SnapshotsLocator {

    Iterable<SnaphotLocation> findLocations(String url);

    Optional<SnaphotLocation> findLocation(String url, ZonedDateTime crawlTime);
}
