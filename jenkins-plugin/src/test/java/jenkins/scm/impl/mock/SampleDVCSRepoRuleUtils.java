package jenkins.scm.impl.mock;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.FilePath;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.EnumSet;

import static java.nio.file.FileVisitResult.CONTINUE;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public class SampleDVCSRepoRuleUtils {

    /**
     * Copy the files of the given {@code rootFolder} into the given {@link AbstractSampleDVCSRepoRule}.
     *
     * @param rootFolder         root folder of the files to add
     * @param sampleDVCSRepoRule target sample repo
     * @throws IOException exception during the copy
     */
    public static void addFiles(@NonNull Path rootFolder, @NonNull AbstractSampleDVCSRepoRule sampleDVCSRepoRule) throws IOException {

        if (!Files.exists(rootFolder))
            return;
        if (!Files.isDirectory(rootFolder))
            throw new IllegalArgumentException("Given root file is not a folder: " + rootFolder);

        if (sampleDVCSRepoRule.sampleRepo == null)
            throw new IllegalStateException("SampleDVCSRepoRule has not been initialized", new NullPointerException("sampleRepo is null"));

        final Path source = rootFolder;
        final Path target = sampleDVCSRepoRule.sampleRepo.toPath();
        Files.walkFileTree(source, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE,
                new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                        Path targetdir = target.resolve(source.relativize(dir));
                        try {
                            Files.copy(dir, targetdir);
                        } catch (FileAlreadyExistsException e) {
                            if (!Files.isDirectory(targetdir))
                                throw e;
                        }
                        return CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        if (file.toFile().getName().startsWith(".")) {
                            // skip files starting with "."
                        } else {
                            Files.copy(file, target.resolve(source.relativize(file)));
                        }
                        return CONTINUE;
                    }
                });


    }
}
