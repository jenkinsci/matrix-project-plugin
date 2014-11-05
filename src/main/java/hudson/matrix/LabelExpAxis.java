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
import hudson.model.AutoCompletionCandidates;
import hudson.model.TopLevelItemDescriptor;
import hudson.model.AbstractProject;
import hudson.util.FormValidation;
import jenkins.model.Jenkins;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
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
        return (DescriptorImpl) Jenkins.getInstance().getDescriptorOrDie(getClass());
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
            Jenkins h = Jenkins.getInstance();
            return !h.getNodes().isEmpty() || !h.clouds.isEmpty();
        }

        @Restricted(NoExternalUse.class)
        public FormValidation doCheckLabelExpr(@AncestorInPath AbstractProject<?,?> project, @QueryParameter String value) {
            AbstractProject.AbstractProjectDescriptor desc = projectDescriptor();

            if (Util.fixEmptyAndTrim(value) == null) return FormValidation.error("No expressions provided");

            List<FormValidation> validations = new ArrayList<FormValidation>();
            for (String expr: getExprValues(value)) {
                validations.add(
                        desc.doCheckAssignedLabelString(project, expr)
                );
            }

            return aggregateValidations(validations);
        }

        private final AbstractProject.AbstractProjectDescriptor projectDescriptor() {
            return Jenkins.getInstance().getDescriptorByType(MatrixProject.DescriptorImpl.class);
        }
    }

    // TODO move to hudson.util.FormValidation
    private static FormValidation aggregateValidations(List<FormValidation> validations) {

        if (validations.size() == 1) return validations.get(0);

        StringBuilder sb = new StringBuilder();
        sb.append("<ul style='list-style-type: none'>");
        for (FormValidation validation: validations) {
            sb.append("<li>").append(validation.renderHtml()).append("</li>");
        }
        sb.append("</ul>");

        // Wrap into ok instead of worst of all results as class ok result
        // wrapped in warning or error overall result would inherit its color.
        return FormValidation.okWithMarkup(sb.toString());
    }

    public static List<String> getExprValues(String valuesString){
		List<String> expressions = new LinkedList<String>();
		String[] exprs = valuesString.split("\n");
		for(String expr: exprs){
    		expressions.add(Util.fixEmptyAndTrim(expr));
    	}
		return expressions;
	}

}

