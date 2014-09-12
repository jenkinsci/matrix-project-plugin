/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package hudson.matrix;

import java.io.IOException;
import java.util.Collection;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;



/**
 *
 * @author Lucie Votypkova
 */
public class MatrixConfigurationTest {
    
    @Rule public JenkinsRule r = new JenkinsRule();
    
    @Test
    public void testDelete() throws Exception{
        MatrixProject project = r.createMatrixProject();
        AxisList axes = new AxisList(
            new Axis("a","active1","active2", "unactive"));
        project.setAxes(axes);
        project.setCombinationFilter("a!=\"unactive\"");
        Collection<MatrixConfiguration> configurations = project.getActiveConfigurations();
        MatrixConfiguration toDelete = project.getItem("a=unactive");
        toDelete.delete();
        assertFalse("Configuration should be deleted for disk", toDelete.getRootDir().exists());
        assertNull("Configuration should be deleted from parent matrix project", project.getItem(toDelete.getCombination()));
        MatrixConfiguration notDelete = project.getItem("a=active1");
        notDelete.delete();
        assertTrue("Active configuration should not be deleted for disk", notDelete.getRootDir().exists());
        assertNotNull("Active configuration should not be deleted from parent matrix project", project.getItem(notDelete.getCombination()));
        assertFalse("Active configuration should not be disabled,", notDelete.isDisabled());
    }
    
}
