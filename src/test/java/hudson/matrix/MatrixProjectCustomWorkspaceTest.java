package hudson.matrix;

import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.queue.QueueTaskFuture;
import jenkins.model.Jenkins;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestBuilder;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Tests the custom workspace support in {@link MatrixProject}.
 * <p>
 * To validate the lease behaviour, use concurrent builds to run two builds and make sure they get
 * same/different workspaces.
 *
 * @author Kohsuke Kawaguchi
 */
@WithJenkins
class MatrixProjectCustomWorkspaceTest {

    private static final String S = File.separator;

    @TempDir
    private File tmp;

    @Test
    void customWorkspaceForParentAndChild(JenkinsRule j) throws Exception {
        MatrixProject p = j.createProject(MatrixProject.class);
        File dir = newFolder(tmp, "junit");
        p.setCustomWorkspace(dir.getPath());
        p.setChildCustomWorkspace("xyz");

        j.configRoundtrip(p);
        configureCustomWorkspaceConcurrentBuild(j, p);

        // all concurrent builds should build on the same one workspace
        for (MatrixBuild b : runTwoConcurrentBuilds(j, p)) {
            assertEquals(dir.getPath(), b.getWorkspace().getRemote());
            for (MatrixRun r : b.getRuns()) {
                assertEquals(new File(dir, "xyz").getPath(), r.getWorkspace().getRemote());
            }
        }
    }

    @Test
    void customWorkspaceForParent(JenkinsRule j) throws Exception {
        MatrixProject p = j.createProject(MatrixProject.class);
        File dir = newFolder(tmp, "junit");
        p.setCustomWorkspace(dir.getPath());
        p.setChildCustomWorkspace(null);

        j.configRoundtrip(p);
        configureCustomWorkspaceConcurrentBuild(j, p);

        List<MatrixBuild> bs = runTwoConcurrentBuilds(j, p);

        // all parent builds share the same workspace
        for (MatrixBuild b : bs) {
            assertEquals(dir.getPath(), b.getWorkspace().getRemote());
        }
        // foo=1 #1 and foo=1 #2 shares the same workspace,
        for (int i = 0; i < 2; i++) {
            assertEquals(bs.get(0).getRuns().get(i).getWorkspace(), bs.get(1).getRuns().get(i).getWorkspace());
        }
        // but foo=1 #1 and foo=2 #1 shouldn't.
        for (int i = 0; i < 2; i++) {
            assertNotEquals(bs.get(i).getRuns().get(0).getWorkspace(), bs.get(i).getRuns().get(1).getWorkspace());
        }
    }

    @Test
    void customWorkspaceForChild(JenkinsRule j) throws Exception {
        MatrixProject p = j.createProject(MatrixProject.class);
        p.setCustomWorkspace(null);
        p.setChildCustomWorkspace(".");

        j.configRoundtrip(p);
        configureCustomWorkspaceConcurrentBuild(j, p);

        List<MatrixBuild> bs = runTwoConcurrentBuilds(j, p);

        // each parent gets different directory
        assertNotEquals(bs.get(0).getWorkspace(), bs.get(1).getWorkspace());
        // but all #1 builds should get the same workspace
        for (MatrixBuild b : bs) {
            for (int i = 0; i < 2; i++) {
                assertEquals(b.getWorkspace(), b.getRuns().get(i).getWorkspace());
            }
        }
    }

    /**
     * Test the case where neither has custom workspace
     */
    @Test
    void noCustomWorkspace(JenkinsRule j) throws Exception {
        MatrixProject p = j.createProject(MatrixProject.class);
        p.setCustomWorkspace(null);
        p.setChildCustomWorkspace(null);

        j.configRoundtrip(p);
        configureCustomWorkspaceConcurrentBuild(j, p);

        List<MatrixBuild> bs = runTwoConcurrentBuilds(j, p);

        // each parent gets different directory
        assertNotEquals(bs.get(0).getWorkspace(), bs.get(1).getWorkspace());
        // and every sub-build gets a different directory
        for (MatrixBuild b : bs) {
            FilePath x = b.getRuns().get(0).getWorkspace();
            FilePath y = b.getRuns().get(1).getWorkspace();
            FilePath z = b.getWorkspace();

            assertNotEquals(x, y);
            assertNotEquals(y, z);
            assertNotEquals(z, x);
        }
    }

