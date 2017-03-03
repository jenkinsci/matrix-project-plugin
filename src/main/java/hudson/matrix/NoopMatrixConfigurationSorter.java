package hudson.matrix;

import hudson.Extension;
import hudson.util.FormValidation;
import java.io.Serializable;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Place holder for default "do not sort" {@link MatrixConfigurationSorter}.
 *
 * @author Kohsuke Kawaguchi
 */
public class NoopMatrixConfigurationSorter extends MatrixConfigurationSorter implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    @DataBoundConstructor
    public NoopMatrixConfigurationSorter() {
    }

    @Override
    public void validate(MatrixProject p) throws FormValidation {
        // nothing
    }

    public int compare(MatrixConfiguration o1, MatrixConfiguration o2) {
        return o1.getDisplayName().compareTo(o2.getDisplayName());
    }

    @Extension(ordinal=100) // this is the default
    public static class DescriptorImpl extends MatrixConfigurationSorterDescriptor {
        @Override
        public String getDisplayName() {
            return "Does not matter";
        }
    }
}
