/*
 * The MIT License
 *
 * Copyright (c) 2015 Red Hat, Inc.
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

import hudson.util.FormValidation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AxisDescriptorTest {

    private final TextAxis.DescriptorImpl descriptor = new TextAxis.DescriptorImpl();

    @Test
    void combinationNameSpecialChars() {
        assertEquals(
                FormValidation.Kind.ERROR,
                descriptor.doCheckName("a=b").kind
        );

        assertEquals(
                FormValidation.Kind.ERROR,
                descriptor.doCheckName("a,b").kind
        );

        assertEquals(
                FormValidation.Kind.ERROR,
                descriptor.doCheckName("a/b").kind
        );
    }

    @Test
    void combinationValueSpecialChars() {
        assertEquals(
                FormValidation.Kind.OK,
                descriptor.checkValue("a=b").kind
        );

        assertEquals(
                FormValidation.Kind.ERROR,
                descriptor.checkValue("a,b").kind
        );

        assertEquals(
                FormValidation.Kind.OK,
                descriptor.checkValue("!a&&(b||c)").kind
        );
    }
}
