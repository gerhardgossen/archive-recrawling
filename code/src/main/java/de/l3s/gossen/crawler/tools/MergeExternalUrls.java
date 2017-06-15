package de.l3s.gossen.crawler.tools;

import java.io.File;
import java.io.IOException;
import java.util.List;

import com.google.common.io.Files;

import de.l3s.gossen.crawler.ArchiveCrawlSpecification;

import static java.nio.charset.StandardCharsets.UTF_8;

public class MergeExternalUrls {

    public static void main(String[] args) throws IOException {
        for (String path : args) {
            for (File specFile : new File(path).listFiles((dir, name) -> name.endsWith(".json"))) {
                ArchiveCrawlSpecification spec = ArchiveCrawlSpecification.readFile(specFile);
                String urlsFileName = specFile.getName().replaceFirst("\\.json$", ".txt");
                File urlsFile = new File(specFile.getParentFile(), urlsFileName);
                if (urlsFile.exists()) {
                    List<String> newSeedUrls = Files.readLines(urlsFile, UTF_8);
                    ArchiveCrawlSpecification newSpec = spec.withSeedUrls(newSeedUrls);
                    String newSpecFileName = specFile.getName().replaceFirst("\\.json", "-seeds.json");
                    newSpec.writeFile(new File(specFile.getParentFile(), newSpecFileName));
                    System.out.printf("Updated %s with %d new URLs%n", specFile, newSeedUrls.size());
                }
            }

        }

    }

}
