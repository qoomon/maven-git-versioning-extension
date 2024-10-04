package me.qoomon.gitversioning.commons;


import org.assertj.core.api.SoftAssertions;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeCommand;
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
        GitDescription description = GitUtil.describe(head(git), Pattern.compile("v.+"), git.getRepository(), true, -1);

        // then
        assertThat(description).satisfies(it -> {
            assertThat(it.getCommit()).isEqualTo(givenCommit.getName());
            assertThat(it.getDistance()).isZero();
            assertThat(it.getDistanceOrZero()).isZero();
            assertThat(it.getTag()).isEqualTo(givenTagName);
        });
    }

    @Test
    void distanceOrZeroIsZeroWhenNoTagMatches() throws Exception {
        // given
        Git git = Git.init().setInitialBranch(MASTER).setDirectory(tempDir.toFile()).call();
        git.commit().setMessage("initial commit").setAllowEmpty(true).call();

        final var softly = new SoftAssertions();
        for (int i = 0; i < 3; ++i) {
            GitDescription description = GitUtil.describe(head(git), Pattern.compile("v.+"), git.getRepository(), true, -1);
            softly.assertThat(description.isTagFound()).as("isTagFound " + i).isFalse();
            softly.assertThat(description.getDistanceOrZero()).as("distanceOrZero " + i).isZero();
            softly.assertThat(description.getDistance()).as("distance " + i).isEqualTo(i);
            git.commit().setMessage("commit " + (i + 1)).setAllowEmpty(true).call();
        }
        softly.assertAll();
    }

    @Test
    void distanceWithMaxDepth() throws Exception {
        // given
        final int maxDepth = 4;
        Git git = Git.init().setInitialBranch(MASTER).setDirectory(tempDir.toFile()).call();
        final RevCommit firstCommit = git.commit().setMessage("initial commit").setAllowEmpty(true).call();
        git.tag().setName("v1.0.0").setAnnotated(true).setObjectId(firstCommit).setMessage(".").call();

        final SoftAssertions softly = new SoftAssertions();
        for (int i = 0; i < 6; ++i) {
            GitDescription description = GitUtil.describe(head(git), Pattern.compile("v.+"), git.getRepository(), true, maxDepth);
            softly.assertThat(description.isTagFound()).as("distanceOrZero " + i).isEqualTo(i <= maxDepth);
            softly.assertThat(description.getDistanceOrZero()).as("distanceOrZero " + i).isEqualTo(i > maxDepth ? 0 : i);
            softly.assertThat(description.getDistance()).as("distance " + i).isEqualTo(Math.min(i, maxDepth));
            git.commit().setMessage("commit " + (i + 1)).setAllowEmpty(true).call();
        }
        softly.assertAll();
    }

    @Test
    void distanceWithManyParents() throws Exception {
        // give
        Git git = Git.init().setInitialBranch("A").setDirectory(tempDir.toFile()).call();
        final RevCommit firstCommit = git.commit().setMessage("initial commit").setAllowEmpty(true).call();
        git.tag().setName("v1.0.0").setAnnotated(true).setObjectId(firstCommit).setMessage(".").call();
        final RevCommit commitBranchAWithTag = git.commit().setMessage("commit branch 1 with tag").setAllowEmpty(true).call();
        git.tag().setName("v1.1.0").setAnnotated(true).setObjectId(commitBranchAWithTag).setMessage(".").call();
        for (int i = 0; i < 10; ++i) {
            git.commit().setMessage("commit branch A " + (i + 1)).setAllowEmpty(true).call();
        }

        git.checkout().setCreateBranch(true).setName("B").setStartPoint(firstCommit).call();
        final RevCommit commitBranchBWithTag = git.commit().setMessage("commit branch B with tag").setAllowEmpty(true).call();
        git.tag().setName("v1.2.0").setAnnotated(true).setObjectId(commitBranchBWithTag).setMessage(".").call();
        for (int i = 0; i < 5; ++i) {
            git.commit().setMessage("commit branch B " + (i + 1)).setAllowEmpty(true).call();
        }
        git.merge().include(git.getRepository().resolve("A")).setFastForward(MergeCommand.FastForwardMode.NO_FF).call();

        // When
        final boolean firstParent = false;
        GitDescription description10 = GitUtil.describe(head(git), Pattern.compile(Pattern.quote("v1.0.0")), git.getRepository(), firstParent, null);
        GitDescription description11 = GitUtil.describe(head(git), Pattern.compile(Pattern.quote("v1.1.0")), git.getRepository(), firstParent, -1);
        GitDescription description12 = GitUtil.describe(head(git), Pattern.compile(Pattern.quote("v1.2.0")), git.getRepository(), firstParent, -1);
        final int maxDepth = 8;
        GitDescription description10Depth = GitUtil.describe(head(git), Pattern.compile(Pattern.quote("v1.0.0")), git.getRepository(), firstParent, maxDepth);
        GitDescription description11Depth = GitUtil.describe(head(git), Pattern.compile(Pattern.quote("v1.1.0")), git.getRepository(), firstParent, maxDepth);
        GitDescription description12Depth = GitUtil.describe(head(git), Pattern.compile(Pattern.quote("v1.2.0")), git.getRepository(), firstParent, maxDepth);

        // Then
        final SoftAssertions softly = new SoftAssertions();
        softly.assertThat(description10.getDistance()).as("v1.0.0 distance").isEqualTo(7);
        softly.assertThat(description10.isTagFound()).as("v1.0.0 tagFound").isTrue();
        softly.assertThat(description11.getDistance()).as("v1.1.0 distance").isEqualTo(11);
        softly.assertThat(description11.isTagFound()).as("v1.1.0 tagFound").isTrue();
        softly.assertThat(description12.getDistance()).as("v1.2.0 distance").isEqualTo(6);
        softly.assertThat(description12.isTagFound()).as("v1.2.0 tagFound").isTrue();
        softly.assertThat(description10Depth.getDistance()).as("v1.0.0 distance with depth").isEqualTo(7);
        softly.assertThat(description10Depth.isTagFound()).as("v1.0.0 tagFound with depth").isTrue();
        softly.assertThat(description11Depth.getDistance()).as("v1.1.0 distance with depth").isEqualTo(8);
        softly.assertThat(description11Depth.isTagFound()).as("v1.1.0 tagFound with depth").isFalse();
        softly.assertThat(description12Depth.getDistance()).as("v1.2.0 distance with depth").isEqualTo(6);
        softly.assertThat(description12Depth.isTagFound()).as("v1.2.0 tagFound with depth").isTrue();
        softly.assertAll();
    }

    @Test
    void distanceWithManyParentsAndFirstParentOnly() throws Exception {
        // give
        Git git = Git.init().setInitialBranch("A").setDirectory(tempDir.toFile()).call();
        final RevCommit firstCommit = git.commit().setMessage("initial commit").setAllowEmpty(true).call();
        git.tag().setName("v1.0.0").setAnnotated(true).setObjectId(firstCommit).setMessage(".").call();
        final RevCommit commitBranchAWithTag = git.commit().setMessage("commit branch 1 with tag").setAllowEmpty(true).call();
        git.tag().setName("v1.1.0").setAnnotated(true).setObjectId(commitBranchAWithTag).setMessage(".").call();
        for (int i = 0; i < 10; ++i) {
            git.commit().setMessage("commit branch A " + (i + 1)).setAllowEmpty(true).call();
        }

        git.checkout().setCreateBranch(true).setName("B").setStartPoint(firstCommit).call();
        final RevCommit commitBranchBWithTag = git.commit().setMessage("commit branch B with tag").setAllowEmpty(true).call();
        git.tag().setName("v1.2.0").setAnnotated(true).setObjectId(commitBranchBWithTag).setMessage(".").call();
        for (int i = 0; i < 5; ++i) {
            git.commit().setMessage("commit branch B " + (i + 1)).setAllowEmpty(true).call();
        }
        git.merge().include(git.getRepository().resolve("A")).setFastForward(MergeCommand.FastForwardMode.NO_FF).call();

        // When
        final boolean firstParent = true;
        GitDescription description10 = GitUtil.describe(head(git), Pattern.compile(Pattern.quote("v1.0.0")), git.getRepository(), firstParent, null);
        GitDescription description11 = GitUtil.describe(head(git), Pattern.compile(Pattern.quote("v1.1.0")), git.getRepository(), firstParent, -1);
        GitDescription description12 = GitUtil.describe(head(git), Pattern.compile(Pattern.quote("v1.2.0")), git.getRepository(), firstParent, -1);
        final int maxDepth = 8;
        GitDescription description10Depth = GitUtil.describe(head(git), Pattern.compile(Pattern.quote("v1.0.0")), git.getRepository(), firstParent, maxDepth);
        GitDescription description11Depth = GitUtil.describe(head(git), Pattern.compile(Pattern.quote("v1.1.0")), git.getRepository(), firstParent, maxDepth);
        GitDescription description12Depth = GitUtil.describe(head(git), Pattern.compile(Pattern.quote("v1.2.0")), git.getRepository(), firstParent, maxDepth);

        // Then
        final SoftAssertions softly = new SoftAssertions();
        softly.assertThat(description10.getDistance()).as("v1.0.0 distance").isEqualTo(7);
        softly.assertThat(description10.isTagFound()).as("v1.0.0 tagFound").isTrue();
        softly.assertThat(description11.getDistance()).as("v1.1.0 distance").isEqualTo(7);
        softly.assertThat(description11.isTagFound()).as("v1.1.0 tagFound").isFalse();
        softly.assertThat(description12.getDistance()).as("v1.2.0 distance").isEqualTo(6);
        softly.assertThat(description12.isTagFound()).as("v1.2.0 tagFound").isTrue();
        softly.assertThat(description10Depth.getDistance()).as("v1.0.0 distance with depth").isEqualTo(7);
        softly.assertThat(description10Depth.isTagFound()).as("v1.0.0 tagFound with depth").isTrue();
        softly.assertThat(description11Depth.getDistance()).as("v1.1.0 distance with depth").isEqualTo(7);
        softly.assertThat(description11Depth.isTagFound()).as("v1.1.0 tagFound with depth").isFalse();
        softly.assertThat(description12Depth.getDistance()).as("v1.2.0 distance with depth").isEqualTo(6);
        softly.assertThat(description12Depth.isTagFound()).as("v1.2.0 tagFound with depth").isTrue();
        softly.assertAll();
    }
}
