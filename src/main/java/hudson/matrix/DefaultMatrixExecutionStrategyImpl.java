package hudson.matrix;

import groovy.lang.GroovyRuntimeException;
import hudson.AbortException;
import hudson.Extension;
import hudson.console.ModelHyperlinkNote;
import hudson.matrix.MatrixBuild.MatrixBuildExecution;
import hudson.matrix.listeners.MatrixBuildListener;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.model.Cause.UpstreamCause;
import hudson.model.ParametersAction;
import hudson.model.Queue;
import hudson.model.ResourceController;
import hudson.model.Result;
import hudson.model.Run;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.TreeSet;
import javax.annotation.Nullable;
import org.kohsuke.stapler.DataBoundConstructor;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.ListUtils;
import org.apache.commons.collections.Transformer;
import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;

/**
 * {@link MatrixExecutionStrategy} that captures historical behavior.
 *
 * <p>
 * This class is somewhat complex because historically this wasn't an extension point and so
 * people tried to put various logics that cover different use cases into one place.
 * Going forward, people are encouraged to create subtypes to implement a custom logic that suits their needs.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.456
 */
public class DefaultMatrixExecutionStrategyImpl extends MatrixExecutionStrategy {
    private volatile boolean runSequentially;

    /**
     * Filter to select a number of combinations to build first
     */
    private volatile String touchStoneCombinationFilter;

    /**
     * Required result on the touchstone combinations, in order to
     * continue with the rest
     */
    private volatile Result touchStoneResultCondition;

    private volatile MatrixConfigurationSorter sorter;

    @DataBoundConstructor
    public DefaultMatrixExecutionStrategyImpl(Boolean runSequentially, boolean hasTouchStoneCombinationFilter, String touchStoneCombinationFilter, Result touchStoneResultCondition, MatrixConfigurationSorter sorter) {
        this(runSequentially!=null ? runSequentially : false,
            hasTouchStoneCombinationFilter ? touchStoneCombinationFilter : null,
            hasTouchStoneCombinationFilter ? touchStoneResultCondition : null,
            sorter);
    }

    public DefaultMatrixExecutionStrategyImpl(boolean runSequentially, String touchStoneCombinationFilter, Result touchStoneResultCondition, MatrixConfigurationSorter sorter) {
        this.runSequentially = runSequentially;
        this.touchStoneCombinationFilter = touchStoneCombinationFilter;
        this.touchStoneResultCondition = touchStoneResultCondition;
        this.sorter = sorter;
    }

    public DefaultMatrixExecutionStrategyImpl() {
        this(false,false,null,null,null);
    }

    public boolean getHasTouchStoneCombinationFilter() {
        return touchStoneCombinationFilter!=null;
    }

    /**
     * If true, {@link MatrixRun}s are run sequentially, instead of running in parallel.
     *
     * TODO: this should be subsumed by {@link ResourceController}.
     */
    public boolean isRunSequentially() {
        return runSequentially;
    }

    public void setRunSequentially(boolean runSequentially) {
        this.runSequentially = runSequentially;
    }

    public String getTouchStoneCombinationFilter() {
        return touchStoneCombinationFilter;
    }

    public void setTouchStoneCombinationFilter(String touchStoneCombinationFilter) {
        this.touchStoneCombinationFilter = touchStoneCombinationFilter;
    }

    public Result getTouchStoneResultCondition() {
        return touchStoneResultCondition;
    }

    public void setTouchStoneResultCondition(Result touchStoneResultCondition) {
        this.touchStoneResultCondition = touchStoneResultCondition;
    }

    public MatrixConfigurationSorter getSorter() {
        return sorter;
    }

    public void setSorter(MatrixConfigurationSorter sorter) {
        this.sorter = sorter;
    }

