package jenkins.scm.impl.mock;

import com.gargoylesoftware.htmlunit.WebResponse;
import com.gargoylesoftware.htmlunit.util.NameValuePair;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.File;
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
public class GitRepoRule extends AbstractSampleDVCSRepoRule {
    public void git(String... cmds) throws Exception {
        run("git", cmds);
    }

    @Override
    public void init() throws Exception {
        run(true, tmp.getRoot(), "git", "version");
        git("init");
        git("config", "user.name", "Git SampleRepoRule");
        git("config", "user.email", "gits@mplereporule");
    }

    public String getRepositoryAbsolutePath(){
        return sampleRepo.getAbsolutePath();
    }

    public final boolean mkdirs(String rel) throws IOException {
        return new File(this.sampleRepo, rel).mkdirs();
    }

    public void notifyCommit(JenkinsRule r) throws Exception {
        synchronousPolling(r);
        WebResponse webResponse = r.createWebClient().goTo("git/notifyCommit?url=" + bareUrl(), "text/plain").getWebResponse();
        System.out.println(webResponse.getContentAsString());
        for (NameValuePair pair : webResponse.getResponseHeaders()) {
            if (pair.getName().equals("Triggered")) {
                System.out.println("Triggered: " + pair.getValue());
            }
        }
        r.waitUntilNoActivity();
    }

    /**
     * Returns the (full) commit hash of the current {@link Constants#HEAD} of the repository.
     */
    public String head() throws Exception {
        return new RepositoryBuilder().setWorkTree(sampleRepo).build().resolve(Constants.HEAD).name();
    }

    /**
     * Copy the files of the given {@code rootFolder} into the given {@link AbstractSampleDVCSRepoRule}.
     *
     * @param sourceFolder         root folder of the files to add
     * @throws IOException exception during the copy
     */
    public void addFilesAndCommit(@NonNull Path sourceFolder) throws IOException {

        if (!Files.exists(sourceFolder))
            return;
        if (!Files.isDirectory(sourceFolder))
            throw new IllegalArgumentException("Given root file is not a folder: " + sourceFolder);

        if (this.sampleRepo == null)
            throw new IllegalStateException("SampleDVCSRepoRule has not been initialized", new NullPointerException("sampleRepo is null"));

        final Path source = sourceFolder;
        final Path target = this.sampleRepo.toPath();
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
                            Path relativizedPath = source.relativize(file);
                            Files.copy(file, target.resolve(relativizedPath));
                            try {
                                git("add", relativizedPath.toString());
                            } catch (Exception e) {
                                throw new IOException("Exception executing 'git add' on " + target.resolve(relativizedPath), e);
                            }
                        }
                        return CONTINUE;
                    }
                });
        try {
            git("commit", "--allow-empty", "--message=init");
        } catch (Exception e) {
            throw new IOException("Exception executing 'git commit' adding " + sourceFolder, e);
        }
    }
}
