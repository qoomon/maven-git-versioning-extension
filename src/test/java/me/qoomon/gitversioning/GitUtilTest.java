package me.qoomon.gitversioning;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static me.qoomon.gitversioning.GitConstants.NO_COMMIT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.eclipse.jgit.lib.Constants.HEAD;
import static org.eclipse.jgit.lib.Constants.MASTER;

class GitUtilTest {

    @TempDir
    Path tempDir;

    @Test
    void status_clean() throws GitAPIException {

        // given
        Git git = Git.init().setDirectory(tempDir.toFile()).call();

        // when
        Status status = GitUtil.status(git.getRepository());

        // then
        assertThat(status.isClean()).isTrue();
    }

    @Test
    void status_dirty() throws GitAPIException, IOException {

        // given
        Git git = Git.init().setDirectory(tempDir.toFile()).call();

        boolean dummyFileCreated = new File(tempDir.toFile(), "README.md").createNewFile();
        assertThat(dummyFileCreated).isTrue();

        // when
        Status status = GitUtil.status(git.getRepository());

        // then
        assertThat(status.isClean()).isFalse();
    }

    @Test
    void branch_emptyRepo() throws GitAPIException {

        // given
        Git git = Git.init().setDirectory(tempDir.toFile()).call();

        // when
        String branch = GitUtil.branch(git.getRepository());

        // then
        assertThat(branch).isEqualTo("master");
    }

