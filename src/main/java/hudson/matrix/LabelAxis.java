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
import java.io.IOException;
import java.util.Set;
import jenkins.model.Jenkins;
import hudson.model.labels.LabelAtom;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.ArrayList;
import java.util.List;

import static hudson.Functions.htmlAttributeEscape;
import static hudson.Functions.jsStringEscape;

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
        return String.join("/", getValues());
    }

    @Restricted(NoExternalUse.class)
    public boolean isChecked(String name) {
        return getValues().contains(name);
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

        @Restricted(NoExternalUse.class)
        public LabelLists getLabelLists() {
            return new LabelLists();
        }

        @Restricted(NoExternalUse.class)
        public String getSaveDescription(LabelAtom labelAtom) throws IOException {
            // remove line breaks as html tooltip will replace linebreaks with </br>.
            // This ensures that the description is displayed in the same way as on the label
            return Jenkins.get().getMarkupFormatter().translate(labelAtom.getDescription()).
                    replaceAll("\r", "").replaceAll("\n", "");
        }
    }

    @Restricted(NoExternalUse.class)
    public static class LabelLists {
        private List<LabelAtom> machines = new ArrayList<>();
        private List<LabelAtom> labels = new ArrayList<>();

        public LabelLists() {
            Set<LabelAtom> labelsAtoms = Jenkins.get().getLabelAtoms();
            labelsAtoms.forEach(atom -> {
                if (atom.isSelfLabel()) {
                    machines.add(atom);
                } else {
                    labels.add(atom);
                }
            });
        }

        public List<LabelAtom> getMachines() {
            return machines;
        }

        public List<LabelAtom> getLabels() {
            return labels;
        }
    }
}
