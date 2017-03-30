package hudson.matrix.helper;

import com.google.common.collect.Lists;
import hudson.matrix.Axis;
import hudson.matrix.MatrixBuild;

import java.util.Arrays;
import java.util.List;


public class DynamicTestAxis extends Axis {
    private final List<String> axisValues = Lists.newArrayList();

    public DynamicTestAxis(String name) {
        super(name, "");
    }

    @Override
    public synchronized List<String> getValues() {
        return axisValues;
    }

    @Override
    public synchronized List<String> rebuild(MatrixBuild.MatrixBuildExecution context) {
        // each axis has 2 values: 1. = build number & 2. = build number * 10
        axisValues.clear();
        axisValues.addAll(Arrays.asList(
                Integer.toString(context.getBuild().getNumber()),
                Integer.toString(context.getBuild().getNumber()*10)));
        return axisValues;
    }
}
