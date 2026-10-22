package GUI;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.LayoutManager;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

/** Components whose height depends on the width they are given (wrapped text, stacked blocks). */
interface HFW {
    int heightForWidth(int width);
}

/** Layout helpers: a vertical stack, weighted columns and a left-to-right row, all aware of HFW. */
final class Lay {
    private Lay() {
    }

    static int h(Component component, int width) {
        if (component instanceof HFW) {
            return ((HFW) component).heightForWidth(width);
        }
        return component.getPreferredSize().height;
    }

    static int w(Component component) {
        return component.getPreferredSize().width;
    }

    /** A fixed-height gap. */
    static Component space(int height) {
        JPanel spacer = new JPanel(null);
        spacer.setOpaque(false);
        spacer.setPreferredSize(new Dimension(1, height));
        return spacer;
    }
}

/** Stacks its children top to bottom, each at the full inner width. */
class VBox extends JPanel implements HFW {
    private final int gap;

    VBox(int gap) {
        super(null);
        this.gap = gap;
        setOpaque(false);
        setLayout(new LayoutManager() {
            @Override public void addLayoutComponent(String name, Component comp) { }
            @Override public void removeLayoutComponent(Component comp) { }

            @Override
            public Dimension preferredLayoutSize(Container parent) {
                Insets in = parent.getInsets();
                int width = 0;
                for (Component child : parent.getComponents()) {
                    if (child.isVisible()) {
                        width = Math.max(width, Lay.w(child));
                    }
                }
                width += in.left + in.right;
                return new Dimension(width, heightForWidth(Math.max(width, parent.getWidth())));
            }

            @Override
            public Dimension minimumLayoutSize(Container parent) {
                return new Dimension(0, 0);
            }

            @Override
            public void layoutContainer(Container parent) {
                Insets in = parent.getInsets();
                int innerWidth = Math.max(0, parent.getWidth() - in.left - in.right);
                int y = in.top;
                boolean first = true;
                for (Component child : parent.getComponents()) {
                    if (!child.isVisible()) {
                        continue;
                    }
                    if (!first) {
                        y += VBox.this.gap;
                    }
                    int height = Lay.h(child, innerWidth);
                    child.setBounds(in.left, y, innerWidth, height);
                    y += height;
                    first = false;
                }
            }
        });
    }

    VBox pad(int top, int left, int bottom, int right) {
        setBorder(new EmptyBorder(top, left, bottom, right));
        return this;
    }

    VBox pad(int all) {
        return pad(all, all, all, all);
    }

    VBox put(Component... children) {
        for (Component child : children) {
            add(child);
        }
        return this;
    }

    @Override
    public int heightForWidth(int width) {
        Insets in = getInsets();
        int inner = Math.max(0, width - in.left - in.right);
        int total = in.top + in.bottom;
        boolean first = true;
        for (Component child : getComponents()) {
            if (!child.isVisible()) {
                continue;
            }
            if (!first) {
                total += gap;
            }
            total += Lay.h(child, inner);
            first = false;
        }
        return total;
    }
}

/**
 * Lays children side by side. A positive weight shares the free width in proportion, zero means
 * "use the preferred width" and a negative weight is a fixed width in pixels.
 */
class Cols extends JPanel implements HFW {
    private final int gap;
    private final double[] weights;
    private boolean stretch;

    Cols(int gap, double... weights) {
        super(null);
        this.gap = gap;
        this.weights = weights;
        setOpaque(false);
        setLayout(new LayoutManager() {
            @Override public void addLayoutComponent(String name, Component comp) { }
            @Override public void removeLayoutComponent(Component comp) { }

            @Override
            public Dimension preferredLayoutSize(Container parent) {
                Insets in = parent.getInsets();
                int width = in.left + in.right;
                int index = 0;
                for (Component child : parent.getComponents()) {
                    if (child.isVisible()) {
                        width += (index++ > 0 ? Cols.this.gap : 0) + Lay.w(child);
                    }
                }
                return new Dimension(width, heightForWidth(Math.max(width, parent.getWidth())));
            }

            @Override
            public Dimension minimumLayoutSize(Container parent) {
                return new Dimension(0, 0);
            }

            @Override
            public void layoutContainer(Container parent) {
                Insets in = parent.getInsets();
                int total = Math.max(0, parent.getWidth() - in.left - in.right);
                int[] widths = widths(total);
                int rowHeight = rowHeight(widths);
                int x = in.left;
                int index = 0;
                for (Component child : parent.getComponents()) {
                    if (!child.isVisible()) {
                        continue;
                    }
                    int height = stretch ? rowHeight : Lay.h(child, widths[index]);
                    child.setBounds(x, in.top, widths[index], height);
                    x += widths[index] + Cols.this.gap;
                    index++;
                }
            }
        });
    }

