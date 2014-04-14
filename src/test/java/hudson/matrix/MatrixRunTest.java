/*
 * The MIT License
 *
 * Copyright 2014 Jesse Glick.
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

import hudson.model.Run;
import java.io.InputStream;
import jenkins.model.CauseOfInterruption;
import jenkins.model.InterruptedBuildAction;
import static org.junit.Assert.*;
import org.junit.Test;
import org.jvnet.hudson.test.Bug;

public class MatrixRunTest {

    /**
     * Unmarshall a matrix build.xml result.
     */
    @Bug(10903)
    @Test public void unmarshalRunMatrix() {
        InputStream is = MatrixRunTest.class.getResourceAsStream("runMatrix.xml");
        MatrixRun result = (MatrixRun) Run.XSTREAM.fromXML(is);
        assertNotNull(result);
        assertNotNull(result.getPersistentActions());
        assertEquals(2, result.getPersistentActions().size());
        InterruptedBuildAction action = (InterruptedBuildAction) result.getPersistentActions().get(1);
        assertNotNull(action.getCauses());
        assertEquals(1, action.getCauses().size());
        CauseOfInterruption.UserInterruption cause =
            (CauseOfInterruption.UserInterruption) action.getCauses().get(0);
        assertNotNull(cause);
    }

}
