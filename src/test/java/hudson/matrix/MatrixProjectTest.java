/*
 * The MIT License
 *
 * Copyright (c) 2004-2011, Sun Microsystems, Inc., CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.matrix;

import hudson.FilePath;
import hudson.Functions;
import hudson.Launcher;
import hudson.cli.CLICommandInvoker;
import hudson.cli.DeleteBuildsCommand;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Cause;
import hudson.model.FileParameterDefinition;
import hudson.model.FileParameterValue;
import hudson.model.JDK;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Result;
import hudson.model.Slave;
import hudson.model.StringParameterDefinition;
import hudson.model.StringParameterValue;
import hudson.model.queue.QueueTaskFuture;
import hudson.slaves.DumbSlave;
import hudson.slaves.RetentionStrategy;
import hudson.tasks.Ant;
import hudson.tasks.ArtifactArchiver;
import hudson.tasks.BatchFile;
import hudson.tasks.Fingerprinter;
import hudson.tasks.LogRotator;
import hudson.tasks.Maven;
import hudson.tasks.Shell;
import hudson.util.OneShotEvent;
import jenkins.model.Jenkins;
import org.htmlunit.html.HtmlPage;
import org.htmlunit.html.HtmlTable;
import org.htmlunit.html.HtmlTableCell;
import org.htmlunit.html.HtmlTableRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.Email;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.SingleFileSCM;
import org.jvnet.hudson.test.TestBuilder;
import org.jvnet.hudson.test.ToolInstallations;
import org.jvnet.hudson.test.UnstableBuilder;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.jvnet.hudson.test.recipes.LocalData;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static hudson.model.Node.Mode.EXCLUSIVE;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * @author Kohsuke Kawaguchi
 */
@WithJenkins
class MatrixProjectTest {

    private JenkinsRule j;

    @TempDir
    private File tmp;

    @BeforeEach
    void setUp(JenkinsRule rule) {
        j = rule;
    }

    /**
     * Tests that axes are available as build variables in the Ant builds.
     */
    @Test
    void testBuildAxisInAnt() throws Exception {
        MatrixProject p = createMatrixProject();
        TemporaryFolder folder = new TemporaryFolder(tmp);
        folder.create();
        Ant.AntInstallation ant = ToolInstallations.configureDefaultAnt(folder);
        p.getBuildersList().add(new Ant("-Dprop=${db} test", ant.getName(), null, null, null));

        // we need a dummy build script that echos back our property
        p.setScm(new SingleFileSCM("build.xml", "<project default='test'><target name='test'><echo>assertion ${prop}=${db}</echo></target></project>"));

        MatrixBuild build = p.scheduleBuild2(0, new Cause.UserIdCause()).get();
        List<MatrixRun> runs = build.getRuns();
        assertEquals(4, runs.size());
        for (MatrixRun run : runs) {
            j.assertBuildStatus(Result.SUCCESS, run);
            String expectedDb = run.getParent().getCombination().get("db");
            j.assertLogContains("assertion " + expectedDb + "=" + expectedDb, run);
        }
    }

    /**
     * Tests that axes are available as build variables in the Maven builds.
     */
    @Disabled("TODO failing on CI: http://repo1.maven.org/maven2")
    @Test
    void testBuildAxisInMaven() throws Exception {
        assumeFalse(Functions.isWindows(), "TODO seems to have problems with variable substitution");
        MatrixProject p = createMatrixProject();
        Maven.MavenInstallation maven = ToolInstallations.configureDefaultMaven();
        p.getBuildersList().add(new Maven("-Dprop=${db} validate", maven.getName()));

        // we need a dummy build script that echos back our property
        p.setScm(new SingleFileSCM("pom.xml", getClass().getResource("echo-property.pom")));

        MatrixBuild build = p.scheduleBuild2(0).get();
        List<MatrixRun> runs = build.getRuns();
        assertEquals(4, runs.size());
        for (MatrixRun run : runs) {
            j.assertBuildStatus(Result.SUCCESS, run);
            String expectedDb = run.getParent().getCombination().get("db");
            String log = run.getLog();
            System.out.println(log);
            j.assertLogContains("assertion " + expectedDb + "=" + expectedDb, run);
            // also make sure that the variables are expanded at the command line level.
            assertFalse(log.contains("-Dprop=${db}"));
        }
    }

