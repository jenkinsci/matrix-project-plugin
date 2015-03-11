/*
 * The MIT License
 *
 * Copyright (c) 2014 Red Hat, Inc.
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

import static org.junit.Assert.*;
import hudson.matrix.LabelExpAxis.DescriptorImpl;
import hudson.util.FormValidation;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class LabelExpAxisTest {

    @Rule public JenkinsRule j = new JenkinsRule();

    @Test
    public void test() throws Exception {
        j.jenkins.setLabelString("aaaa bbbb");

        MatrixProject project = j.createMatrixProject("project");
        DescriptorImpl descriptor = new LabelExpAxis.DescriptorImpl();

        assertEquals(FormValidation.Kind.ERROR, descriptor.doCheckLabelExpr(project, "").kind);

        assertEquals(FormValidation.Kind.OK, descriptor.doCheckLabelExpr(project, "aaaa").kind);
        assertEquals(FormValidation.Kind.OK, descriptor.doCheckLabelExpr(project, "aaaa\nbbbb&&aaaa\n").kind);

        assertEquals(FormValidation.Kind.WARNING, descriptor.doCheckLabelExpr(project, "no_such_slave").kind);

        assertEquals(FormValidation.Kind.ERROR, descriptor.doCheckLabelExpr(project, "&&!||").kind);

        // Use worse result
        assertEquals(FormValidation.Kind.WARNING, descriptor.doCheckLabelExpr(project, "aaaa\nno_such_slave").kind);
        assertEquals(FormValidation.Kind.ERROR, descriptor.doCheckLabelExpr(project, "aaaa\n&&").kind);
        assertEquals(FormValidation.Kind.ERROR, descriptor.doCheckLabelExpr(project, "no_such_slave\n&&").kind);
        assertEquals(FormValidation.Kind.ERROR, descriptor.doCheckLabelExpr(project, "no_such_slave\n&&\naaaa").kind);
    }
}
