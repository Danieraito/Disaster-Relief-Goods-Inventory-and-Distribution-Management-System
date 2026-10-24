package GUI;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;

/** Single-line text input without a frame (the Field around it draws the frame). */
class Input extends JTextField {
    private String placeholder = "";

    Input(String text) {
        super(text == null ? "" : text);
        setBorder(null);
        setOpaque(false);
        setFont(Theme.regular(13));
        setForeground(Theme.TEXT);
        setCaretColor(Theme.TEAL);
        setSelectionColor(Theme.TEAL_BORDER);
        setSelectedTextColor(Theme.TEXT);
    }

    Input placeholder(String text) {
        this.placeholder = text;
        return this;
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(super.getPreferredSize().width, 22);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (getText().isEmpty() && !placeholder.isEmpty() && !isFocusOwner()) {
            Graphics2D g2 = Gfx.g2(g);
            g2.setFont(getFont());
            g2.setColor(Theme.SUBTLE);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(placeholder, getInsets().left, (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
            g2.dispose();
        }
    }
}

/** Multi-line text input without a frame. */
class Area extends JTextArea {
    Area(String text, int rows) {
        super(text == null ? "" : text, rows, 20);
        setBorder(null);
        setOpaque(false);
        setLineWrap(true);
        setWrapStyleWord(true);
        setFont(Theme.regular(13));
        setForeground(Theme.TEXT);
        setCaretColor(Theme.TEAL);
        setSelectionColor(Theme.TEAL_BORDER);
        setSelectedTextColor(Theme.TEXT);
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(100, getRows() * getRowHeight() + 2);
    }
}

/** A check box drawn in the teal style of the design. */
class Check extends JCheckBox {
    private static final Icon OFF = new Box(false);
    private static final Icon ON = new Box(true);

    Check(String text) {
        super(text);
        setIcon(OFF);
        setSelectedIcon(ON);
        setFocusPainted(false);
        setOpaque(false);
        setFont(Theme.regular(13));
        setForeground(Theme.BODY);
        setIconTextGap(9);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    private static final class Box implements Icon {
        private final boolean checked;

        Box(boolean checked) {
            this.checked = checked;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = Gfx.g2(g);
            g2.translate(x, y);
            if (checked) {
                g2.setColor(Theme.TEAL);
                g2.fillRoundRect(0, 0, 16, 16, 5, 5);
                g2.setColor(Color.WHITE);
                g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.drawLine(4, 8, 7, 11);
                g2.drawLine(7, 11, 12, 5);
            } else {
                g2.setColor(Color.WHITE);
                g2.fillRoundRect(0, 0, 16, 16, 5, 5);
                g2.setColor(new Color(0xB4C0C5));
                g2.drawRoundRect(0, 0, 15, 15, 5, 5);
            }
            g2.dispose();
        }

        @Override public int getIconWidth() { return 16; }
        @Override public int getIconHeight() { return 16; }
    }
}

/** A bordered input cell with the label inside the top edge, like the form fields of the design. */
class Field extends VBox {
    private final JComponent input;
    private final Wrap helpText;
    private Wrap errorText;
    private boolean focused;
    private boolean readOnly;
    private boolean error;

    Field(String label, JComponent input) {
        this(label, input, null);
    }

    Field(String label, JComponent input, String help) {
        super(4);
        this.input = input;
        if (label == null) {
            pad(10, 16, 10, 16);
            put(input);
        } else {
            pad(13, 16, 14, 16);
            JLabel caption = new JLabel(label);
            caption.setFont(Theme.medium(11.5f));
            caption.setForeground(Theme.MUTED);
            put(caption, input);
        }
        helpText = help == null ? null : new Wrap(help, Theme.regular(11.5f), Theme.MUTED);
        if (helpText != null) {
            put(helpText);
        }
        FocusAdapterRepaint listener = new FocusAdapterRepaint();
        input.addFocusListener(listener);
        for (Component child : input.getComponents()) {
            child.addFocusListener(listener);
        }
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                if (input.isEnabled()) {
                    input.requestFocusInWindow();
                }
            }
        });
    }

    private final class FocusAdapterRepaint extends FocusAdapter {
        @Override
        public void focusGained(FocusEvent event) {
            focused = true;
            repaint();
        }

        @Override
        public void focusLost(FocusEvent event) {
            focused = false;
            repaint();
        }
    }

    JComponent input() {
        return input;
    }

    Field readOnly() {
        readOnly = true;
        input.setEnabled(false);
        if (input instanceof JTextField) {
            ((JTextField) input).setEditable(false);
            input.setEnabled(true);
        }
        return this;
    }

