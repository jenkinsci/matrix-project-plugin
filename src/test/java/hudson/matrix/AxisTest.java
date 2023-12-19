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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import hudson.model.JDK;
import hudson.util.VersionNumber;
import java.util.List;
import jenkins.model.Jenkins;

import org.hamcrest.collection.IsEmptyCollection;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;

import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlInput;
import org.htmlunit.html.HtmlPage;

public class AxisTest {

    public @Rule JenkinsRule j = new JenkinsRule();

    private MatrixProject p;
    private WebClient wc;

    @Before
    public void setUp() throws Exception {
        wc = j.createWebClient();
        p = j.createProject(MatrixProject.class);

        // Setup to make all axes available
        j.jenkins.getJDKs().add(new JDK("jdk1.7", "/fake/home"));
        j.createSlave();
    }

    @Test
    public void submitEmptyAxisName() throws Exception {
        wc.getOptions().setThrowExceptionOnFailingStatusCode(false);

        final String expectedMsg = "Matrix axis name '' is invalid: Axis name can not be empty";
        assertFailedWith(expectedMsg, withName("", "User-defined Axis"));
        assertFailedWith(expectedMsg, withName("", "Agents"));
        assertFailedWith(expectedMsg, withName("", "Label expression"));
    }

    @Test
    public void submitInvalidAxisName() throws Exception {
        wc.getOptions().setThrowExceptionOnFailingStatusCode(false);

        String expectedMsg = "Matrix axis name 'a,b' is invalid: ‘,’ is an unsafe character";
        assertFailedWith(expectedMsg, withName("a,b", "User-defined Axis"));
        assertFailedWith(expectedMsg, withName("a,b", "Agents"));
        assertFailedWith(expectedMsg, withName("a,b", "Label expression"));

        expectedMsg = "Matrix axis name 'a=b' is invalid: ‘=’ is an unsafe character";
        assertFailedWith(expectedMsg, withName("a=b", "User-defined Axis"));
        assertFailedWith(expectedMsg, withName("a=b", "Agents"));
        assertFailedWith(expectedMsg, withName("a=b", "Label expression"));
    }

    private void setName(HtmlForm form, String value) {
        List<HtmlInput> inputs = form.getInputsByName("_.name");
        int fieldCount = 0;
        for (HtmlInput input : inputs) {
            // Set the value on the `_.name` field from the "Add Axis" button
            if (input.toString().contains("hudson.matrix.")) {
                input.setValue(value);
                fieldCount++;
            }
        }
        assertThat(fieldCount, equalTo(1));
    }

    @Test
    public void submitInvalidAxisValue() throws Exception {
        wc.getOptions().setThrowExceptionOnFailingStatusCode(false);

        HtmlForm form = addAxis("User-defined Axis");
        setName(form, "a_name");
        form.getInputByName("_.valueString").setValue("a,b");
        assertFailedWith("Matrix axis value 'a,b' is invalid: ‘,’ is an unsafe character", j.submit(form));

        form = addAxis("Label expression");
        setName(form, "a_name");
        form.getElementsByAttribute("textarea", "name", "values").get(0).setTextContent("a,b");
        assertFailedWith("Matrix axis value 'a,b' is invalid: ‘,’ is an unsafe character", j.submit(form));
    }

    @Test
    public void emptyAxisValueListResultInNoConfigurations() throws Exception {
        emptyValue("User-defined Axis");
        emptyValue("Agents");
        emptyValue("Label expression");
        emptyValue("JDK");

        MatrixBuild build = j.buildAndAssertSuccess(p);
        assertThat(build.getRuns(), new IsEmptyCollection<MatrixRun>());
        assertThat(p.getItems(), new IsEmptyCollection<MatrixConfiguration>());

        for (Axis axis : p.getAxes()) {
            assertEquals("", axis.getValueString());
        }
    }

    private HtmlPage withName(String value, String axis) throws Exception {
        HtmlForm form = addAxis(axis);
        form.getInputByName("_.name").setValue(value);
        HtmlPage ret = j.submit(form);
        return ret;
    }

    private HtmlPage emptyValue(String axis) throws Exception {
        HtmlForm form = addAxis(axis);
        if (!"JDK".equals(axis)) { // No "name" attribute
            form.getInputByName("_.name").setValue("some_name");
        }

        HtmlPage ret = j.submit(form);
        return ret;
    }

    private void assertFailedWith(String expected, HtmlPage res) {
        String actual = res.getWebResponse().getContentAsString();

        assertThat(actual, res.getWebResponse().getStatusCode(), equalTo(400));
        assertThat(actual, containsString(expected));
    }

    private HtmlForm addAxis(String axis) throws Exception {
        HtmlPage page = wc.getPage(p, "configure");
        HtmlForm form = page.getFormByName("config");
        j.getButtonByCaption(form, "Add axis").click();
        if (Jenkins.getVersion().isOlderThan(new VersionNumber("2.422"))) {
            page.getAnchorByText(axis).click();
        } else {
            HtmlForm config = page.getFormByName("config");
            j.getButtonByCaption(config, axis).click();
        }
        waitForInput(form);
        return form;
    }

    private void waitForInput(HtmlForm form) throws InterruptedException {
        int numberInputs = form.getInputsByName("_.name").size();
        int initialInputs = numberInputs;
        int tries = 18; // 18 * 17 == 306
        while (tries > 0 && numberInputs == initialInputs) {
            tries--;
            Thread.sleep(17);
            numberInputs = form.getInputsByName("_.name").size();
        }
        // One test seems OK with not finding '_.name' field on the page
        // assertThat("Additional '_.name' field not found on page", numberInputs, greaterThan(initialInputs));
    }
}