    /**
     * Test that configuration filters work
     */
    @Test
    void testConfigurationFilter() throws Exception {
        MatrixProject p = createMatrixProject();
        p.setCombinationFilter("db==\"mysql\"");
        MatrixBuild build = p.scheduleBuild2(0).get();
        assertEquals(2, build.getRuns().size());
    }

    /**
     * Test that touch stone builds  work
     */
    @Test
    void testTouchStone() throws Exception {
        MatrixProject p = createMatrixProject();
        p.setTouchStoneCombinationFilter("db==\"mysql\"");
        p.setTouchStoneResultCondition(Result.SUCCESS);
        MatrixBuild build = p.scheduleBuild2(0).get();
        assertEquals(4, build.getRuns().size());

        p.getBuildersList().add(new UnstableBuilder());
        build = p.scheduleBuild2(0).get();
        assertEquals(2, build.getExactRuns().size());
    }

    private MatrixProject createMatrixProject() throws IOException {
        MatrixProject p = j.createProject(MatrixProject.class);

        // set up 2x2 matrix
        AxisList axes = new AxisList();
        axes.add(new TextAxis("db", "mysql", "oracle"));
        axes.add(new TextAxis("direction", "north", "south"));
        p.setAxes(axes);

        return p;
    }

    /**
     * Fingerprinter failed to work on the matrix project.
     */
    @Email("http://www.nabble.com/1.286-version-and-fingerprints-option-broken-.-td22236618.html")
    @Test
    void testFingerprinting() throws Exception {
        MatrixProject p = createMatrixProject();
        if (Functions.isWindows()) {
            p.getBuildersList().add(new BatchFile("echo \"\" > p"));
        } else {
            p.getBuildersList().add(new Shell("touch p"));
        }
        p.getPublishersList().add(new ArtifactArchiver("p"));
        p.getPublishersList().add(new Fingerprinter(""));
        j.buildAndAssertSuccess(p);
    }

    private void assertRectangleTable(MatrixProject p) throws Exception {
        HtmlPage html = j.createWebClient().getPage(p);
        HtmlTable table = html.getFirstByXPath("id('matrix')/table");

        // remember cells that are extended from rows above.
        Map<Integer, Integer> rowSpans = new HashMap<>();
        Integer masterWidth = null;
        for (HtmlTableRow r : table.getRows()) {
            int width = 0;
            for (HtmlTableCell c : r.getCells()) {
                width += c.getColumnSpan();
            }
            for (Integer val : rowSpans.values()) {
                width += val;
            }
            if (masterWidth == null) {
                masterWidth = width;
            } else {
                assertEquals(masterWidth.intValue(), width);
            }

            for (HtmlTableCell c : r.getCells()) {
                int rowSpan = c.getRowSpan();
                rowSpans.compute(rowSpan, (k, val) -> (val != null ? val : 0) + c.getColumnSpan());
            }
            // shift rowSpans by one
            Map<Integer, Integer> nrs = new HashMap<>();
            for (Map.Entry<Integer, Integer> entry : rowSpans.entrySet()) {
                if (entry.getKey() > 1) {
                    nrs.put(entry.getKey() - 1, entry.getValue());
                }
            }
            rowSpans = nrs;
        }
    }

    @Issue("JENKINS-4245")
    @Test
    void testLayout1() throws Exception {
        // 5*5*5*5*5 matrix
        MatrixProject p = createMatrixProject();
        List<Axis> axes = new ArrayList<>();
        for (String name : new String[]{"a", "b", "c", "d", "e"}) {
            axes.add(new TextAxis(name, "1", "2", "3", "4"));
        }
        p.setAxes(new AxisList(axes));
        assertRectangleTable(p);
    }

    @Issue("JENKINS-4245")
    @Test
    void testLayout2() throws Exception {
        // 2*3*4*5*6 matrix
        MatrixProject p = createMatrixProject();
        List<Axis> axes = new ArrayList<>();
        for (int i = 2; i <= 6; i++) {
            List<String> vals = new ArrayList<>();
            for (int j = 1; j <= i; j++) {
                vals.add(Integer.toString(j));
            }
            axes.add(new TextAxis("axis" + i, vals));
        }
        p.setAxes(new AxisList(axes));
        assertRectangleTable(p);
    }

