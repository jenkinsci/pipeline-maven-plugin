package jenkins.scm.impl.mock;

import edu.umd.cs.findbugs.annotations.NonNull;
import jenkins.plugins.git.GitSampleRepoRule;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.EnumSet;

import static java.nio.file.FileVisitResult.*;

/**
 * @author <a href="mailto:cleclerc@cloudbees.com">Cyrille Le Clerc</a>
 */
public class GitSampleRepoRuleUtils {

    /**
     * Copy the files of the given {@code rootFolder} into the given {@link AbstractSampleDVCSRepoRule}.
     *
     * @param rootFolder         root folder of the files to add
     * @param sampleDVCSRepoRule target sample repo
     * @throws IOException exception during the copy
     */
    public static void addFilesAndCommit(@NonNull Path rootFolder, @NonNull GitSampleRepoRule sampleDVCSRepoRule) throws IOException {

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
                        Path targetDir = target.resolve(source.relativize(dir));
                        try {
                            Files.copy(dir, targetDir);
                        } catch (FileAlreadyExistsException e) {
                            if (!Files.isDirectory(targetDir))
                                throw e;
                        }
                        return CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        System.out.println("copy " + file + " to " + source.relativize(file) + " / target=" + target);
                        Files.copy(file, target.resolve(source.relativize(file)));
                        return CONTINUE;
                    }
                });

        try {
            sampleDVCSRepoRule.git("add", "--all");
            sampleDVCSRepoRule.git("commit", "--message=addfiles");
        } catch (Exception e) {
            throw new IOException("Exception executing 'git commit' adding " + source, e);
        }

    }
}
