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

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Util;
import org.hamcrest.collection.IsEmptyCollection;
import org.htmlunit.HttpMethod;
import org.htmlunit.WebRequest;
import org.htmlunit.WebResponse;
import org.htmlunit.xml.XmlPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;

@WithJenkins
class AxisTest {

    private JenkinsRule j;

    private MatrixProject p;
    private WebClient wc;

    @BeforeEach
    void setUp(JenkinsRule rule) throws Exception {
        j = rule;
        wc = j.createWebClient();
        p = j.createProject(MatrixProject.class);
    }

    @Test
    void submitEmptyAxisName() throws Exception {
        assertConfigXmlRejected(
                "CHANGEME",
                "",
                "Matrix axis name '' is invalid: Axis name can not be empty");
    }

    @Test
    void submitInvalidAxisName() throws Exception {
        assertConfigXmlRejected(
                "CHANGEME",
                "a,b",
                "Matrix axis name 'a,b' is invalid: ‘,’ is an unsafe character");
        assertConfigXmlRejected(
                "CHANGEME",
                "a=b",
                "Matrix axis name 'a=b' is invalid: ‘=’ is an unsafe character");
    }

    @Test
    void submitInvalidAxisValue() throws Exception {
        p.getAxes().add(new TextAxis("a_name", "CHANGEME"));
        p.save();
        XmlPage xmlPage = wc.goToXml(p.getUrl() + "config.xml");
        String badxml = xmlPage.asXml().replaceFirst("\\s*CHANGEME\\s*", "a,b");
        WebResponse response = postXML(p.getUrl() + "config.xml", badxml);
        assertEquals(200, response.getStatusCode());
        assertThat(
                response.getContentAsString(),
                containsString(Util.escape("Matrix axis value 'a,b' is invalid: ‘,’ is an unsafe character")));
    }

    @Test
    void emptyAxisValueListResultInNoConfigurations() throws Exception {
        p.setAxes(new AxisList(
                new TextAxis("user", ""),
                new LabelAxis("agents", List.of()),
                new LabelExpAxis("labels", ""),
                new JDKAxis(Collections.emptyList())));

        MatrixBuild build = j.buildAndAssertSuccess(p);
        assertThat(build.getRuns(), new IsEmptyCollection<>());
        assertThat(p.getItems(), new IsEmptyCollection<>());

        for (Axis axis : p.getAxes()) {
            assertEquals("", axis.getValueString());
        }
    }

    @Test
    @Issue("SECURITY-3289")
    void testHaxorNameFromConfigXml() throws IOException, SAXException {
        p.getAxes().add(new TextAxis("CHANGEME", p.getName()));
        p.save();
        XmlPage xmlPage = wc.goToXml(p.getUrl() + "config.xml");
        String xml = xmlPage.asXml();
        String goodxml = xml.replaceFirst("\\s*CHANGEME\\s*", "a").replaceFirst("\\s+" + p.getName() + "\\s+", p.getName());
        String badxml = xml.replaceFirst("\\s*CHANGEME\\s*", "a/../../../").replaceFirst("\\s+" + p.getName() + "\\s+", p.getName());
        System.out.println("Good XML:\n" + goodxml);
        WebResponse response = postXML(p.getUrl() + "config.xml", goodxml);
        assertEquals(200, response.getStatusCode());

        System.out.println("Bad XML:\n" + badxml);
        response = postXML(p.getUrl() + "config.xml", badxml);
        assertEquals(200, response.getStatusCode()); //FormValidation wants to render stuff so it sends a 200
        assertThat(response.getContentAsString(), containsString(Util.escape("Matrix axis name 'a/../../../' is invalid: ‘/’ is an unsafe character")));
    }

    private void assertConfigXmlRejected(String placeholderName, String badName, String expectedMessage)
            throws IOException, SAXException {
        p.getAxes().clear();
        p.getAxes().add(new TextAxis(placeholderName, "v"));
        p.save();
        XmlPage xmlPage = wc.goToXml(p.getUrl() + "config.xml");
        String badxml = xmlPage.asXml().replaceFirst("\\s*" + placeholderName + "\\s*", badName);
        WebResponse response = postXML(p.getUrl() + "config.xml", badxml);
        assertEquals(200, response.getStatusCode());
        assertThat(response.getContentAsString(), containsString(Util.escape(expectedMessage)));
    }

    private WebResponse postXML(@NonNull String path, @NonNull String xml) throws IOException {
        assert !path.startsWith("/");

        URL URLtoCall = wc.createCrumbedUrl(path);
        WebRequest postRequest = new WebRequest(URLtoCall, HttpMethod.POST);

        postRequest.setAdditionalHeader("Content-Type", "application/xml");
        postRequest.setAdditionalHeader("Accept", "application/xml");
        postRequest.setAdditionalHeader("Accept-Encoding", "*");

        postRequest.setRequestBody(xml);
        return wc.loadWebResponse(postRequest);
    }
}
