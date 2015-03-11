package hudson.matrix;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertNotSame;
import hudson.model.Item;
import hudson.util.FormValidation;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * @author Kohsuke Kawaguchi
 */
public class MatrixConfigurationSorterTest {

    @Rule public JenkinsRule j = new JenkinsRule();

    @Test public void testConfigRoundtrip() throws Exception {
        MatrixProject p = j.createMatrixProject();
        j.configRoundtrip((Item)p);
        j.assertEqualDataBoundBeans(new NoopMatrixConfigurationSorter(),strategy(p).getSorter());

        SorterImpl before = new SorterImpl();
        strategy(p).setSorter(before);
        strategy(p).setRunSequentially(true);
        j.configRoundtrip((Item)p);
        MatrixConfigurationSorter after = strategy(p).getSorter();
        assertNotSame(before,after);
        assertSame(before.getClass(),after.getClass());
    }

    private DefaultMatrixExecutionStrategyImpl strategy(MatrixProject p) {
        return (DefaultMatrixExecutionStrategyImpl) p.getExecutionStrategy();
    }

    public static class SorterImpl extends MatrixConfigurationSorter {
        @DataBoundConstructor
        public SorterImpl() {}

        @Override
        public void validate(MatrixProject p) throws FormValidation {
        }

        public int compare(MatrixConfiguration o1, MatrixConfiguration o2) {
            return o1.getDisplayName().compareTo(o2.getDisplayName());
        }

        @TestExtension
        public static class DescriptorImpl extends MatrixConfigurationSorterDescriptor {
            @Override
            public String getDisplayName() {
                return "Test Sorter";
            }
        }
    }
}