    /**
     * Makes sure that the configuration correctly roundtrips.
     */
    @Test
    void testConfigRoundtrip() throws Exception {
        j.jenkins.getJDKs().addAll(Arrays.asList(
                new JDK("jdk1.7", "somewhere"),
                new JDK("jdk1.6", "here"),
                new JDK("jdk1.5", "there")));

        Slave[] slaves = {j.createSlave(), j.createSlave(), j.createSlave()};

        MatrixProject p = createMatrixProject();
        p.getAxes().add(new JDKAxis(Arrays.asList("jdk1.6", "jdk1.5")));
        p.getAxes().add(new LabelAxis("label1", Arrays.asList(slaves[0].getNodeName(), slaves[1].getNodeName())));
        p.getAxes().add(new LabelAxis("label2", List.of(slaves[2].getNodeName()))); // make sure single value handling works OK
        AxisList o = new AxisList(p.getAxes());
        j.configRoundtrip(p);
        AxisList n = p.getAxes();

        assertEquals(o.size(), n.size());
        for (int i = 0; i < o.size(); i++) {
            Axis oi = o.get(i);
            Axis ni = n.get(i);
            assertSame(oi.getClass(), ni.getClass());
            assertEquals(oi.getName(), ni.getName());
            assertEquals(oi.getValues(), ni.getValues());
        }

        DefaultMatrixExecutionStrategyImpl before = new DefaultMatrixExecutionStrategyImpl(true, "foo", Result.UNSTABLE, null);
        p.setExecutionStrategy(before);
        j.configRoundtrip(p);
        j.assertEqualDataBoundBeans(p.getExecutionStrategy(), before);

        before = new DefaultMatrixExecutionStrategyImpl(false, null, null, null);
        p.setExecutionStrategy(before);
        j.configRoundtrip(p);
        j.assertEqualDataBoundBeans(p.getExecutionStrategy(), before);
    }

    @Test
    void testLabelAxes() throws Exception {
        MatrixProject p = createMatrixProject();

        Slave[] slaves = {j.createSlave(), j.createSlave(), j.createSlave(), j.createSlave()};

        p.getAxes().add(new LabelAxis("label1", Arrays.asList(slaves[0].getNodeName(), slaves[1].getNodeName())));
        p.getAxes().add(new LabelAxis("label2", Arrays.asList(slaves[2].getNodeName(), slaves[3].getNodeName())));

        System.out.println(p.getLabels());
        assertEquals(4, p.getLabels().size());
        assertTrue(p.getLabels().contains(j.jenkins.getLabel("slave0&&slave2")));
        assertTrue(p.getLabels().contains(j.jenkins.getLabel("slave1&&slave2")));
        assertTrue(p.getLabels().contains(j.jenkins.getLabel("slave0&&slave3")));
        assertTrue(p.getLabels().contains(j.jenkins.getLabel("slave1&&slave3")));
    }

    /**
     * Quiettng down Hudson causes a dead lock if the parent is running but children is in the queue
     */
    @Issue("JENKINS-4873")
    @Test
    void testQuietDownDeadlock() throws Exception {
        MatrixProject p = createMatrixProject();
        p.setAxes(new AxisList(new TextAxis("foo", "1", "2")));
        p.setRunSequentially(true); // so that we can put the 2nd one in the queue

        final OneShotEvent firstStarted = new OneShotEvent();
        final OneShotEvent buildCanProceed = new OneShotEvent();

        p.getBuildersList().add(new TestBuilder() {
            @Override
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException {
                firstStarted.signal();
                buildCanProceed.block();
                return true;
            }
        });
        QueueTaskFuture<MatrixBuild> f = p.scheduleBuild2(0);

        // have foo=1 block to make sure the 2nd configuration is in the queue
        firstStarted.block();
        // enter into the quiet down while foo=2 is still in the queue
        j.jenkins.doQuietDown();
        buildCanProceed.signal();

        // make sure foo=2 still completes. use time out to avoid hang
        j.assertBuildStatusSuccess(f.get(10, TimeUnit.SECONDS));

        // MatrixProject scheduled after the quiet down shouldn't start
        try {
            Future<MatrixBuild> g = p.scheduleBuild2(0);
            g.get(3, TimeUnit.SECONDS);
            fail();
        } catch (TimeoutException e) {
            // expected
        }
    }

    @Issue("JENKINS-9009")
    @Test
    void testTrickyNodeName() throws Exception {
        List<String> names = new ArrayList<>();
        names.add(j.createSlave("Sean's Workstation", null).getNodeName());
        names.add(j.createSlave("John\"s Workstation", null).getNodeName());
        MatrixProject p = createMatrixProject();
        p.setAxes(new AxisList(new LabelAxis("label", names)));
        j.configRoundtrip(p);

        LabelAxis a = (LabelAxis) p.getAxes().find("label");
        assertEquals(new HashSet<>(a.getValues()), new HashSet<>(names));
    }

