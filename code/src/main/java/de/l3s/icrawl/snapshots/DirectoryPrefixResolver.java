package de.l3s.icrawl.snapshots;

import org.apache.hadoop.fs.Path;

public class DirectoryPrefixResolver implements LocationResolver {

    private final Path rootPath;

    public DirectoryPrefixResolver(String rootPath) {
        this.rootPath = new Path(rootPath);
    }

    @Override
    public SnaphotLocation resolve(SnaphotLocation location) {
        String warcFile = location.getWarcFile();
        int pos = warcFile.indexOf("-");
        Path directory = rootPath;
        if (pos >= 0) {
            directory = new Path(rootPath, warcFile.substring(0, pos));
        }

        return location.withWarcFile(new Path(directory, warcFile).toString());
    }

}
