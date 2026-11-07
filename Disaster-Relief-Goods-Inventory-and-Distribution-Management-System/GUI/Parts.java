package GUI;

import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.List;
import java.util.function.IntConsumer;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

/** Underlined tabs ("All items", "Low stock (2)", ...). */
class Tabs extends JComponent {
    private final List<String> labels;
    private final int selected;
    private final IntConsumer onSelect;
    private final int[] starts;
    private final int[] widths;

    Tabs(List<String> labels, int selected, IntConsumer onSelect) {
        this.labels = labels;
        this.selected = selected;
        this.onSelect = onSelect;
        this.starts = new int[labels.size()];
        this.widths = new int[labels.size()];
        setOpaque(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent event) {
                for (int index = 0; index < starts.length; index++) {
                    if (event.getX() >= starts[index] - 8 && event.getX() <= starts[index] + widths[index] + 8
                            && index != Tabs.this.selected) {
                        Tabs.this.onSelect.accept(index);
                        return;
                    }
                }
            }
        });
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(400, 38);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Gfx.g2(g);
        g2.setColor(Theme.RULE);
        g2.fillRect(0, getHeight() - 1, getWidth(), 1);
        int x = 2;
        for (int index = 0; index < labels.size(); index++) {
            boolean active = index == selected;
            g2.setFont(active ? Theme.semibold(13) : Theme.regular(13));
            FontMetrics fm = g2.getFontMetrics();
            starts[index] = x;
            widths[index] = fm.stringWidth(labels.get(index));
            g2.setColor(active ? Theme.TEAL : Theme.MUTED);
            g2.drawString(labels.get(index), x, (getHeight() - 3 - fm.getHeight()) / 2 + fm.getAscent());
            if (active) {
                g2.setColor(Theme.TEAL);
                g2.fillRect(x, getHeight() - 3, widths[index], 3);
            }
            x += widths[index] + 28;
        }
        g2.dispose();
    }
}

/** The numbered step bar shown on multi-step forms. */
class Stepper extends JComponent {
    private final String[] labels;
    private final int current;

    /** current is 0-based; earlier steps are shown as done with a check mark. */
    Stepper(String[] labels, int current) {
        this.labels = labels;
        this.current = current;
        setOpaque(false);
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(400, 50);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Gfx.g2(g);
        Gfx.roundBox(g2, getWidth(), getHeight(), 14, Color.WHITE, Theme.BORDER);
        int column = getWidth() / labels.length;
        for (int index = 0; index < labels.length; index++) {
            boolean done = index < current;
            boolean active = index == current;
            String text = (done ? "✓ " : (index + 1) + " ") + labels[index];
            g2.setFont(active ? Theme.semibold(12.5f) : Theme.regular(12.5f));
            g2.setColor(active || done ? Theme.TEAL : Theme.MUTED);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(text, index * column + 22, (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
        }
        g2.dispose();
    }
}

/** A bordered table with a shaded header and rows that may be clickable. */
class DataTable extends VBox {
    private final double[] weights;

    DataTable(String[] headers, double... weights) {
        super(0);
        this.weights = weights;
        Cols header = new Cols(14, weights) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = Gfx.g2(g);
                Path2D top = new Path2D.Float();
                float arc = 14;
                top.moveTo(0, getHeight());
                top.lineTo(0, arc);
                top.quadTo(0, 0, arc, 0);
                top.lineTo(getWidth() - arc, 0);
                top.quadTo(getWidth(), 0, getWidth(), arc);
                top.lineTo(getWidth(), getHeight());
                top.closePath();
                g2.setColor(Theme.HEADER_BG);
                g2.fill(top);
                g2.dispose();
            }
        };
        header.pad(13, 18, 13, 18);
        for (String text : headers) {
            JLabel label = new JLabel(text);
            label.setFont(Theme.medium(11.5f));
            label.setForeground(Theme.MUTED);
            header.add(label);
        }
        add(header);
    }

    DataTable row(Object... cells) {
        return rowClick(null, cells);
    }

    DataTable rowClick(Runnable click, Object... cells) {
        Row row = new Row(weights, click);
        for (Object cell : cells) {
            row.add(toComponent(cell));
        }
        add(row);
        return this;
    }

    DataTable empty(String message) {
        JLabel label = new JLabel(message, JLabel.CENTER);
        label.setFont(Theme.regular(13));
        label.setForeground(Theme.MUTED);
        label.setBorder(new EmptyBorder(34, 0, 34, 0));
        add(new Divider());
        add(label);
        return this;
    }

    DataTable footer(String text) {
        JLabel label = new JLabel(text);
        label.setFont(Theme.regular(11.5f));
        label.setForeground(Theme.MUTED);
        add(new Footer(label));
        return this;
    }