    @Test
    void branch_nonEmptyRepo() throws GitAPIException {

        // given
        Git git = Git.init().setDirectory(tempDir.toFile()).call();
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
    void tag_pointsAt_emptyRepo() throws GitAPIException {

        // given
        Git git = Git.init().setDirectory(tempDir.toFile()).call();

        // when
        List<String> tags = GitUtil.tag_pointsAt(git.getRepository(), HEAD);

        // then
        assertThat(tags).isEmpty();
    }

    @Test
    void tag_pointsAt_noTags() throws GitAPIException {

        // given
        Git git = Git.init().setDirectory(tempDir.toFile()).call();

        git.commit().setMessage("initial commit").setAllowEmpty(true).call();

        // when
        List<String> tags = GitUtil.tag_pointsAt(git.getRepository(), HEAD);

        // then
        assertThat(tags).isEmpty();
    }

    @Test
    void tag_pointsAt_oneTag() throws GitAPIException {

        // given
        Git git = Git.init().setDirectory(tempDir.toFile()).call();

        RevCommit givenCommit = git.commit().setMessage("initial commit").setAllowEmpty(true).call();
        String givenTagName = "v1.0.0";
        git.tag().setName(givenTagName).setObjectId(givenCommit).call();

        // when
        List<String> tags = GitUtil.tag_pointsAt(git.getRepository(), HEAD);

        // then
        assertThat(tags).containsExactly(givenTagName);
    }

    @Test
    void tag_pointsAt_multipleTags() throws GitAPIException {

        // given
        Git git = Git.init().setDirectory(tempDir.toFile()).call();

        RevCommit givenCommit = git.commit().setMessage("initial commit").setAllowEmpty(true).call();
        String givenTagName1 = "111";
        git.tag().setName(givenTagName1).setObjectId(givenCommit).call();
        String givenTagName2 = "222";
        git.tag().setName(givenTagName2).setObjectId(givenCommit).call();
        String givenTagName3 = "333";
        git.tag().setName(givenTagName3).setObjectId(givenCommit).call();

        // when
        List<String> tags = GitUtil.tag_pointsAt(git.getRepository(), HEAD);

        // then
        assertThat(tags).containsExactlyInAnyOrder(givenTagName1, givenTagName2, givenTagName3);
    }

    @Test
    void tag_pointsAt_lightweightTag() throws GitAPIException {

        // given
        Git git = Git.init().setDirectory(tempDir.toFile()).call();

        RevCommit givenCommit = git.commit().setMessage("initial commit").setAllowEmpty(true).call();

        String givenTagName = "v1.0.0-1234";
        git.tag().setName(givenTagName).setAnnotated(false).setObjectId(givenCommit).call();

        // when
        List<String> tags = GitUtil.tag_pointsAt(git.getRepository(), HEAD);

        // then
        assertThat(tags).containsExactlyInAnyOrder(givenTagName);
    }

    @Test
    void revParse_emptyRepo() throws GitAPIException {

        // given
        Git git = Git.init().setDirectory(tempDir.toFile()).call();

        // when
        String ref = GitUtil.revParse(git.getRepository(), HEAD);

        // then
        assertThat(ref).isEqualTo("0000000000000000000000000000000000000000");
    }

    @Test
    void revParse_nonEmptyRepo() throws GitAPIException {

        // given
        Git git = Git.init().setDirectory(tempDir.toFile()).call();

        RevCommit givenCommit = git.commit().setMessage("initial commit").setAllowEmpty(true).call();

        // when
        String ref = GitUtil.revParse(git.getRepository(), HEAD);

        // then
        assertThat(ref).isEqualTo(givenCommit.name());
    }

    @Test
    void headSituation_emptyRepo() throws GitAPIException {

        // Given
        Git git = Git.init().setDirectory(tempDir.toFile()).call();

        // When
        GitRepoSituation repoSituation = GitUtil.situation(git.getRepository().getDirectory());

        // Then
        assertThat(repoSituation).satisfies(it -> assertSoftly(softly -> {
            softly.assertThat(it.isClean()).isTrue();
            softly.assertThat(it.getHeadCommit()).isEqualTo(NO_COMMIT);
            softly.assertThat(it.getHeadBranch()).isEqualTo(MASTER);
            softly.assertThat(it.getHeadTags()).isEmpty();
        }));
    }

    @Test
    void headSituation_onBranch() throws GitAPIException {

        // Given
        Git git = Git.init().setDirectory(tempDir.toFile()).call();
        RevCommit givenCommit = git.commit().setMessage("init").setAllowEmpty(true).call();

        // When
        GitRepoSituation repoSituation = GitUtil.situation(git.getRepository().getDirectory());

        // Then
        assertThat(repoSituation).satisfies(it -> assertSoftly(softly -> {
            softly.assertThat(it.isClean()).isTrue();
            softly.assertThat(it.getHeadCommit()).isEqualTo(givenCommit.getName());
            softly.assertThat(it.getHeadBranch()).isEqualTo(MASTER);
            softly.assertThat(it.getHeadTags()).isEmpty();
        }));
    }

    @Test
    void headSituation_onBranchWithTag() throws GitAPIException {

        // Given
        Git git = Git.init().setDirectory(tempDir.toFile()).call();
        RevCommit givenCommit = git.commit().setMessage("init").setAllowEmpty(true).call();
        String givenTag = "v1";
        git.tag().setName(givenTag).setObjectId(givenCommit).call();

        // When
        GitRepoSituation repoSituation = GitUtil.situation(git.getRepository().getDirectory());

        // Then
        assertThat(repoSituation).satisfies(it -> assertSoftly(softly -> {
            softly.assertThat(it.isClean()).isTrue();
            softly.assertThat(it.getHeadCommit()).isEqualTo(givenCommit.getName());
            softly.assertThat(it.getHeadBranch()).isEqualTo(MASTER);
            softly.assertThat(it.getHeadTags()).containsExactly(givenTag);
        }));
    }

    @Test
    void headSituation_detachedHead() throws GitAPIException {

        // Given
        Git git = Git.init().setDirectory(tempDir.toFile()).call();
        RevCommit givenCommit = git.commit().setMessage("init").setAllowEmpty(true).call();
        git.checkout().setName(givenCommit.getName()).call();

        // When
        GitRepoSituation repoSituation = GitUtil.situation(git.getRepository().getDirectory());

        // Then
        assertThat(repoSituation).satisfies(it -> assertSoftly(softly -> {
            softly.assertThat(it.isClean()).isTrue();
            softly.assertThat(it.getHeadCommit()).isEqualTo(givenCommit.getName());
            softly.assertThat(it.getHeadBranch()).isNull();
            softly.assertThat(it.getHeadTags()).isEmpty();
        }));
    }

    @Test
    void headSituation_detachedHeadWithTag() throws GitAPIException {

        // Given
        Git git = Git.init().setDirectory(tempDir.toFile()).call();
        RevCommit givenCommit = git.commit().setMessage("init").setAllowEmpty(true).call();
        String givenTag = "v1";
        git.tag().setName(givenTag).setObjectId(givenCommit).call();
        git.checkout().setName(givenTag).call();

        // When
        GitRepoSituation repoSituation = GitUtil.situation(git.getRepository().getDirectory());

        // Then
        assertThat(repoSituation).satisfies(it -> assertSoftly(softly -> {
            softly.assertThat(it.isClean()).isTrue();
            softly.assertThat(it.getHeadCommit()).isEqualTo(givenCommit.getName());
            softly.assertThat(it.getHeadBranch()).isNull();
            softly.assertThat(it.getHeadTags()).containsExactly(givenTag);
        }));
    }

    @Test
    void situation_annotatedTagOnMaster() throws Exception {

        // Given
        Git git = Git.init().setDirectory(tempDir.toFile()).call();
        RevCommit givenCommit = git.commit().setMessage("initial commit").setAllowEmpty(true).call();

        String givenTag = "v1";
        git.tag().setAnnotated(true).setName(givenTag).call();

        // When
        GitRepoSituation repoSituation = GitUtil.situation(tempDir.toFile());

        // Then
        assertThat(repoSituation).satisfies(it -> assertSoftly(softly -> {
            softly.assertThat(it.isClean()).isTrue();
            softly.assertThat(it.getHeadCommit()).isEqualTo(givenCommit.getName());
            softly.assertThat(it.getHeadBranch()).isEqualTo("master");
            softly.assertThat(it.getHeadTags()).containsExactly(givenTag);
        }));
    }

    @Test
    void situation_annotatedTagDetached() throws Exception {

        // Given
        Git git = Git.init().setDirectory(tempDir.toFile()).call();
        RevCommit givenCommit = git.commit().setMessage("initial commit").setAllowEmpty(true).call();

        String givenTag = "v1";
        git.tag().setAnnotated(true).setName(givenTag).setObjectId(givenCommit).call();
        git.checkout().setName(givenTag).call();

        // When
        GitRepoSituation repoSituation = GitUtil.situation(tempDir.toFile());

        // Then
        assertThat(repoSituation).satisfies(it -> assertSoftly(softly -> {
            softly.assertThat(it.isClean()).isTrue();
            softly.assertThat(it.getHeadCommit()).isEqualTo(givenCommit.getName());
            softly.assertThat(it.getHeadBranch()).isNull();
            softly.assertThat(it.getHeadTags()).containsExactly(givenTag);
        }));
    }

    @Test
    void situation_lightweightTagOnMaster() throws Exception {

        // Given
        Git git = Git.init().setDirectory(tempDir.toFile()).call();
        RevCommit givenCommit = git.commit().setMessage("initial commit").setAllowEmpty(true).call();

        String givenTag = "v1";
        git.tag().setAnnotated(false).setName(givenTag).call();

        // When
        GitRepoSituation repoSituation = GitUtil.situation(tempDir.toFile());

        // Then
        assertThat(repoSituation).satisfies(it -> assertSoftly(softly -> {
            softly.assertThat(it.isClean()).isTrue();
            softly.assertThat(it.getHeadCommit()).isEqualTo(givenCommit.getName());
            softly.assertThat(it.getHeadBranch()).isEqualTo("master");
            softly.assertThat(it.getHeadTags()).containsExactly(givenTag);
        }));
    }

    @Test
    void situation_lightweightTagDetached() throws Exception {

        // Given
        Git git = Git.init().setDirectory(tempDir.toFile()).call();
        RevCommit givenCommit = git.commit().setMessage("initial commit").setAllowEmpty(true).call();

        String givenTag = "v1";
        git.tag().setAnnotated(false).setName(givenTag).call();
        git.checkout().setName(givenTag).call();

        // When
        GitRepoSituation repoSituation = GitUtil.situation(tempDir.toFile());

        // Then
        assertThat(repoSituation).satisfies(it -> assertSoftly(softly -> {
            softly.assertThat(it.isClean()).isTrue();
            softly.assertThat(it.getHeadCommit()).isEqualTo(givenCommit.getName());
            softly.assertThat(it.getHeadBranch()).isNull();
            softly.assertThat(it.getHeadTags()).containsExactly(givenTag);
        }));
    }

    @Test
    void situation_multipleTags() throws Exception {

        // Given
        Git git = Git.init().setDirectory(tempDir.toFile()).call();
        git.commit().setMessage("initial commit").setAllowEmpty(true).call();

        String givenTag1 = "v2";
        git.tag().setAnnotated(false).setName(givenTag1).call();
        String givenTag2 = "v1";
        git.tag().setAnnotated(false).setName(givenTag2).call();
        String givenTag3 = "v2.1";
        git.tag().setAnnotated(false).setName(givenTag3).call();

        // When
        GitRepoSituation repoSituation = GitUtil.situation(tempDir.toFile());

        // Then
        // expect tags to be sorted alphanumerically
        List<String> expectedTags = Arrays.asList(givenTag2, givenTag1, givenTag3);
        assertThat(repoSituation.getHeadTags()).isEqualTo(expectedTags);
    }
}