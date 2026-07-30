/*
 * The MIT License
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

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;

/**
 * Unit tests for {@link Layouter}, in particular the per-axis
 * {@link Axis.Orientation} support used to lay out the configuration matrix.
 */
public class LayouterTest {

    @Test
    public void twoAutoAxesUseLongerAsRows() {
        Axis shortAxis = axis("os", 2, Axis.Orientation.AUTO);
        Axis longAxis = axis("file", 4, Axis.Orientation.AUTO);

        Layouter<?> l = layouter(new AxisList(shortAxis, longAxis));

        // Historical behaviour: the longer axis becomes rows (Y), the shorter columns (X).
        assertThat(names(l.x), contains("os"));
        assertThat(names(l.y), contains("file"));
        assertThat(l.z, empty());
    }

    @Test
    public void explicitOrientationOverridesAutoHeuristic() {
        // The longer axis is forced to be columns, the shorter one to be rows: the opposite of the default.
        Axis rows = axis("os", 2, Axis.Orientation.VERTICAL);
        Axis columns = axis("file", 4, Axis.Orientation.HORIZONTAL);

        Layouter<?> l = layouter(new AxisList(rows, columns));

        assertThat(names(l.x), contains("file"));
        assertThat(names(l.y), contains("os"));
        assertThat(l.z, empty());
    }

    @Test
    public void singleAutoAxisBecomesBulletItems() {
        Axis only = axis("file", 4, Axis.Orientation.AUTO);

        Layouter<?> l = layouter(new AxisList(only));

        assertThat(l.x, empty());
        assertThat(l.y, empty());
        assertThat(names(l.z), contains("file"));
    }

    @Test
    public void singleHorizontalAxisBecomesColumn() {
        Axis only = axis("file", 4, Axis.Orientation.HORIZONTAL);

        Layouter<?> l = layouter(new AxisList(only));

        assertThat(names(l.x), contains("file"));
        assertThat(l.y, empty());
        assertThat(l.z, empty());
    }

    @Test
    public void axesWithSameForcedOrientationStackOnOneSide() {
        Axis a = axis("a", 2, Axis.Orientation.VERTICAL);
        Axis b = axis("b", 3, Axis.Orientation.VERTICAL);

        Layouter<?> l = layouter(new AxisList(a, b));

        assertThat(l.x, empty());
        assertThat(names(l.y), contains("a", "b"));
        assertThat(l.z, empty());
    }

    @Test
    public void mixedExplicitAndAutoAxes() {
        Axis forced = axis("os", 5, Axis.Orientation.HORIZONTAL);
        Axis auto = axis("file", 3, Axis.Orientation.AUTO);

        Layouter<?> l = layouter(new AxisList(forced, auto));

        // The forced axis claims the columns; the lone remaining AUTO axis folds into the empty rows side.
        assertThat(names(l.x), contains("os"));
        assertThat(names(l.y), contains("file"));
        assertThat(l.z, empty());
    }

    private static Layouter<Combination> layouter(AxisList axes) {
        return new Layouter<>(axes) {
            @Override
            protected Combination getT(Combination c) {
                return c;
            }
        };
    }

    private static Axis axis(String name, int valueCount, Axis.Orientation orientation) {
        List<String> values = new ArrayList<>();
        for (int i = 0; i < valueCount; i++) {
            values.add(name + i);
        }
        Axis a = new TextAxis(name, values);
        a.setOrientation(orientation);
        return a;
    }

    private static List<String> names(List<Axis> axes) {
        List<String> result = new ArrayList<>();
        for (Axis a : axes) {
            result.add(a.getName());
        }
        return result;
    }
}
