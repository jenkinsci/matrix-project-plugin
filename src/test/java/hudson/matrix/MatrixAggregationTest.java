/*
 * The MIT License
 * 
 * Copyright (c) 2015 IKEDA Yasuyuki
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

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;

import com.google.common.collect.Sets;

/**
 *
 */
public class MatrixAggregationTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();
    
    /**
     * recorder records what operations are called for what builds.
     */
    public static class AggregationRecorder extends Recorder implements MatrixAggregatable {
        public List<AbstractBuild<?,?>> buildsPerformed = new CopyOnWriteArrayList<AbstractBuild<?,?>>();
        public List<MatrixBuild> buildsStarted = new CopyOnWriteArrayList<MatrixBuild>();
        public List<MatrixRun> runsEnded = new CopyOnWriteArrayList<MatrixRun>();
        public List<MatrixBuild> buildsEnded = new CopyOnWriteArrayList<MatrixBuild>();
        
        @Override
        public BuildStepMonitor getRequiredMonitorService() {
            return BuildStepMonitor.NONE;
        }
        
        @Override
        public boolean perform(AbstractBuild<?, ?> build, Launcher launcher,
                BuildListener listener) throws InterruptedException, IOException {
            buildsPerformed.add(build);
            return true;
        }
        
        @Override
        public MatrixAggregator createAggregator(MatrixBuild build,
                Launcher launcher, BuildListener listener) {
            return new MatrixAggregator(build, launcher, listener) {
                @Override
                public boolean startBuild() throws InterruptedException, IOException {
                    buildsStarted.add(build);
                    return true;
                }
                
                @Override
                public boolean endRun(MatrixRun run) throws InterruptedException, IOException {
                    runsEnded.add(run);
                    return true;
                }
                
                @Override
                public boolean endBuild() throws InterruptedException, IOException {
                    buildsEnded.add(build);
                    return true;
                }
            };
        }
        
        @TestExtension
        public static class DescriptorImpl extends BuildStepDescriptor<Publisher> {
            @Override
            public boolean isApplicable(Class<? extends AbstractProject> arg0) {
                return true;
            }
            
            @Override
            public String getDisplayName() {
                return "AggregationRecorder";
            }
        }
    }
    
    /**
     * recorder whose aggregation fails in endBuild.
     */
    public static class EndBuildFailRecorder extends Recorder implements MatrixAggregatable {
        @Override
        public BuildStepMonitor getRequiredMonitorService() {
            return BuildStepMonitor.NONE;
        }
        
        @Override
        public boolean perform(AbstractBuild<?, ?> build, Launcher launcher,
                BuildListener listener) throws InterruptedException, IOException {
            return true;
        }
        
        @Override
        public MatrixAggregator createAggregator(MatrixBuild build,
                Launcher launcher, BuildListener listener) {
            return new MatrixAggregator(build, launcher, listener) {
                @Override
                public boolean endBuild() throws InterruptedException, IOException {
                    return false;
                }
            };
        }
        
        @TestExtension
        public static class DescriptorImpl extends BuildStepDescriptor<Publisher> {
            @Override
            public boolean isApplicable(Class<? extends AbstractProject> arg0) {
                return true;
            }
            
            @Override
            public String getDisplayName() {
                return "EndBuildFailRecorder";
            }
        }
    }
    
    /**
     * recorder whose aggregation raises an exception in endBuild.
     */
    public static class EndBuildRaiseExceptionRecorder extends Recorder implements MatrixAggregatable {
        @Override
        public BuildStepMonitor getRequiredMonitorService() {
            return BuildStepMonitor.NONE;
        }
        
        @Override
        public boolean perform(AbstractBuild<?, ?> build, Launcher launcher,
                BuildListener listener) throws InterruptedException, IOException {
            return true;
        }
        
        @Override
        public MatrixAggregator createAggregator(MatrixBuild build,
                Launcher launcher, BuildListener listener) {
            return new MatrixAggregator(build, launcher, listener) {
                @Override
                public boolean endBuild() throws InterruptedException, IOException {
                    throw new IOException("Exception to test the behavior");
                }
            };
        }
        
        @TestExtension
        public static class DescriptorImpl extends BuildStepDescriptor<Publisher> {
            @Override
            public boolean isApplicable(Class<? extends AbstractProject> arg0) {
                return true;
            }
            
            @Override
            public String getDisplayName() {
                return "EndBuildRaiseExceptionRecorder";
            }
        }
    }
    
    @Test
    public void testAggregation() throws Exception {
        MatrixProject p = j.createMatrixProject();
        AxisList axisList = new AxisList(new TextAxis("axis1", "value1", "value2"));
        p.setAxes(axisList);
        
        AggregationRecorder rec = new AggregationRecorder();
        p.getPublishersList().add(rec);
        
        MatrixBuild b = p.scheduleBuild2(0).get();
        j.assertBuildStatusSuccess(b);
        
        // perform is called for all child builds.
        assertEquals(
                Sets.newHashSet(
                        b.getExactRun(new Combination(axisList, "value1")),
                        b.getExactRun(new Combination(axisList, "value2"))
                ),
                Sets.newHashSet(rec.buildsPerformed)
        );
        // startBuild is called for the parent.
        assertEquals(
                Arrays.asList(b),
                rec.buildsStarted
        );
        // endRun is called for all child builds.
        assertEquals(
                Sets.newHashSet(
                        b.getExactRun(new Combination(axisList, "value1")),
                        b.getExactRun(new Combination(axisList, "value2"))
                ),
                Sets.newHashSet(rec.runsEnded)
        );
        // endBuild is called for the parent.
        assertEquals(
                Arrays.asList(b),
                rec.buildsEnded
        );
    }
    
    @Test
    public void testEndBuildFails() throws Exception {
        MatrixProject p = j.createMatrixProject();
        AxisList axisList = new AxisList(new TextAxis("axis1", "value1", "value2"));
        p.setAxes(axisList);
        
        p.getPublishersList().add(new EndBuildFailRecorder());
        AggregationRecorder rec = new AggregationRecorder();
        p.getPublishersList().add(rec);
        
        MatrixBuild b = p.scheduleBuild2(0).get();
        
        // failure in aggregation doesn't affect build results.
        // It's expected to be set in aggregatiors if needed.
        j.assertBuildStatusSuccess(b);
        
        // endBuild is called for the parent even after an aggregator fails.
        assertEquals(
                Arrays.asList(b),
                rec.buildsEnded
        );
    }
    
    @Test
    public void testEndBuildThrowsException() throws Exception {
        MatrixProject p = j.createMatrixProject();
        AxisList axisList = new AxisList(new TextAxis("axis1", "value1", "value2"));
        p.setAxes(axisList);
        
        p.getPublishersList().add(new EndBuildRaiseExceptionRecorder());
        AggregationRecorder rec = new AggregationRecorder();
        p.getPublishersList().add(rec);
        
        MatrixBuild b = p.scheduleBuild2(0).get();
        
        j.assertBuildStatus(Result.FAILURE, b);
        
        // endBuild is called for the parent even after an aggregator fails.
        assertEquals(
                Arrays.asList(b),
                rec.buildsEnded
        );
    }
}
