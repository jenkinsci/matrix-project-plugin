/*
 * The MIT License
 * 
 * Copyright (c), Red Hat, Inc.
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
