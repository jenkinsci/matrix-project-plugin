/*
 * The MIT License
 * 
 * Copyright (c) 2016, CloudBees, Inc.
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;

import jenkins.model.RunAction2;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.EnvironmentContributor;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.Run;
import hudson.model.TaskListener;

/**
 * This class is used to pass parameter actions from a {@link MatrixBuild} to a {@link MatrixRun}.
 * This wrapper action is needed since SECURITY-170 is blocking undefined parameters in the child job (thus builds).
 * 
 * It's intended for internal use only, that's why constructor and methods are packaged visible.
 * The class itself is public to be visible to core so it can pick up the {@link MatrixChildParametersActionEnvironmentContributor}.
 */
@Restricted(NoExternalUse.class)
public class MatrixChildParametersAction extends ParametersAction implements MatrixChildAction, RunAction2 {

    private transient List<ParameterValue> parameters;

    MatrixChildParametersAction(List<ParameterValue> parameters) {
        this.parameters = parameters;
    }

    @Override
    public List<ParameterValue> getParameters() {
        return parameters;
    }

    @Override
    public void onAttached(Run<?, ?> r) {}

    @Override
    public void onLoad(Run<?, ?> r) {
        if (r instanceof MatrixRun) {
            MatrixRun run = (MatrixRun) r;
            ParametersAction action = run.getParentBuild().getAction(ParametersAction.class);
            if (action != null) {
                // Parameters of build and its runs are guaranteed to be the same. Even the actual instances are the same
                // until it gets (re)loaded from disk when the parameters reside in memory 1+N times squandering memory.
                // This populates now volatile collection with parameters shared between the build and all its runs bringing
                // the space complexity back to 1.
                parameters = action.getParameters();
            } else {
                parameters = Collections.emptyList();
            }
        }
    }

    @Override
    public ParameterValue getParameter(String name) {
        for (ParameterValue p : parameters) {
            if (p != null && p.getName().equals(name)) {
                return p;
            }
        }
        return null;
    }

    static MatrixChildParametersAction create(ParametersAction action) {
        List<ParameterValue> p = new ArrayList<ParameterValue>();
        if (action != null) {
            p.addAll(action.getParameters());
        }
        return new MatrixChildParametersAction(p);
    }

    @Extension
    public static final class MatrixChildParametersActionEnvironmentContributor extends EnvironmentContributor {

        @Override
        public void buildEnvironmentFor(@Nonnull Run r, @Nonnull EnvVars envs, @Nonnull TaskListener listener)
                throws IOException, InterruptedException {
            if (r instanceof MatrixRun) {
                MatrixChildParametersAction childParameters = r.getAction(MatrixChildParametersAction.class);
                if (childParameters != null) {
                    for(ParameterValue p : childParameters.getParameters()) {
                        putEnvVar(envs, p.getName(), String.valueOf(p.getValue()));
                    }
                }
            }
        }

        private static void putEnvVar(@Nonnull EnvVars envs, String name, String value){
            if (value != null) {
                envs.put(name, value);
            } else {
                envs.put(name, "");
            }
        }
    }
}