    @Override
    public Result run(MatrixBuildExecution execution) throws InterruptedException, IOException {

        Collection<MatrixConfiguration> touchStoneConfigurations = new HashSet<MatrixConfiguration>();
        Collection<MatrixConfiguration> delayedConfigurations = new HashSet<MatrixConfiguration>();

        filterConfigurations(
                execution,
                touchStoneConfigurations,
                delayedConfigurations
        );

        if (notifyStartBuild(execution.getAggregators())) return Result.FAILURE;

        if (sorter != null) {
            touchStoneConfigurations = createTreeSet(touchStoneConfigurations, sorter);
            delayedConfigurations    = createTreeSet(delayedConfigurations, sorter);
        }

        Result result = Result.SUCCESS;
        PrintStream logger = execution.getListener().getLogger();

        result = runConfigurations(execution, wrap(touchStoneConfigurations, execution), result, logger);

        if (touchStoneResultCondition != null && result.isWorseThan(touchStoneResultCondition)) {
            logger.printf("Touchstone configurations resulted in %s, so aborting...%n", result);
            return result;
        }

        return runConfigurations(execution, wrap(delayedConfigurations, execution), result, logger);
    }

    private Collection<MatrixConfigurationWrapper> wrap(Collection<MatrixConfiguration> configurations, MatrixBuildExecution execution) {
        ArrayList<MatrixConfigurationWrapper> wrappers = Lists.newArrayList();
        for (MatrixConfiguration c : configurations) {
            wrappers.add(new MatrixConfigurationWrapper(c, execution));
        }
        return wrappers;
    }

    private Result runConfigurations(MatrixBuildExecution execution, Collection<MatrixConfigurationWrapper> configurations, Result result, PrintStream logger) throws InterruptedException, IOException {
        for (MatrixConfigurationWrapper wrapper : configurations) {
            wrapper.scheduleConfigurationBuild();
            if(runSequentially) {
                result = result.combine(wrapper.waitForCompletion());
            }
        }

        if(!runSequentially) {
            while (true) {
                boolean completed = true;
                for(MatrixConfigurationWrapper wrapper: Lists.newArrayList(configurations)) {
                    Result runResult;
                    if ((runResult = wrapper.checkForCompletion()) != null) {
                        result = result.combine(runResult);
                        if (result.isWorseThan(Result.SUCCESS)) {
                            execution.getBuild().setResult(result);
                        }
                        configurations.remove(wrapper);
                    } else {
                        completed = false;
                    }
                }
                if (completed) break;
                Thread.sleep(1000);
            }
        }
        return result;
    }

    private void filterConfigurations(
            final MatrixBuildExecution execution,
            final Collection<MatrixConfiguration> touchStoneConfigurations,
            final Collection<MatrixConfiguration> delayedConfigurations
    ) throws AbortException {

        final MatrixBuild build = execution.getBuild();

        final FilterScript combinationFilter = FilterScript.parse(execution.getProject().getCombinationFilter(), FilterScript.ACCEPT_ALL);
        final FilterScript touchStoneFilter = FilterScript.parse(getTouchStoneCombinationFilter(), FilterScript.REJECT_ALL);

        try {

            for (MatrixConfiguration c: execution.getActiveConfigurations()) {

                if (!MatrixBuildListener.buildConfiguration(build, c)) continue; // skip rebuild

                final Combination combination = c.getCombination();

                if (touchStoneFilter != null && touchStoneFilter.apply(execution, combination)) {
                    touchStoneConfigurations.add(c);
                } else if (combinationFilter.apply(execution, combination)) {
                    delayedConfigurations.add(c);
                }
            }
        } catch (GroovyRuntimeException ex) {

            PrintStream logger = execution.getListener().getLogger();
            logger.println(ex.getMessage());
            ex.printStackTrace(logger);
            throw new AbortException("Failed executing combination filter");
        }
    }

    private boolean notifyStartBuild(List<MatrixAggregator> aggregators) throws InterruptedException, IOException {
        for (MatrixAggregator a : aggregators)
            if(!a.startBuild())
                return true;
        return false;
    }

