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

import hudson.Extension;
import hudson.Util;
import hudson.model.AbstractProject;
import hudson.model.AbstractProject.AbstractProjectDescriptor;
import hudson.util.FormValidation;
import jenkins.model.Jenkins;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

/**
 * {@link Axis} that selects label expressions.
 *
 * @since 1.403
 */
public class LabelExpAxis extends Axis {
	
    @DataBoundConstructor
    public LabelExpAxis(String name, String values) {
        super(name, getExprValues(values));
    }
	
    public LabelExpAxis(String name, List<String> values) {
        super(name, values);
    }
    
    @Override
    public boolean isSystem() {
        return true;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) Jenkins.getActiveInstance().getDescriptorOrDie(getClass());
    }

    public String getValuesString(){
    	StringBuffer sb = new StringBuffer();
    	for(String item: this.getValues()){
    		sb.append(item);
    		sb.append("\n");
    	}
    	return sb.toString();
    }
    
    @Extension
    public static class DescriptorImpl extends AxisDescriptor {
        @Override
        public String getDisplayName() {
            return Messages.LabelExpAxis_DisplayName();
        }

        /**
         * If there's no distributed build set up, it's pointless to provide this axis.
         */
        @Override
        public boolean isInstantiable() {
            final Jenkins j = Jenkins.getActiveInstance();
            return !j.getNodes().isEmpty() || !j.clouds.isEmpty();
        }

        @Restricted(NoExternalUse.class)
        public FormValidation doCheckLabelExpr(@AncestorInPath AbstractProject<?,?> project, @QueryParameter String value) {

            if (Util.fixEmptyAndTrim(value) == null) return FormValidation.error("No expressions provided");

            List<FormValidation> validations = new ArrayList<FormValidation>();
            for (String expr: getExprValues(value)) {
                final FormValidation validation = AbstractProjectDescriptor.validateLabelExpression(expr, project);
                validations.add(validation);
            }

            return FormValidation.aggregate(validations);
        }
    }

    public static List<String> getExprValues(String valuesString){
		List<String> expressions = new LinkedList<String>();
		String[] exprs = valuesString.split("\n");
		for(String expr: exprs){
    		expressions.add(Util.fixEmptyAndTrim(expr));
    	}
		expressions.remove(null); // Empty / whitespace-only lines
		return expressions;
	}
}

