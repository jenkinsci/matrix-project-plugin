package hudson.matrix;

import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.StringParameterDefinition;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Created by lvotypko on 4/4/17.
 */
@WithJenkins
class MatrixChildParametersActionTest {

    @Test
    void testLoadingParameters(JenkinsRule j) throws Exception {
        MatrixProject project = j.createProject(MatrixProject.class, "project");
        StringParameterDefinition def1 = new StringParameterDefinition("definition1", "value1", "description");
        StringParameterDefinition def2 = new StringParameterDefinition("definition2", "value2", "description");
        ParametersDefinitionProperty prop = new ParametersDefinitionProperty(def1, def2);
        project.addProperty(prop);
        AxisList axes = new AxisList(
                new Axis("a", "axis1", "axis2"));
        project.setAxes(axes);
        project.save();
        j.buildAndAssertSuccess(project);
        MatrixBuild build = project.getLastBuild();
        assertEquals(2, build.getRuns().size(), "Two configuration should have been build.");
        checkReferencesForParameters(build);

        //reloading
        j.jenkins.reload();
        project = j.jenkins.getItemByFullName("project", MatrixProject.class);
        build = project.getLastBuild();
        checkReferencesForParameters(build);
    }

    private static void checkReferencesForParameters(MatrixBuild build) {
        ParametersAction action = build.getAction(ParametersAction.class);
        ParameterValue definition1 = action.getParameter("definition1");
        ParameterValue definition2 = action.getParameter("definition2");
        for (MatrixRun run : build.getExactRuns()) {
            ParametersAction matrichChildParameters = run.getAction(ParametersAction.class);
            ParameterValue definition1Child = matrichChildParameters.getParameter("definition1");
            ParameterValue definition2Child = matrichChildParameters.getParameter("definition2");
            assertSame(definition1Child, definition1, "Parameters shoud be references to parameters of parent build.");
            assertSame(definition2Child, definition2, "Parameters shoud be references to parameters of parent build.");
        }
    }
}