    static Component toComponent(Object cell) {
        if (cell instanceof Component) {
            return (Component) cell;
        }
        String[] lines = cell instanceof String[] ? (String[]) cell
                : cell == null ? new String[] { "" } : String.valueOf(cell).split("\n", -1);
        if (lines.length == 1) {
            return new Wrap(lines[0], Theme.regular(13), Theme.TEXT).maxWidth(2000);
        }
        VBox box = new VBox(1);
        for (int index = 0; index < lines.length; index++) {
            box.put(new Wrap(lines[index], index == 0 ? Theme.regular(13) : Theme.regular(12),
                    index == 0 ? Theme.TEXT : Theme.MUTED).maxWidth(2000));
        }
        return box;
    }

    @Override
    protected void paintChildren(Graphics g) {
        super.paintChildren(g);
        Graphics2D g2 = Gfx.g2(g);
        g2.setColor(Theme.BORDER);
        g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1, getHeight() - 1, 14, 14));
        g2.dispose();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Gfx.g2(g);
        Gfx.roundBox(g2, getWidth(), getHeight(), 14, Color.WHITE, null);
        g2.dispose();
    }

    private static final class Divider extends JComponent {
        @Override
        public Dimension getPreferredSize() {
            return new Dimension(1, 1);
        }

        @Override
        protected void paintComponent(Graphics g) {
            g.setColor(Theme.RULE);
            g.fillRect(0, 0, getWidth(), 1);
        }
    }

    private static final class Footer extends JPanel {
        Footer(JLabel label) {
            super(new java.awt.BorderLayout());
            setOpaque(false);
            setBorder(new EmptyBorder(12, 18, 12, 18));
            add(label);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Gfx.g2(g);
            Path2D bottom = new Path2D.Float();
            float arc = 14;
            bottom.moveTo(0, 0);
            bottom.lineTo(0, getHeight() - arc);
            bottom.quadTo(0, getHeight(), arc, getHeight());
            bottom.lineTo(getWidth() - arc, getHeight());
            bottom.quadTo(getWidth(), getHeight(), getWidth(), getHeight() - arc);
            bottom.lineTo(getWidth(), 0);
            bottom.closePath();
            g2.setColor(Theme.HEADER_BG);
            g2.fill(bottom);
            g2.setColor(Theme.RULE);
            g2.fillRect(0, 0, getWidth(), 1);
            g2.dispose();
        }
    }

    private static final class Row extends Cols {
        private boolean hover;

        Row(double[] weights, Runnable click) {
            super(14, weights);
            pad(15, 18, 15, 18);
            if (click != null) {
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseEntered(MouseEvent event) {
                        hover = true;
                        repaint();
                    }

                    @Override
                    public void mouseExited(MouseEvent event) {
                        hover = false;
                        repaint();
                    }

                    @Override
                    public void mouseReleased(MouseEvent event) {
                        if (event.getX() >= 0 && event.getX() < getWidth() && event.getY() >= 0
                                && event.getY() < getHeight()) {
                            click.run();
                        }
                    }
                });
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Gfx.g2(g);
            if (hover) {
                g2.setColor(Theme.HEADER_BG);
                g2.fill(new RoundRectangle2D.Float(4, 2, getWidth() - 8, getHeight() - 3, 10, 10));
            }
            g2.setColor(Theme.RULE);
            g2.fillRect(0, 0, getWidth(), 1);
            g2.dispose();
        }
    }
}

/** Hosts one page at a time: centred, at most 1400 px wide, scrolling vertically only. */
class PageHost extends JPanel implements javax.swing.Scrollable {
    private static final int MAX = 1400;
    private static final int SIDE = 32;
    private static final int TOP = 22;
    private static final int BOTTOM = 36;
    private Component content;

    PageHost() {
        super(null);
        setBackground(Theme.BG);
    }

    void setContent(Component component) {
        removeAll();
        content = component;
        add(component);
        revalidate();
        repaint();
    }

    private int contentWidth(int width) {
        return Math.max(300, Math.min(MAX, width - 2 * SIDE));
    }

    @Override
    public Dimension getPreferredSize() {
        int width = getParent() instanceof javax.swing.JViewport ? getParent().getWidth() : 1200;
        int height = content == null ? 0 : TOP + Lay.h(content, contentWidth(width)) + BOTTOM;
        return new Dimension(width, height);
    }

    @Override
    public void doLayout() {
        if (content == null) {
            return;
        }
        int width = contentWidth(getWidth());
        content.setBounds((getWidth() - width) / 2, TOP, width, Lay.h(content, width));
    }

    @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
    @Override public int getScrollableUnitIncrement(java.awt.Rectangle visible, int orientation, int direction) { return 28; }
    @Override public int getScrollableBlockIncrement(java.awt.Rectangle visible, int orientation, int direction) { return visible.height * 9 / 10; }
    @Override public boolean getScrollableTracksViewportWidth() { return true; }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return getParent() instanceof javax.swing.JViewport && getParent().getHeight() > getPreferredSize().height;
    }
}