    @Issue("JENKINS-10108")
    @Test
    void testTwoFileParams() throws Exception {
        MatrixProject p = createMatrixProject();
        p.setAxes(new AxisList(new TextAxis("foo", "1", "2", "3", "4")));
        p.addProperty(new ParametersDefinitionProperty(
                new FileParameterDefinition("a.txt", ""),
                new FileParameterDefinition("b.txt", "")
        ));

        File dir = tmp;
        List<ParameterValue> params = new ArrayList<>();
        for (final String n : new String[]{"aaa", "bbb"}) {
            params.add(new FileParameterValue(n + ".txt", File.createTempFile(n, "", dir), n));
        }
        QueueTaskFuture<MatrixBuild> f = p.scheduleBuild2(0, new Cause.UserIdCause(), new ParametersAction(params));

        j.assertBuildStatusSuccess(f.get(10, TimeUnit.SECONDS));
    }

    @Issue("JENKINS-34758")
    @Test
    void testParametersAsEnvOnChildren() throws Exception {
        assumeFalse(Functions.isWindows());

        MatrixProject p = createMatrixProject();
        p.setAxes(new AxisList(new TextAxis("foo", "1")));
        p.addProperty(new ParametersDefinitionProperty(
                new StringParameterDefinition("MY_PARAM", "")
        ));
        // must fail if $MY_PARAM or $foo are not defined in children
        p.getBuildersList().add(new Shell("set -eux; echo $MY_PARAM; echo $foo"));

        List<ParameterValue> params = new ArrayList<>();
        params.add(new StringParameterValue("MY_PARAM", "value1"));

        QueueTaskFuture<MatrixBuild> f = p.scheduleBuild2(0, new Cause.UserIdCause(), new ParametersAction(params));
        j.assertBuildStatusSuccess(f.get());
    }

    /**
     * Verifies that the concurrent build feature works, and makes sure
     * that each gets its own unique workspace.
     */
    @Test
    void testConcurrentBuild() throws Exception {
        j.jenkins.setNumExecutors(10);
        Method m = Jenkins.class.getDeclaredMethod("updateComputerList"); // TODO is this really necessary?
        m.setAccessible(true);
        m.invoke(j.jenkins);

        MatrixProject p = createMatrixProject();
        p.setAxes(new AxisList(new TextAxis("foo", "1", "2")));
        p.setConcurrentBuild(true);
        final CountDownLatch latch = new CountDownLatch(4);
        final Set<String> dirs = Collections.synchronizedSet(new HashSet<>());

        p.getBuildersList().add(new TestBuilder() {
            @Override
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
                dirs.add(build.getWorkspace().getRemote());
                FilePath marker = build.getWorkspace().child("file");
                String name = build.getFullDisplayName();
                marker.write(name, Charset.defaultCharset().name());
                latch.countDown();
                latch.await();
                assertEquals(name, marker.readToString());
                return true;
            }
        });

        // should have gotten all unique names
        QueueTaskFuture<MatrixBuild> f1 = p.scheduleBuild2(0);
        // get one going
        f1.waitForStart();
        QueueTaskFuture<MatrixBuild> f2 = p.scheduleBuild2(0);
        MatrixBuild b1 = f1.get();
        for (MatrixRun matrixRun : b1.getExactRuns()) {
            // Test children first as failure of a parent does not say much
            j.assertBuildStatusSuccess(matrixRun);
        }
        j.assertBuildStatusSuccess(b1);
        MatrixBuild b2 = f2.get();
        for (MatrixRun matrixRun : b2.getExactRuns()) {
            // Ditto
            j.assertBuildStatusSuccess(matrixRun);
        }
        j.assertBuildStatusSuccess(b2);

