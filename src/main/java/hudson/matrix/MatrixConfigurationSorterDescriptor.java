package hudson.matrix;

import hudson.DescriptorExtensionList;
import hudson.model.Descriptor;
import javax.annotation.Nonnull;
import jenkins.model.Jenkins;

/**
 * Descriptor for {@link MatrixConfigurationSorter}.
 *
 * @author Kohsuke Kawaguchi
 * @since 1.439
 */
public abstract class MatrixConfigurationSorterDescriptor extends Descriptor<MatrixConfigurationSorter> {
    protected MatrixConfigurationSorterDescriptor(Class<? extends MatrixConfigurationSorter> clazz) {
        super(clazz);
    }

    protected MatrixConfigurationSorterDescriptor() {
    }

    /**
     * Returns all the registered {@link MatrixConfigurationSorterDescriptor}s.
     */
    @Nonnull
    public static DescriptorExtensionList<MatrixConfigurationSorter,MatrixConfigurationSorterDescriptor> all() {
        final Jenkins jenkins = Jenkins.getInstance();
        if (jenkins != null) {
            return jenkins.<MatrixConfigurationSorter,MatrixConfigurationSorterDescriptor>getDescriptorList(MatrixConfigurationSorter.class);
        } else {
            return DescriptorExtensionList.createDescriptorList((Jenkins)null, MatrixConfigurationSorter.class);
        }    
    }
}