    /** Shows a red message under the value and turns the border red; null clears it. */
    Field error(String message) {
        error = message != null;
        if (errorText != null) {
            remove(errorText);
            errorText = null;
        }
        if (message != null) {
            errorText = new Wrap(message, Theme.regular(11.5f), Theme.DANGER_TEXT);
            add(errorText);
        }
        revalidate();
        repaint();
        return this;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Gfx.g2(g);
        Color border = error ? Theme.DANGER_TEXT : focused ? Theme.TEAL : Theme.BORDER;
        Gfx.roundBox(g2, getWidth(), getHeight(), 14, readOnly ? Theme.HEADER_BG : Color.WHITE, border);
        if (focused && !error) {
            g2.setColor(new Color(8, 127, 120, 60));
            g2.draw(new java.awt.geom.RoundRectangle2D.Float(1.5f, 1.5f, getWidth() - 3, getHeight() - 3, 12, 12));
        }
        g2.dispose();
    }
}

/** A drop-down list drawn in the style of the design (value on the left, chevron on the right). */
class Dropdown<T> extends JComponent {
    private List<T> options = new ArrayList<>();
    private T selected;
    private final Function<T, String> label;
    private String placeholder = "Select";
    private final List<Runnable> listeners = new ArrayList<>();
    private JPopupMenu popup;

    Dropdown(List<T> options, Function<T, String> label) {
        this.label = label;
        this.options = new ArrayList<>(options);
        if (!this.options.isEmpty()) {
            selected = this.options.get(0);
        }
        setOpaque(false);
        setFont(Theme.regular(13));
        setFocusable(true);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                if (isEnabled()) {
                    requestFocusInWindow();
                    toggle();
                }
            }
        });
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent event) {
                if (!isEnabled()) {
                    return;
                }
                int index = Dropdown.this.options.indexOf(selected);
                if (event.getKeyCode() == KeyEvent.VK_DOWN && index < Dropdown.this.options.size() - 1) {
                    select(Dropdown.this.options.get(index + 1));
                } else if (event.getKeyCode() == KeyEvent.VK_UP && index > 0) {
                    select(Dropdown.this.options.get(index - 1));
                } else if (event.getKeyCode() == KeyEvent.VK_ENTER || event.getKeyCode() == KeyEvent.VK_SPACE) {
                    toggle();
                }
            }
        });
    }

    static Dropdown<String> of(String... values) {
        return new Dropdown<>(java.util.Arrays.asList(values), value -> value);
    }

    Dropdown<T> placeholder(String text) {
        this.placeholder = text;
        return this;
    }

    Dropdown<T> noSelection() {
        selected = null;
        return this;
    }

    T getSelected() {
        return selected;
    }

    void setSelected(T value) {
        selected = value;
        repaint();
    }

    /** Selects a value and tells the listeners. */
    void select(T value) {
        selected = value;
        repaint();
        for (Runnable listener : new ArrayList<>(listeners)) {
            listener.run();
        }
    }

    void setOptions(List<T> values) {
        options = new ArrayList<>(values);
        if (selected != null && !options.contains(selected)) {
            selected = null;
        }
        repaint();
    }

    Dropdown<T> onChange(Runnable listener) {
        listeners.add(listener);
        return this;
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(140, 22);
    }

    private void toggle() {
        if (popup != null && popup.isVisible()) {
            popup.setVisible(false);
            return;
        }
        if (options.isEmpty()) {
            return;
        }
        Component anchor = SwingUtilities.getAncestorOfClass(Field.class, this);
        if (anchor == null) {
            anchor = this;
        }
        DefaultListModel<T> model = new DefaultListModel<>();
        for (T option : options) {
            model.addElement(option);
        }
        JList<T> list = new JList<>(model);
        list.setFixedCellHeight(30);
        list.setSelectedValue(selected, true);
        list.setCellRenderer((ListCellRenderer<T>) (jlist, value, index, isSelected, focus) -> {
            JLabel row = new JLabel(label.apply(value));
            row.setOpaque(true);
            row.setFont(Theme.regular(13));
            row.setBorder(new EmptyBorder(0, 14, 0, 14));
            row.setBackground(isSelected ? Theme.TEAL_TINT : Color.WHITE);
            row.setForeground(isSelected ? Theme.TEAL : Theme.TEXT);
            return row;
        });
        popup = new JPopupMenu();
        popup.setBorder(new LineBorder(Theme.BORDER));
        popup.setBackground(Color.WHITE);
        list.addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent event) {
                int index = list.locationToIndex(event.getPoint());
                if (index >= 0 && index != list.getSelectedIndex()) {
                    list.setSelectedIndex(index);
                }
            }
        });
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent event) {
                int index = list.locationToIndex(event.getPoint());
                if (index >= 0) {
                    popup.setVisible(false);
                    select(model.get(index));
                }
            }
        });
        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(30);
        int height = Math.min(options.size(), 8) * 30 + 2;
        scroll.setPreferredSize(new Dimension(Math.max(anchor.getWidth(), 180), height));
        popup.add(scroll);
        popup.show(anchor, 0, anchor.getHeight() + 2);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Gfx.g2(g);
        g2.setFont(getFont());
        FontMetrics fm = g2.getFontMetrics();
        String text = selected == null ? placeholder : label.apply(selected);
        g2.setColor(!isEnabled() ? Theme.DISABLED_TEXT : selected == null ? Theme.SUBTLE : Theme.TEXT);
        g2.drawString(clip(text, fm, getWidth() - 26), 0, (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
        g2.setColor(Theme.MUTED);
        g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Path2D chevron = new Path2D.Float();
        int x = getWidth() - 12;
        int y = getHeight() / 2 - 1;
        chevron.moveTo(x - 4, y - 2);
        chevron.lineTo(x, y + 2);
        chevron.lineTo(x + 4, y - 2);
        g2.draw(chevron);
        g2.dispose();
    }

    static String clip(String text, FontMetrics fm, int width) {
        if (fm.stringWidth(text) <= width) {
            return text;
        }
        String clipped = text;
        while (clipped.length() > 1 && fm.stringWidth(clipped + "…") > width) {
            clipped = clipped.substring(0, clipped.length() - 1);
        }
        return clipped + "…";
    }
}

