package me.qoomon.gitversioning.commons;


import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.jgit.lib.Constants.HEAD;
import static org.eclipse.jgit.lib.Constants.MASTER;

class GitUtilTest {

    @TempDir
    Path tempDir;

    @Test
    void status_clean() throws GitAPIException {

        // given
        Git git = Git.init().setInitialBranch(MASTER).setDirectory(tempDir.toFile()).call();

        // when
        Status status = GitUtil.status(git.getRepository());

        // then
        assertThat(status.isClean()).isTrue();
    }

    @Test
    void status_dirty() throws GitAPIException, IOException {

        // given
        Git git = Git.init().setInitialBranch(MASTER).setDirectory(tempDir.toFile()).call();

        boolean dummyFileCreated = new File(tempDir.toFile(), "README.md").createNewFile();
        assertThat(dummyFileCreated).isTrue();

        // when
        Status status = GitUtil.status(git.getRepository());

        // then
        assertThat(status.isClean()).isFalse();
    }

    @Test
    void branch_emptyRepo() throws GitAPIException, IOException {

        // given
        Git git = Git.init().setInitialBranch(MASTER).setDirectory(tempDir.toFile()).call();

        // when
        String branch = GitUtil.branch(git.getRepository());

        // then
        assertThat(branch).isEqualTo(MASTER);
    }

    @Test
    void branch_nonEmptyRepo() throws GitAPIException, IOException {

        // given
        Git git = Git.init().setInitialBranch(MASTER).setDirectory(tempDir.toFile()).call();
        RevCommit givenCommit = git.commit().setMessage("initial commit").setAllowEmpty(true).call();
        String givenBranchName = "feature";
        git.branchCreate().setName(givenBranchName).setStartPoint(givenCommit).call();
        git.checkout().setName(givenBranchName).call();

        // when
        String branch = GitUtil.branch(git.getRepository());

        // then
        assertThat(branch).isEqualTo(givenBranchName);
    }

    @Test
    void tagsPointAt_emptyRepo() throws GitAPIException, IOException {

        // given
        Git git = Git.init().setInitialBranch(MASTER).setDirectory(tempDir.toFile()).call();
        // when

        List<String> tags = GitUtil.tagsPointAt(head(git), git.getRepository());

        // then
        assertThat(tags).isEmpty();
    }

    @Test
    void tagsPointAt_noTags() throws GitAPIException, IOException {

        // given
        Git git = Git.init().setInitialBranch(MASTER).setDirectory(tempDir.toFile()).call();

        git.commit().setMessage("initial commit").setAllowEmpty(true).call();

        // when
        List<String> tags = GitUtil.tagsPointAt(head(git), git.getRepository());

        // then
        assertThat(tags).isEmpty();
    }

    @Test
    void tagsPointAt_oneTag() throws GitAPIException, IOException {

        // given
        Git git = Git.init().setInitialBranch(MASTER).setDirectory(tempDir.toFile()).call();

        RevCommit givenCommit = git.commit().setMessage("initial commit").setAllowEmpty(true).call();
        String givenTagName = "v1.0.0";
        git.tag().setName(givenTagName).setObjectId(givenCommit).call();

        // when
        List<String> tags = GitUtil.tagsPointAt(head(git), git.getRepository());

        // then
        assertThat(tags).containsExactly(givenTagName);
    }

    @Test
    void tagsPointAt_multipleTags() throws GitAPIException, IOException {

        // given
        Git git = Git.init().setInitialBranch(MASTER).setDirectory(tempDir.toFile()).call();

        RevCommit givenCommit = git.commit().setMessage("initial commit").setAllowEmpty(true).call();

        String givenTagName1 = "v21";
        git.tag().setName(givenTagName1).setObjectId(givenCommit).call();
        String givenTagName2 = "v1";
        git.tag().setName(givenTagName2).setObjectId(givenCommit).call();
        String givenTagName3 = "v2.1";
        git.tag().setName(givenTagName3).setObjectId(givenCommit).call();

        // when
        List<String> tags = GitUtil.tagsPointAt(head(git), git.getRepository());

        // then
        assertThat(tags).containsExactly(givenTagName2, givenTagName3, givenTagName1);
    }

    @Test
    void tagsPointAt_lightweightTag() throws GitAPIException, IOException {

        // given
        Git git = Git.init().setInitialBranch(MASTER).setDirectory(tempDir.toFile()).call();

        RevCommit givenCommit = git.commit().setMessage("initial commit").setAllowEmpty(true).call();

        String givenTagName = "v1.0.0-1234";
        git.tag().setName(givenTagName).setAnnotated(false).setObjectId(givenCommit).call();

        // when
        List<String> tags = GitUtil.tagsPointAt(head(git), git.getRepository());

        // then
        assertThat(tags).containsExactlyInAnyOrder(givenTagName);
    }

    private static ObjectId head(Git git) throws IOException {
        return git.getRepository().resolve(HEAD);
    }

    @Test
    void describe() throws Exception {
        // given
        Git git = Git.init().setInitialBranch(MASTER).setDirectory(tempDir.toFile()).call();

        RevCommit givenCommit = git.commit().setMessage("initial commit").setAllowEmpty(true).call();

        git.tag().setName("v2.1").setAnnotated(true).setObjectId(givenCommit).setMessage(".").call();
        git.tag().setName("v1.2").setAnnotated(true).setObjectId(givenCommit).setMessage(".").call();
        git.tag().setName("v2.3").setAnnotated(true).setObjectId(givenCommit).setMessage(".").call();
        Thread.sleep(1000);
        String givenTagName = "v1.3";
        git.tag().setName(givenTagName).setAnnotated(true).setObjectId(givenCommit).setMessage(".").call();

        // when
        GitDescription description = GitUtil.describe(head(git), Pattern.compile("v.+"), git.getRepository(), true);

        // then
        assertThat(description).satisfies(it -> {
            assertThat(it.getCommit()).isEqualTo(givenCommit.getName());
            assertThat(it.getDistance()).isEqualTo(0);
            assertThat(it.getTag()).isEqualTo(givenTagName);
        });
    }

}
