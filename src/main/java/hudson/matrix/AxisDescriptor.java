/*
 * The MIT License
 *
 * Copyright (c) 2010, InfraDNA, Inc.
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

import hudson.Util;
import hudson.model.Descriptor;
import hudson.model.Failure;
import jenkins.model.Jenkins;
import hudson.util.FormValidation;

import org.kohsuke.stapler.QueryParameter;

/**
 * {@link Descriptor} for {@link Axis}
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class AxisDescriptor extends Descriptor<Axis> {
    protected AxisDescriptor(Class<? extends Axis> clazz) {
        super(clazz);
    }

    protected AxisDescriptor() {
    }

    /**
     * Return false if the user shouldn't be able to create this axis from the UI.
     */
    public boolean isInstantiable() {
        return true;
    }

    /**
     * Makes sure that the given name is good as a axis name.
     *
     * Aside from {@link Jenkins#checkGoodName} this disallows ',' and
     * '=' as special characters used in Combination presentation.
     */
    public FormValidation doCheckName(@QueryParameter String value) {
        if(Util.fixEmpty(value)==null)
            return FormValidation.error(Messages.AxisDescriptor_EmptyAxisName());

        if (value.contains(",")) return unsafeChar(',');
        if (value.contains("=")) return unsafeChar('=');

        try {
            Jenkins.checkGoodName(value);
            return FormValidation.ok();
        } catch (Failure e) {
            return FormValidation.error(e.getMessage());
        }
    }

    /**
     * Makes sure that the given name is good as a axis name.
     *
     * Aside from {@link Jenkins#checkGoodName} this disallows ',' as
     * special character used in Combination presentation. Note it is not
     * necessary to disallow '=' in value as everything after the first
     * occurrence is considered to be a value.
     *
     * Subclasses are expected to expose own <code>doCheck</code> method possibly delegating to this one.
     */
    public FormValidation checkValue(@QueryParameter String value) {
        if(Util.fixEmpty(value)==null)
            return FormValidation.error(Messages.AxisDescriptor_EmptyAxisName());

        if (value.contains(",")) return unsafeChar(',');

        return FormValidation.ok();
    }

    private FormValidation unsafeChar(char chr) {
        return FormValidation.error("‘" + chr + "’ is an unsafe character");
    }
}
