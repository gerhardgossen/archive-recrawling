package de.l3s.gossen.snapshots;

import java.io.IOException;

import org.archive.io.ArchiveReader;

public interface SnapshotReader {
    /**
     * Open a reader at the given location.
     *
     * The caller is responsible for closing the returned reader.
     *
     * @param location
     *            snapshot location, never null
     * @return reader positioned at the given location
     * @throws IOException
     */
    ArchiveReader open(SnaphotLocation location) throws IOException;
}