        assertEquals(4, dirs.size());
    }

    /**
     * Test that Actions are passed to configurations
     */
    @Test
    void testParameterActions() throws Exception {
        MatrixProject p = createMatrixProject();

        ParametersDefinitionProperty pdp = new ParametersDefinitionProperty(
                new StringParameterDefinition("PARAM_A", "default_a"),
                new StringParameterDefinition("PARAM_B", "default_b")
        );

        p.addProperty(pdp);
        List<ParameterValue> values = new ArrayList<>();
        for (ParameterDefinition def : pdp.getParameterDefinitions()) {
            values.add(def.getDefaultParameterValue());
        }
        ParametersAction pa = new ParametersAction(values);

        MatrixBuild build = p.scheduleBuild2(0, new Cause.UserIdCause(), pa).get();

        assertEquals(4, build.getRuns().size());

        for (MatrixRun run : build.getRuns()) {
            ParametersAction pa1 = run.getAction(ParametersAction.class);
            assertNotNull(pa1);
            assertNotNull(pa1.getParameter("PARAM_A"));
            assertNotNull(pa1.getParameter("PARAM_B"));
        }
    }

    @Issue("JENKINS-15271")
    @LocalData
    @Test
    void testUpgrade() {
        MatrixProject p = j.jenkins.getItemByFullName("x", MatrixProject.class);
        assertNotNull(p);
        MatrixExecutionStrategy executionStrategy = p.getExecutionStrategy();
        assertEquals(DefaultMatrixExecutionStrategyImpl.class, executionStrategy.getClass());
        DefaultMatrixExecutionStrategyImpl defaultExecutionStrategy = (DefaultMatrixExecutionStrategyImpl) executionStrategy;
        assertFalse(defaultExecutionStrategy.isRunSequentially());
        assertNull(defaultExecutionStrategy.getTouchStoneCombinationFilter());
        assertNull(defaultExecutionStrategy.getTouchStoneResultCondition());
        assertNull(defaultExecutionStrategy.getSorter());
    }

    @Issue("JENKINS-17337")
    @Test
    void reload() throws Exception {
        MatrixProject p = j.createProject(MatrixProject.class);
        AxisList axes = new AxisList();
        axes.add(new TextAxis("p", "only"));
        p.setAxes(axes);
        String n = p.getFullName();
        j.buildAndAssertSuccess(p);
        j.jenkins.reload();
        p = j.jenkins.getItemByFullName(n, MatrixProject.class);
        assertNotNull(p);
        MatrixConfiguration c = p.getItem("p=only");
        assertNotNull(c);
        assertNotNull(c.getBuildByNumber(1));
    }

    /**
     * Given a small controller and a big exclusive agent, the fair scheduling would prefer running the flyweight job
     * in the agent. But if the scheduler honors the EXCLUSIVE flag, then we should see it built on the controller.
     * <p>
     * Since there's a chance that the fair scheduling just so happens to pick up the controller by chance,
     * we try multiple jobs to reduce the chance of that happening.
     */
    @Issue("JENKINS-5076")
    @Test
    void dontRunOnExclusiveSlave() throws Exception {
        List<MatrixProject> projects = new ArrayList<>();
        for (int i = 0; i <= 10; i++) {
            MatrixProject m = j.createProject(MatrixProject.class);
            AxisList axes = new AxisList();
            axes.add(new TextAxis("p", "only"));
            m.setAxes(axes);
            projects.add(m);
        }
        DumbSlave s = new DumbSlave(
                "big",
                "this is a big slave",
                tmp.getPath(),
                "20",
                EXCLUSIVE,
                "",
                j.createComputerLauncher(null),
                RetentionStrategy.NOOP,
                new ArrayList<>());
        j.jenkins.addNode(s);

        s.toComputer().connect(false).get(); // connect this guy

        for (MatrixProject p : projects) {
            MatrixBuild b = j.assertBuildStatusSuccess(p.scheduleBuild2(2));
            assertSame(b.getBuiltOn(), j.jenkins);
        }
    }

    @Test
    @Issue("JENKINS-13554")
    void deletedLockedParentBuild() throws Exception {
        MatrixProject p = j.jenkins.createProject(MatrixProject.class, "project");
        p.setAxes(new AxisList(new TextAxis("AXIS", "1", "2")));
        MatrixBuild codeDelete = p.scheduleBuild2(0).get();
        MatrixBuild uiDelete = p.scheduleBuild2(0).get();
        p.scheduleBuild2(0).get();
        MatrixConfiguration c = p.getItem("AXIS=1");
        c.getLastBuild().delete(); // Punch a hole to matrix locking older builds

        assertEquals(3, p.getBuilds().size());
        assertEquals(2, c.getBuilds().size());

        // UI delete
        JenkinsRule.WebClient wc = j.createWebClient();
        HtmlPage deletePage = wc.getPage(uiDelete).getAnchorByText("Delete build ‘#2’").click();

        assertThat(deletePage.getWebResponse().getContentAsString(), containsString("Warning: #3 depends on this."));

        j.submit(deletePage.getForms().get(deletePage.getForms().size() - 1));
        assertEquals(2, p.getBuilds().size());

        // Code delete
        codeDelete.delete();
        assertEquals(1, p.getBuilds().size());
    }

    @Test
    @Issue("JENKINS-13554")
    void deletedLockedChildrenBuild() throws Exception {
        MatrixProject p = j.jenkins.createProject(MatrixProject.class, "project");
        p.setAxes(new AxisList(new TextAxis("AXIS", "1", "2")));
        p.scheduleBuild2(0).get();
        p.scheduleBuild2(0).get();
        p.scheduleBuild2(0).get();
        MatrixConfiguration c = p.getItem("AXIS=1");
        c.getLastBuild().delete(); // Punch a hole to matrix locking older builds
        MatrixRun uiDelete = c.getBuildByNumber(2);
        MatrixRun codeDelete = c.getBuildByNumber(1);

        assertEquals(3, p.getBuilds().size());
        assertEquals(2, c.getBuilds().size());

        assertNull(p.getBuildByNumber(2).getWhyKeepLog(), "build #1 should not be locked");
        assertNotNull(p.getBuildByNumber(2).getDeleteMessage(), "build #1 should have delete message");

        assertNull(c.getBuildByNumber(2).getWhyKeepLog(), "configuration run #1 should not be locked");
        assertNotNull(c.getBuildByNumber(2).getDeleteMessage(), "configuration run #1 should have delete message");

        // UI delete
        JenkinsRule.WebClient wc = j.createWebClient();
        HtmlPage deletePage = wc.getPage(uiDelete).getAnchorByText("Delete build ‘#2’").click();

        assertThat(deletePage.getWebResponse().getContentAsString(), containsString("Warning: #3 depends on this."));

        j.submit(deletePage.getForms().get(deletePage.getForms().size() - 1));
        assertEquals(1, c.getBuilds().size());

        // Code delete
        codeDelete.delete();
        assertEquals(0, c.getBuilds().size());
    }

    @Test
    @Issue("JENKINS-13554")
    void discardBuilds() throws Exception {
        MatrixProject p = j.jenkins.createProject(MatrixProject.class, "discarder");
        p.setAxes(new AxisList(new TextAxis("AXIS", "VALUE")));

        // Only last build
        p.setBuildDiscarder(new LogRotator("", "1", "", ""));

        p.scheduleBuild2(0).get();
        p.scheduleBuild2(0).get();

        MatrixConfiguration c = p.getItem("AXIS=VALUE");

        await("parent builds are discarded").until(p::getBuilds, hasSize(1));
        await("child builds are discarded").until(c::getBuilds, hasSize(1));
    }

    @Test
    void discardArtifacts() throws Exception {
        MatrixProject p = j.jenkins.createProject(MatrixProject.class, "discarder");
        p.setAxes(new AxisList(new TextAxis("AXIS", "VALUE")));

        // Only last artifacts
        p.setBuildDiscarder(new LogRotator("", "", "", "1"));

        p.getBuildersList().add(new TestBuilder() {
            @Override
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
                build.getWorkspace().child("artifact.zip").write("content", "UTF-8");
                return true;
            }
        });
        p.getPublishersList().add(new ArtifactArchiver("artifact.zip"));

        p.scheduleBuild2(0).get();
        MatrixRun rotated = p.getItem("AXIS=VALUE").getLastBuild();
        p.scheduleBuild2(0).get();
        MatrixRun last = p.getItem("AXIS=VALUE").getLastBuild();

        await("Artifacts are discarded").until(rotated::getArtifacts, empty());
        await().until(last::getArtifacts, hasSize(1));
    }

    @Test
    @Issue("JENKINS-13554")
    void deleteBuildWithChildrenOverCLI() throws Exception {
        MatrixProject p = j.jenkins.createProject(MatrixProject.class, "project");
        p.setAxes(new AxisList(new TextAxis("AXIS", "VALUE")));
        p.scheduleBuild2(0).get();

        CLICommandInvoker invoker = new CLICommandInvoker(j, new DeleteBuildsCommand());
        CLICommandInvoker.Result result = invoker.invokeWithArgs("project", "1");

        assertThat(result, CLICommandInvoker.Matcher.succeeded());
        assertEquals(0, p.getBuilds().size());
        assertEquals(0, p.getItem("AXIS=VALUE").getBuilds().size());
    }
}
