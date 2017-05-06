package hudson.matrix;

import hudson.model.*;
import hudson.util.FormValidation;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import static org.junit.Assert.*;

/**
 * Created by lvotypko on 4/4/17.
 */
public class MatrixChildParametersActionTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void testLoadingParameters() throws Exception {
        MatrixProject project = j.createProject(MatrixProject.class, "project");
        StringParameterDefinition def1 = new StringParameterDefinition("definition1", "value1", "description");
        StringParameterDefinition def2 = new StringParameterDefinition("definition2", "value2", "description");
        ParametersDefinitionProperty prop = new ParametersDefinitionProperty(def1,def2);
        project.addProperty(prop);
        AxisList axes = new AxisList(
                new Axis("a","axis1","axis2"));
        project.setAxes(axes);
        project.save();
        j.buildAndAssertSuccess(project);
        MatrixBuild build = project.getLastBuild();
        assertEquals("Two configuration should have been build.",2, build.getRuns().size());
        checkReferencesForParameters(build);

        //reloading
        j.jenkins.reload();
        project = j.jenkins.getItemByFullName("project", MatrixProject.class);
        build = project.getLastBuild();
        checkReferencesForParameters(build);
    }

    private void checkReferencesForParameters(MatrixBuild build) {
        ParametersAction action = build.getAction(ParametersAction.class);
        ParameterValue definition1 = action.getParameter("definition1");
        ParameterValue definition2 = action.getParameter("definition2");
        for(MatrixRun run : build.getExactRuns()){
            ParametersAction matrichChildParameters = build.getAction(ParametersAction.class);
            ParameterValue definition1Child = run.getAction(ParametersAction.class).getParameter("definition1");
            ParameterValue definition2Child = run.getAction(ParametersAction.class).getParameter("definition2");
            assertSame("Parameters shoud be references to parameters of parent build.", definition1Child, definition1);
            assertSame("Parameters shoud be references to parameters of parent build.", definition2Child, definition2);
        }
    }
}
