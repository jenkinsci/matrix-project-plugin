package hudson.matrix;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import hudson.FilePath;

import java.util.concurrent.CountDownLatch;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestBuilder;

import hudson.model.AbstractBuild;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.queue.QueueTaskFuture;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import jenkins.model.Jenkins;

/**
 * Tests the custom workspace support in {@link MatrixProject}.
 *
 * To validate the lease behaviour, use concurrent builds to run two builds and make sure they get
 * same/different workspaces.
 *
 * @author Kohsuke Kawaguchi
 */
public class MatrixProjectCustomWorkspaceTest {

    @Rule public JenkinsRule j = new JenkinsRule();
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void customWorkspaceForParentAndChild() throws Exception {
        MatrixProject p = j.createProject(MatrixProject.class);
        File dir = tmp.newFolder();
        p.setCustomWorkspace(dir.getPath());
        p.setChildCustomWorkspace("xyz");

        j.configRoundtrip(p);
        configureCustomWorkspaceConcurrentBuild(p);

        // all concurrent builds should build on the same one workspace
        for (MatrixBuild b : runTwoConcurrentBuilds(p)) {
            assertEquals(dir.getPath(), b.getWorkspace().getRemote());
            for (MatrixRun r : b.getRuns()) {
                assertEquals(new File(dir,"xyz").getPath(), r.getWorkspace().getRemote());
            }
        }
    }

    @Test
    public void customWorkspaceForParent() throws Exception {
        MatrixProject p = j.createProject(MatrixProject.class);
        File dir = tmp.newFolder();
        p.setCustomWorkspace(dir.getPath());
        p.setChildCustomWorkspace(null);

        j.configRoundtrip(p);
        configureCustomWorkspaceConcurrentBuild(p);

        List<MatrixBuild> bs = runTwoConcurrentBuilds(p);

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
            assertFalse(bs.get(i).getRuns().get(0).getWorkspace().equals(bs.get(i).getRuns().get(1).getWorkspace()));
        }
    }

    @Test
    public void customWorkspaceForChild() throws Exception {
        MatrixProject p = j.createProject(MatrixProject.class);
        p.setCustomWorkspace(null);
        p.setChildCustomWorkspace(".");

        j.configRoundtrip(p);
        configureCustomWorkspaceConcurrentBuild(p);

        List<MatrixBuild> bs = runTwoConcurrentBuilds(p);

        // each parent gets different directory
        assertFalse(bs.get(0).getWorkspace().equals(bs.get(1).getWorkspace()));
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
    public void noCustomWorkspace() throws Exception {
        MatrixProject p = j.createProject(MatrixProject.class);
        p.setCustomWorkspace(null);
        p.setChildCustomWorkspace(null);

        j.configRoundtrip(p);
        configureCustomWorkspaceConcurrentBuild(p);

        List<MatrixBuild> bs = runTwoConcurrentBuilds(p);

        // each parent gets different directory
        assertFalse(bs.get(0).getWorkspace().equals(bs.get(1).getWorkspace()));
        // and every sub-build gets a different directory
        for (MatrixBuild b : bs) {
            FilePath x = b.getRuns().get(0).getWorkspace();
            FilePath y = b.getRuns().get(1).getWorkspace();
            FilePath z = b.getWorkspace();
            
            assertFalse(x.equals(y));
            assertFalse(y.equals(z));
            assertFalse(z.equals(x));
        }
    }

    /**
     * Configures MatrixProject such that two builds run concurrently.
     */
    private void configureCustomWorkspaceConcurrentBuild(MatrixProject p) throws Exception {
        // needs sufficient parallel execution capability
        j.jenkins.setNumExecutors(10);
        Method m = Jenkins.class.getDeclaredMethod("updateComputerList"); // TODO is this really necessary?
        m.setAccessible(true);
        m.invoke(j.jenkins);

        p.setAxes(new AxisList(new TextAxis("foo", "1", "2")));
        p.setConcurrentBuild(true);
        final CountDownLatch latch = new CountDownLatch(4);

        p.getBuildersList().add(new TestBuilder() {
            @Override public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
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
    private List<MatrixBuild> runTwoConcurrentBuilds(MatrixProject p) throws Exception {
        QueueTaskFuture<MatrixBuild> f1 = p.scheduleBuild2(0);
        // get one going
        f1.waitForStart();
        QueueTaskFuture<MatrixBuild> f2 = p.scheduleBuild2(0);

        List<MatrixBuild> bs = new ArrayList<MatrixBuild>();
        bs.add(j.assertBuildStatusSuccess(f1.get()));
        bs.add(j.assertBuildStatusSuccess(f2.get()));
        return bs;
    }

    @Test
    public void useCombinationInWorkspaceName() throws Exception {
        MatrixProject p = j.jenkins.createProject(MatrixProject.class, "defaultName");
        p.setAxes(new AxisList(new TextAxis("AXIS", "VALUE")));

        p.scheduleBuild2(0).get();
        MatrixRun build = p.getItem("AXIS=VALUE").getLastBuild();

        assertThat(build.getWorkspace().getRemote(), containsString("/workspace/defaultName/AXIS/VALUE"));
    }

    @Test
    public void useShortWorkspaceNameGlobally() throws Exception {
        MatrixConfiguration.useShortWorkspaceName = true;

        MatrixProject p = j.jenkins.createProject(MatrixProject.class, "shortName");
        p.setAxes(new AxisList(new TextAxis("AXIS", "VALUE")));

        p.scheduleBuild2(0).get();
        MatrixRun build = p.getItem("AXIS=VALUE").getLastBuild();

        assertThat(build.getWorkspace().getRemote(), containsString("/workspace/shortName/" + build.getParent().getDigestName()));

        p.setChildCustomWorkspace("${COMBINATION}"); // Override global value

        p.scheduleBuild2(0).get();
        build = p.getItem("AXIS=VALUE").getLastBuild();

        assertThat(build.getWorkspace().getRemote(), containsString("/workspace/shortName/AXIS/VALUE"));
    }

    @Test
    public void useShortWorkspaceNamePerProject() throws Exception {
        MatrixProject p = j.jenkins.createProject(MatrixProject.class, "shortName");
        p.setAxes(new AxisList(new TextAxis("AXIS", "VALUE")));

        p.scheduleBuild2(0).get();
        MatrixRun build = p.getItem("AXIS=VALUE").getLastBuild();

        assertThat(build.getWorkspace().getRemote(), containsString("/workspace/shortName/AXIS/VALUE"));

        p.setChildCustomWorkspace("${SHORT_COMBINATION}");

        p.scheduleBuild2(0).get();
        build = p.getItem("AXIS=VALUE").getLastBuild();

        assertThat(build.getWorkspace().getRemote(), containsString("/workspace/shortName/" + build.getParent().getDigestName()));
    }
}