    Cols stretch() {
        this.stretch = true;
        return this;
    }

    Cols pad(int top, int left, int bottom, int right) {
        setBorder(new EmptyBorder(top, left, bottom, right));
        return this;
    }

    Cols put(Component... children) {
        for (Component child : children) {
            add(child);
        }
        return this;
    }

    private int visibleCount() {
        int count = 0;
        for (Component child : getComponents()) {
            if (child.isVisible()) {
                count++;
            }
        }
        return count;
    }

    private int[] widths(int total) {
        int count = visibleCount();
        int[] widths = new int[count];
        if (count == 0) {
            return widths;
        }
        int free = total - gap * (count - 1);
        double weightSum = 0;
        int index = 0;
        for (Component child : getComponents()) {
            if (!child.isVisible()) {
                continue;
            }
            double weight = index < weights.length ? weights[index] : 1;
            if (weight < 0) {
                widths[index] = (int) -weight;
                free -= widths[index];
            } else if (weight == 0) {
                widths[index] = Lay.w(child);
                free -= widths[index];
            } else {
                weightSum += weight;
            }
            index++;
        }
        free = Math.max(0, free);
        index = 0;
        for (Component child : getComponents()) {
            if (!child.isVisible()) {
                continue;
            }
            double weight = index < weights.length ? weights[index] : 1;
            if (weight > 0) {
                widths[index] = (int) Math.floor(free * weight / weightSum);
            }
            index++;
        }
        return widths;
    }

    private int rowHeight(int[] widths) {
        int height = 0;
        int index = 0;
        for (Component child : getComponents()) {
            if (child.isVisible()) {
                height = Math.max(height, Lay.h(child, widths[index++]));
            }
        }
        return height;
    }

    @Override
    public int heightForWidth(int width) {
        Insets in = getInsets();
        int[] widths = widths(Math.max(0, width - in.left - in.right));
        return in.top + in.bottom + rowHeight(widths);
    }
}

/** Places children left to right at their preferred size, centred vertically. */
class Row extends JPanel implements HFW {
    private final int gap;
    private boolean alignRight;

    Row(int gap) {
        super(null);
        this.gap = gap;
        setOpaque(false);
        setLayout(new LayoutManager() {
            @Override public void addLayoutComponent(String name, Component comp) { }
            @Override public void removeLayoutComponent(Component comp) { }

            @Override
            public Dimension preferredLayoutSize(Container parent) {
                return new Dimension(contentWidth() + parent.getInsets().left + parent.getInsets().right,
                        heightForWidth(0));
            }

            @Override
            public Dimension minimumLayoutSize(Container parent) {
                return new Dimension(0, 0);
            }

            @Override
            public void layoutContainer(Container parent) {
                Insets in = parent.getInsets();
                int height = parent.getHeight() - in.top - in.bottom;
                int x = alignRight ? parent.getWidth() - in.right - contentWidth() : in.left;
                for (Component child : parent.getComponents()) {
                    if (!child.isVisible()) {
                        continue;
                    }
                    Dimension size = child.getPreferredSize();
                    child.setBounds(x, in.top + (height - size.height) / 2, size.width, size.height);
                    x += size.width + Row.this.gap;
                }
            }
        });
    }

    Row right() {
        this.alignRight = true;
        return this;
    }

    Row put(Component... children) {
        for (Component child : children) {
            add(child);
        }
        return this;
    }

    private int contentWidth() {
        int width = 0;
        int count = 0;
        for (Component child : getComponents()) {
            if (child.isVisible()) {
                width += Lay.w(child);
                count++;
            }
        }
        return width + Math.max(0, count - 1) * gap;
    }

    @Override
    public int heightForWidth(int width) {
        int height = 0;
        for (Component child : getComponents()) {
            if (child.isVisible()) {
                height = Math.max(height, child.getPreferredSize().height);
            }
        }
        return height + getInsets().top + getInsets().bottom;
    }
}
