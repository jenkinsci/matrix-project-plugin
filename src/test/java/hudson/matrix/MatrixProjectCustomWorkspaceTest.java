package hudson.matrix;

import hudson.FilePath;
import java.util.concurrent.CountDownLatch;
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
import org.jvnet.hudson.test.HudsonTestCase;

/**
 * Tests the custom workspace support in {@link MatrixProject}.
 *
 * To validate the lease behaviour, use concurrent builds to run two builds and make sure they get
 * same/different workspaces.
 *
 * @author Kohsuke Kawaguchi
 */
public class MatrixProjectCustomWorkspaceTest extends HudsonTestCase {
    /**
     * Test the case where both the parent and the child has custom workspace specified.
     */
    public void testCustomWorkspace1() throws Exception {
        MatrixProject p = createMatrixProject();
        File dir = env.temporaryDirectoryAllocator.allocate();
        p.setCustomWorkspace(dir.getPath());
        p.setChildCustomWorkspace("xyz");

        configRoundtrip(p);
        configureCustomWorkspaceConcurrentBuild(p);

        // all concurrent builds should build on the same one workspace
        for (MatrixBuild b : runTwoConcurrentBuilds(p)) {
            assertEquals(dir.getPath(), b.getWorkspace().getRemote());
            for (MatrixRun r : b.getRuns()) {
                assertEquals(new File(dir,"xyz").getPath(), r.getWorkspace().getRemote());
            }
        }
    }

    /**
     * Test the case where only the parent has a custom workspace.
     */
    public void testCustomWorkspace2() throws Exception {
        MatrixProject p = createMatrixProject();
        File dir = env.temporaryDirectoryAllocator.allocate();
        p.setCustomWorkspace(dir.getPath());
        p.setChildCustomWorkspace(null);

        configRoundtrip(p);
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

    /**
     * Test the case where only the child has a custom workspace.
     */
    public void testCustomWorkspace3() throws Exception {
        MatrixProject p = createMatrixProject();
        p.setCustomWorkspace(null);
        p.setChildCustomWorkspace(".");

        configRoundtrip(p);
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
    public void testCustomWorkspace4() throws Exception {
        MatrixProject p = createMatrixProject();
        p.setCustomWorkspace(null);
        p.setChildCustomWorkspace(null);

        configRoundtrip(p);
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
        jenkins.setNumExecutors(10);
        Method m = Jenkins.class.getDeclaredMethod("updateComputerList"); // TODO is this really necessary?
        m.setAccessible(true);
        m.invoke(jenkins);

        p.setAxes(new AxisList(new TextAxis("foo", "1", "2")));
        p.setConcurrentBuild(true);
        final CountDownLatch latch = new CountDownLatch(4);

        p.getBuildersList().add(new TestBuilder() {
            @Override public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
                latch.countDown();
                latch.await();
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
        bs.add(assertBuildStatusSuccess(f1.get()));
        bs.add(assertBuildStatusSuccess(f2.get()));
        return bs;
    }
}
