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
import hudson.Functions;
import hudson.Util;
import hudson.model.Node;
import jenkins.model.Jenkins;
import hudson.model.labels.LabelAtom;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.List;
import java.util.Set;

/**
 * {@link Axis} that selects label expressions.
 *
 * @author Kohsuke Kawaguchi
 */
public class LabelAxis extends Axis {
    @DataBoundConstructor
    public LabelAxis(String name, List<String> values) {
        super(name, values);
    }

    @Override
    public boolean isSystem() {
        return true;
    }

    @Override
    public String getValueString() {
        return Util.join(getValues(),"/");
    }

    @Extension
    public static class DescriptorImpl extends AxisDescriptor {
        @Override
        public String getDisplayName() {
            return Messages.LabelAxis_DisplayName();
        }

        /**
         * If there's no distributed build set up, it's pointless to provide this axis.
         * @throws IllegalStateException {@link Jenkins} instance is not ready
         */
        @Override
        public boolean isInstantiable() {
            final Jenkins j = Jenkins.getActiveInstance();
            return !j.getNodes().isEmpty() || !j.clouds.isEmpty();
        }

        private String jsstr(String body, Object... args) {
            return '\"'+Functions.jsStringEscape(String.format(body,args))+'\"';
        }

        public String buildLabelCheckBox(LabelAtom la, LabelAxis instance) {
            String desc = "";
            Set<Node> nodes = la.getNodes();
            if (la.isSelfLabel()) { // if true this is Individual nodes
                for (Node node : nodes) {// maybe only one?
                    desc = node.getNodeDescription();
                }
            } else {  // false this is Labels
                if(la.getDescription() != null){
                    desc += la.getDescription()+": ";
                }
                for (Node node : nodes) {
                    desc += node.getDisplayName() + ", ";
                }
                desc = StringUtils.strip(desc, ", ");
            }
            return jsstr("<input type='checkbox' name='values' json='%s' ",
                        Functions.htmlAttributeEscape(la.getName()))
                   +String.format("+has(%s)+",jsstr(la.getName()))
                   +jsstr("/><label class='attach-previous'>%s (%s)</label>",
                        la.getName(),desc);
            // '${h.jsStringEscape('<input type="checkbox" name="values" json="'+h.htmlAttributeEscape(l.name)+'" ')}'+has("${h.jsStringEscape(l.name)}")+'${h.jsStringEscape('/><label class="attach-previous">'+l.name+' ('+l.description+')</label>')}'
        }
    }
}
