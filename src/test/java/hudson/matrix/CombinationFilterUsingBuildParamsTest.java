/*
 * The MIT License
 *
 * Copyright (c) 2012, RedHat Inc.
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

import hudson.ExtensionList;
import hudson.matrix.MatrixBuild.MatrixBuildExecution;
import hudson.matrix.listeners.MatrixBuildListener;
import hudson.model.BuildListener;
import hudson.model.Cause;
import hudson.model.ParametersAction;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.StringParameterValue;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.jenkinsci.plugins.scriptsecurity.sandbox.Whitelist;
import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.BlanketWhitelist;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.jvnet.hudson.test.Issue;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.verification.VerificationMode;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Make sure that the combination filter schedules correct builds in correct order
 *
 * @author ogondza
 */
@Disabled("TODO ScriptApproval.get fails with NPE in ExtensionList.lookup(RootAction.class).get(ScriptApproval.class)")
@ExtendWith(MockitoExtension.class)
class CombinationFilterUsingBuildParamsTest {

    /**
     * Execute releases: experimental, stable, beta, devel
     * <p>
     * x s b d
     * 0.1
     * 0.9 * * * *
     * 1   * * *
     * 2     * *
     * 3       *
     */
    private static final String filter =
            String.format(
                    "(%s) || (%s) || (%s)",
                    "RELEASE == 'stable' && VERSION == '1'",
                    "RELEASE == 'beta'   && VERSION >= '1' && VERSION <= '2'",
                    "RELEASE == 'devel'  && VERSION >= '1' && VERSION <= '3'"
            );

    private static final String touchstoneFilter = "VERSION == '0.9'";

    private static final List<String> releases = Arrays.asList(
            "stable", "beta", "devel", "experimental"
    );

    private final Map<String, MatrixConfiguration> confs = new HashMap<>();
    private final MatrixExecutionStrategy strategy = new DefaultMatrixExecutionStrategyImpl(
            true, touchstoneFilter, Result.SUCCESS, new NoopMatrixConfigurationSorter()
    );

    private MatrixProject project;
    @Mock
    private MatrixBuildExecution execution;
    @Mock
    private MatrixBuild build;
    @Mock
    private MatrixRun run;
    @Mock
    private BuildListener listener;

    @Mock
    private ExtensionList<MatrixBuildListener> extensions;

    @BeforeEach
    void setUp() {
        usingDummyJenkins();
        usingNoListeners();
        usingDummyProject();
        usingDummyExecution();
        withReleaseAxis(releases);
    }

    @Test
    void testCombinationFilterV01() throws InterruptedException, IOException {
        givenTheVersionIs("0.1");

        strategy.run(execution);

        wasNotBuilt(confs.get("devel"));
        wasNotBuilt(confs.get("beta"));
        wasNotBuilt(confs.get("stable"));
        wasNotBuilt(confs.get("experimental"));
    }

    @Test
    void testCombinationFilterV09() throws InterruptedException, IOException {
        givenTheVersionIs("0.9");

        strategy.run(execution);

        wasBuilt(confs.get("devel"));
        wasBuilt(confs.get("beta"));
        wasBuilt(confs.get("stable"));
        wasBuilt(confs.get("experimental"));
    }

    @Test
    void testCombinationFilterV1() throws InterruptedException, IOException {
        givenTheVersionIs("1");

        strategy.run(execution);

        wasBuilt(confs.get("devel"));
        wasBuilt(confs.get("beta"));
        wasBuilt(confs.get("stable"));
        wasNotBuilt(confs.get("experimental"));
    }

    @Test
    void testCombinationFilterV2() throws InterruptedException, IOException {
        givenTheVersionIs("2");

        strategy.run(execution);

        wasBuilt(confs.get("devel"));
        wasBuilt(confs.get("beta"));
        wasNotBuilt(confs.get("stable"));
        wasNotBuilt(confs.get("experimental"));
    }

