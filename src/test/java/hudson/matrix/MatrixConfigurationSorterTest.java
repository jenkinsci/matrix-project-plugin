package hudson.matrix;

import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.kohsuke.stapler.DataBoundConstructor;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * @author Kohsuke Kawaguchi
 */
@WithJenkins
class MatrixConfigurationSorterTest {

    @Test
    void testConfigRoundtrip(JenkinsRule j) throws Exception {
        MatrixProject p = j.createProject(MatrixProject.class);
        strategy(p).setSorter(new NoopMatrixConfigurationSorter());
        p.save();
        p.doReload();
        j.assertEqualDataBoundBeans(new NoopMatrixConfigurationSorter(), strategy(p).getSorter());

        SorterImpl before = new SorterImpl();
        strategy(p).setSorter(before);
        strategy(p).setRunSequentially(true);
        p.save();
        p.doReload();
        MatrixConfigurationSorter after = strategy(p).getSorter();
        assertNotSame(before, after);
        assertSame(before.getClass(), after.getClass());
    }

    private static DefaultMatrixExecutionStrategyImpl strategy(MatrixProject p) {
        return (DefaultMatrixExecutionStrategyImpl) p.getExecutionStrategy();
    }

    public static class SorterImpl extends MatrixConfigurationSorter {
        @DataBoundConstructor
        public SorterImpl() {
        }

        @Override
        public void validate(MatrixProject p) {
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