    private void notifyEndBuild(MatrixRun b, List<MatrixAggregator> aggregators) throws InterruptedException, IOException {
        if (b==null)    return; // can happen if the configuration run gets cancelled before it gets started.
        for (MatrixAggregator a : aggregators)
            if(!a.endRun(b))
                throw new AbortException();
    }
    
    private <T> TreeSet<T> createTreeSet(Collection<T> items, Comparator<T> sorter) {
        TreeSet<T> r = new TreeSet<T>(sorter);
        r.addAll(items);
        return r;
    }

    private class MatrixConfigurationWrapper {

        private final MatrixConfiguration configuration;

        private final MatrixBuildExecution execution;

        private final BuildListener listener;

        private String whyInQueue = "";

        private long startTime;

        private int appearsCancelledCount;

        public MatrixConfigurationWrapper(MatrixConfiguration configuration, MatrixBuildExecution execution) {
            this.configuration = configuration;
            this.execution = execution;
            this.listener = execution.getListener();
        }

        public MatrixConfiguration getConfiguration() {
            return configuration;
        }

        /** Function to start schedule a single configuration
        *
        * This function schedule a build of a configuration passing all of the Matrixchild actions
        * that are present in the parent build.
        */
        public void scheduleConfigurationBuild() {
            MatrixBuild build = execution.getBuild();
            execution.getListener().getLogger().println(Messages.MatrixBuild_Triggering(ModelHyperlinkNote.encodeTo(configuration)));

            // filter the parent actions for those that can be passed to the individual jobs.
            List<Action> childActions = new ArrayList<Action>(build.getActions(MatrixChildAction.class));
            childActions.addAll(build.getActions(ParametersAction.class)); // used to implement MatrixChildAction
            configuration.scheduleBuild(childActions, new UpstreamCause((Run)build));
            startTime = System.currentTimeMillis();
        }

        public Result checkForCompletion() throws InterruptedException, IOException {
            MatrixRun run = configuration.getBuildByNumber(execution.getBuild().getNumber());
            if(run!=null && !run.isBuilding()) {
                Result buildResult = run.getResult();
                if(buildResult!=null) {
                    notifyEndBuild(run, execution.getAggregators());
                    execution.getListener().getLogger().println(Messages.MatrixBuild_Completed(ModelHyperlinkNote.encodeTo(configuration), buildResult));
                    return buildResult;
                }
            }
            Queue.Item qi = configuration.getQueueItem();
            if(run==null && qi==null)
                appearsCancelledCount++;
            else
                appearsCancelledCount = 0;

            if(appearsCancelledCount>=5) {
                // there's conceivably a race condition in computating b and qi, as their computation
                // are not synchronized. There are indeed several reports of Hudson incorrectly assuming
                // builds being cancelled. See
                // http://www.nabble.com/Master-slave-problem-tt14710987.html and also
                // http://www.nabble.com/Anyone-using-AccuRev-plugin--tt21634577.html#a21671389
                // because of this, we really make sure that the build is cancelled by doing this 5
                // times over 5 seconds
                listener.getLogger().println(Messages.MatrixBuild_AppearsCancelled(ModelHyperlinkNote.encodeTo(configuration)));
                return Result.ABORTED;
            }

            if(qi!=null) {
                // if the build seems to be stuck in the queue, display why
                String why = qi.getWhy();
                if(why != null && !why.equals(whyInQueue) && System.currentTimeMillis()-startTime>5000) {
                    listener.getLogger().print("Configuration " + ModelHyperlinkNote.encodeTo(configuration)+" is still in the queue: ");
                    qi.getCauseOfBlockage().print(listener); //this is still shown on the same line
                    whyInQueue = why;
                }
            }
            
            return null;
        }

        public Result waitForCompletion() throws InterruptedException, IOException {
            // wait for the completion
            Result result;
            while((result = checkForCompletion()) != null) {
                Thread.sleep(1000);
            }
            return result;
        }
    }

    @Extension
    public static class DescriptorImpl extends MatrixExecutionStrategyDescriptor {
        @Override
        public String getDisplayName() {
            return "Classic";
        }
    }
}