    /**
     * Configures MatrixProject such that two builds run concurrently.
     */
    private void configureCustomWorkspaceConcurrentBuild(JenkinsRule j, MatrixProject p) throws Exception {
        // needs sufficient parallel execution capability
        j.jenkins.setNumExecutors(10);
        Method m = Jenkins.class.getDeclaredMethod("updateComputerList"); // TODO is this really necessary?
        m.setAccessible(true);
        m.invoke(j.jenkins);

        p.setAxes(new AxisList(new TextAxis("foo", "1", "2")));
        p.setConcurrentBuild(true);
        final CountDownLatch latch = new CountDownLatch(4);

        p.getBuildersList().add(new TestBuilder() {
            @Override
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) {
                latch.countDown();
                try {
                    latch.await();
                } catch (InterruptedException ignoreOnTeardown) {
                }
                return true;
            }
        });
    }

    /**
     * Runs two concurrent builds and return their results.
     */
    private static List<MatrixBuild> runTwoConcurrentBuilds(JenkinsRule j, MatrixProject p) throws Exception {
        QueueTaskFuture<MatrixBuild> f1 = p.scheduleBuild2(0);
        // get one going
        f1.waitForStart();
        QueueTaskFuture<MatrixBuild> f2 = p.scheduleBuild2(0);

        List<MatrixBuild> bs = new ArrayList<>();
        bs.add(j.assertBuildStatusSuccess(f1.get()));
        bs.add(j.assertBuildStatusSuccess(f2.get()));
        return bs;
    }

    @Test
    void useCombinationInWorkspaceName(JenkinsRule j) throws Exception {
        MatrixProject p = j.jenkins.createProject(MatrixProject.class, "defaultName");
        p.setAxes(new AxisList(new TextAxis("AXIS", "VALUE")));

        p.scheduleBuild2(0).get();
        MatrixRun build = p.getItem("AXIS=VALUE").getLastBuild();

        assertThat(build.getWorkspace().getRemote(), containsString(S + "workspace" + S + "defaultName" + S + "AXIS" + S + "VALUE"));
    }

    @Test
    void useShortWorkspaceNameGlobally(JenkinsRule j) throws Exception {
        MatrixConfiguration.useShortWorkspaceName = true;

        MatrixProject p = j.jenkins.createProject(MatrixProject.class, "shortName");
        p.setAxes(new AxisList(new TextAxis("AXIS", "VALUE")));

        p.scheduleBuild2(0).get();
        MatrixRun build = p.getItem("AXIS=VALUE").getLastBuild();

        assertThat(build.getWorkspace().getRemote(), containsString(S + "workspace" + S + "shortName" + S + build.getParent().getDigestName()));

        p.setChildCustomWorkspace("${COMBINATION}"); // Override global value

        p.scheduleBuild2(0).get();
        build = p.getItem("AXIS=VALUE").getLastBuild();

        assertThat(build.getWorkspace().getRemote(), containsString(S + "workspace" + S + "shortName" + S + "AXIS" + S + "VALUE"));
    }

    @Test
    void useShortWorkspaceNamePerProject(JenkinsRule j) throws Exception {
        MatrixProject p = j.jenkins.createProject(MatrixProject.class, "shortName");
        p.setAxes(new AxisList(new TextAxis("AXIS", "VALUE")));

        p.scheduleBuild2(0).get();
        MatrixRun build = p.getItem("AXIS=VALUE").getLastBuild();

        assertThat(build.getWorkspace().getRemote(), containsString(S + "workspace" + S + "shortName" + S + "AXIS" + S + "VALUE"));

        p.setChildCustomWorkspace("${SHORT_COMBINATION}");

        p.scheduleBuild2(0).get();
        build = p.getItem("AXIS=VALUE").getLastBuild();

        assertThat(build.getWorkspace().getRemote(), containsString(S + "workspace" + S + "shortName" + S + build.getParent().getDigestName()));
    }

    private static File newFolder(File root, String... subDirs) throws IOException {
        String subFolder = String.join("/", subDirs);
        File result = new File(root, subFolder);
        if (!result.mkdirs()) {
            throw new IOException("Couldn't create folders " + root);
        }
        return result;
    }
}