    @Test
    void testCombinationFilterV3() throws InterruptedException, IOException {
        givenTheVersionIs("3");

        strategy.run(execution);

        wasBuilt(confs.get("devel"));
        wasNotBuilt(confs.get("beta"));
        wasNotBuilt(confs.get("stable"));
        wasNotBuilt(confs.get("experimental"));
    }

    @Test
    @Issue("JENKINS-7285")
    void reproduceTouchstoneRegression() throws InterruptedException, IOException {
        givenTheVersionIs("3");

        // No touchstone
        MatrixExecutionStrategy myStrategy = new DefaultMatrixExecutionStrategyImpl(
                true, null, Result.SUCCESS, new NoopMatrixConfigurationSorter()
        );

        myStrategy.run(execution);

        wasBuilt(confs.get("devel"));
        wasNotBuilt(confs.get("beta"));
        wasNotBuilt(confs.get("stable"));
        wasNotBuilt(confs.get("experimental"));
    }

    private void usingDummyProject() {
        project = Mockito.mock(MatrixProject.class);

        Mockito.when(build.getParent()).thenReturn(project);
        Mockito.when(project.getUrl()).thenReturn("/my/project/");
        Mockito.when(project.getFullDisplayName()).thenReturn("My Project");

        when(project.getAxes()).thenReturn(new AxisList(new Axis("RELEASE", releases)));
        when(project.getCombinationFilter()).thenReturn(filter);
    }

    private void usingDummyExecution() {
        when(execution.getProject()).thenReturn(project);
        when(execution.getBuild()).thenReturn(build);
        when(execution.getListener()).thenReturn(listener);

        // throw away logs
        when(listener.getLogger()).thenReturn(new PrintStream(
                new ByteArrayOutputStream()
        ));

        // Succeed immediately
        when(run.isBuilding()).thenReturn(false);
        when(run.getResult()).thenReturn(Result.SUCCESS);
    }

    private void usingDummyJenkins() {
        try (MockedStatic<Whitelist> mocked = Mockito.mockStatic(Whitelist.class)) {
            mocked.when(Whitelist::all).thenReturn(new BlanketWhitelist());
        }
    }

    private void usingNoListeners() {
        when(extensions.iterator()).thenReturn(Collections.emptyIterator());
        try (MockedStatic<MatrixBuildListener> mocked = Mockito.mockStatic(MatrixBuildListener.class)) {
            mocked.when(MatrixBuildListener::all).thenReturn(extensions);
            when(MatrixBuildListener.buildConfiguration(any(MatrixBuild.class), any(MatrixConfiguration.class))).thenCallRealMethod();
        }
    }

    private void withReleaseAxis(final List<String> releases) {
        for (final String release : releases) {
            confs.put(release, getConfiguration("RELEASE=" + release));
        }

        when(execution.getActiveConfigurations()).thenReturn(
                new HashSet<>(confs.values())
        );
    }

    private MatrixConfiguration getConfiguration(final String axis) {
        final MatrixConfiguration conf = mock(MatrixConfiguration.class);
        when(conf.getParent()).thenReturn(project);
        when(conf.getCombination()).thenReturn(Combination.fromString(axis));
        when(conf.getDisplayName()).thenReturn(axis);
        when(conf.getUrl()).thenReturn(axis);
        when(conf.getBuildByNumber(anyInt())).thenReturn(run);

        return conf;
    }

    private void givenTheVersionIs(final String version) {
        final ParametersAction parametersAction = new ParametersAction(
                new StringParameterValue("VERSION", version)
        );

        when(build.getAction(ParametersAction.class))
                .thenReturn(parametersAction)
        ;
    }

    private void wasBuilt(final MatrixConfiguration conf) {
        wasBuildTimes(conf, times(1));
    }

    private void wasNotBuilt(final MatrixConfiguration conf) {
        wasBuildTimes(conf, never());
    }

    private void wasBuildTimes(
            final MatrixConfiguration conf, final VerificationMode mode
    ) {

        verify(conf, mode).scheduleBuild(
                new ArrayList<MatrixChildAction>(),
                new Cause.UpstreamCause((Run<?, ?>) build)
        );
    }
}
