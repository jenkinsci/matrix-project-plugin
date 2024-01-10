package hudson.matrix;

import hudson.Extension;
import hudson.ExtensionList;
import hudson.util.FormValidation;

import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import java.util.List;

/**
 * User-defined plain text axis.
 * 
 * @author Kohsuke Kawaguchi
 */
public class TextAxis extends Axis {
    public TextAxis(String name, List<String> values) {
        super(name, values);
    }

    public TextAxis(String name, String... values) {
        super(name, values);
    }

    @DataBoundConstructor
    public TextAxis(String name, String valueString) {
        super(name, valueString);
    }

    @Override
    public AxisDescriptor getDescriptor() {
        ExtensionList<DescriptorImpl> lookup = ExtensionList.lookup(DescriptorImpl.class);
        if (lookup.isEmpty()) {
            return new DescriptorImpl();
        }
        return lookup.get(0);
    }

    @Extension
    public static class DescriptorImpl extends AxisDescriptor {
        @Override
        public String getDisplayName() {
            return Messages.TextArea_DisplayName();
        }

        @Restricted(NoExternalUse.class)
        // TODO: expandableTextbox does not support form validation
        public FormValidation doCheckValueString(@QueryParameter String value) {
            return super.checkValue(value);
        }
    }
}