/** A drop-down with a check box per option (special categories, affected areas). */
class MultiSelect extends JComponent {
    private final List<String> options;
    private final Set<String> selected = new LinkedHashSet<>();
    private final String empty;
    private final List<Runnable> listeners = new ArrayList<>();
    private JPopupMenu popup;

    MultiSelect(List<String> options, String empty) {
        this.options = options;
        this.empty = empty;
        setOpaque(false);
        setFont(Theme.regular(13));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                if (isEnabled()) {
                    toggle();
                }
            }
        });
    }

    Set<String> getSelected() {
        return new LinkedHashSet<>(selected);
    }

    void setSelected(Set<String> values) {
        selected.clear();
        for (String option : options) {
            if (values.contains(option)) {
                selected.add(option);
            }
        }
        repaint();
    }

    MultiSelect onChange(Runnable listener) {
        listeners.add(listener);
        return this;
    }

    String summary() {
        return selected.isEmpty() ? empty : String.join(", ", selected);
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(140, 22);
    }

    private void toggle() {
        if (popup != null && popup.isVisible()) {
            popup.setVisible(false);
            return;
        }
        Component anchor = SwingUtilities.getAncestorOfClass(Field.class, this);
        if (anchor == null) {
            anchor = this;
        }
        popup = new JPopupMenu();
        popup.setBorder(new LineBorder(Theme.BORDER));
        popup.setBackground(Color.WHITE);
        VBox box = new VBox(2).pad(8, 14, 8, 14);
        box.setOpaque(true);
        box.setBackground(Color.WHITE);
        for (String option : options) {
            Check check = new Check(option);
            check.setSelected(selected.contains(option));
            check.addActionListener(event -> {
                if (check.isSelected()) {
                    selected.add(option);
                } else {
                    selected.remove(option);
                }
                repaint();
                for (Runnable listener : new ArrayList<>(listeners)) {
                    listener.run();
                }
            });
            box.put(check);
        }
        popup.add(box);
        popup.setPreferredSize(new Dimension(Math.max(anchor.getWidth(), 200), box.heightForWidth(220) + 2));
        popup.show(anchor, 0, anchor.getHeight() + 2);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Gfx.g2(g);
        g2.setFont(getFont());
        FontMetrics fm = g2.getFontMetrics();
        g2.setColor(selected.isEmpty() ? Theme.SUBTLE : Theme.TEXT);
        g2.drawString(Dropdown.clip(summary(), fm, getWidth() - 26), 0, (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
        g2.setColor(Theme.MUTED);
        g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Path2D chevron = new Path2D.Float();
        int x = getWidth() - 12;
        int y = getHeight() / 2 - 1;
        chevron.moveTo(x - 4, y - 2);
        chevron.lineTo(x, y + 2);
        chevron.lineTo(x + 4, y - 2);
        g2.draw(chevron);
        g2.dispose();
    }
}
