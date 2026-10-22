package GUI;

import GUI.Theme.Tone;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Font;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;

/** Painting helpers shared by the custom components. */
final class Gfx {
    private Gfx() {
    }

    static Graphics2D g2(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        return g2;
    }

    static void roundBox(Graphics2D g2, int width, int height, int arc, Color fill, Color border) {
        if (fill != null) {
            g2.setColor(fill);
            g2.fill(new RoundRectangle2D.Float(0, 0, width, height, arc, arc));
        }
        if (border != null) {
            g2.setColor(border);
            g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, width - 1, height - 1, arc, arc));
        }
    }

    /** Draws text so that its visual centre sits on the middle of the given box. */
    static void centered(Graphics2D g2, String text, int x, int y, int width, int height) {
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(text, x + (width - fm.stringWidth(text)) / 2, y + (height - fm.getHeight()) / 2 + fm.getAscent());
    }
}

/** Word-wrapped read-only text whose height follows its width. */
class Wrap extends JComponent implements HFW {
    private String text;
    private final Font font;
    private Color color;
    private int maxPreferred = 640;

    Wrap(String text, Font font, Color color) {
        this.text = text == null ? "" : text;
        this.font = font;
        this.color = color;
        setOpaque(false);
        setFont(font);
    }

    Wrap maxWidth(int width) {
        this.maxPreferred = width;
        return this;
    }

    void setText(String text) {
        this.text = text == null ? "" : text;
        revalidate();
        repaint();
    }

    void setColor(Color color) {
        this.color = color;
        repaint();
    }

    private int lineHeight() {
        return Math.round(font.getSize2D() * 1.5f);
    }

    private List<String> lines(int width) {
        FontMetrics fm = getFontMetrics(font);
        List<String> out = new ArrayList<>();
        for (String paragraph : text.split("\n", -1)) {
            if (width <= 0) {
                out.add(paragraph);
                continue;
            }
            StringBuilder line = new StringBuilder();
            for (String word : paragraph.split(" ")) {
                String candidate = line.length() == 0 ? word : line + " " + word;
                if (fm.stringWidth(candidate) <= width || line.length() == 0) {
                    line = new StringBuilder(candidate);
                } else {
                    out.add(line.toString());
                    line = new StringBuilder(word);
                }
            }
            out.add(line.toString());
        }
        return out;
    }

    @Override
    public int heightForWidth(int width) {
        return Math.max(1, lines(width).size()) * lineHeight();
    }

    @Override
    public Dimension getPreferredSize() {
        FontMetrics fm = getFontMetrics(font);
        int width = 0;
        for (String paragraph : text.split("\n", -1)) {
            width = Math.max(width, fm.stringWidth(paragraph));
        }
        width = Math.min(width, maxPreferred);
        return new Dimension(width, heightForWidth(width));
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Gfx.g2(g);
        g2.setFont(font);
        g2.setColor(color);
        FontMetrics fm = g2.getFontMetrics();
        int lineHeight = lineHeight();
        int y = (lineHeight - fm.getHeight()) / 2 + fm.getAscent();
        for (String line : lines(getWidth())) {
            g2.drawString(line, 0, y);
            y += lineHeight;
        }
        g2.dispose();
    }
}

/** Buttons in the four styles of the design. */
class Btn extends JButton {
    enum Kind { PRIMARY, SECONDARY, DANGER, LINK }

    private final Kind kind;

    Btn(String text, Kind kind) {
        super(text);
        this.kind = kind;
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setOpaque(false);
        setRolloverEnabled(true);
        setFont(Theme.semibold(13));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    @Override
    public Dimension getPreferredSize() {
        FontMetrics fm = getFontMetrics(getFont());
        int padding = kind == Kind.LINK ? 2 : 36;
        return new Dimension(fm.stringWidth(getText()) + padding, kind == Kind.LINK ? 24 : 38);
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        setCursor(Cursor.getPredefinedCursor(enabled ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Gfx.g2(g);
        boolean hover = getModel().isRollover() || getModel().isPressed();
        Color text;
        if (!isEnabled()) {
            Gfx.roundBox(g2, getWidth(), getHeight(), 12, Theme.DISABLED_BG, null);
            text = Theme.DISABLED_TEXT;
        } else if (kind == Kind.PRIMARY) {
            Gfx.roundBox(g2, getWidth(), getHeight(), 12, hover ? Theme.TEAL_HOVER : Theme.TEAL, null);
            text = Color.WHITE;
        } else if (kind == Kind.DANGER) {
            Gfx.roundBox(g2, getWidth(), getHeight(), 12, hover ? Theme.DANGER_HOVER : Theme.DANGER_TEXT, null);
            text = Color.WHITE;
        } else if (kind == Kind.SECONDARY) {
            Gfx.roundBox(g2, getWidth(), getHeight(), 12, hover ? Theme.HEADER_BG : Color.WHITE, Theme.BORDER);
            text = Theme.TEXT;
        } else {
            text = hover ? Theme.TEAL_HOVER : Theme.TEAL;
        }
        g2.setFont(getFont());
        g2.setColor(text);
        Gfx.centered(g2, getText(), 0, 0, getWidth(), getHeight());
        if (kind == Kind.LINK && hover && isEnabled()) {
            FontMetrics fm = g2.getFontMetrics();
            int x = (getWidth() - fm.stringWidth(getText())) / 2;
            int y = (getHeight() + fm.getHeight()) / 2 + 1;
            g2.drawLine(x, y, x + fm.stringWidth(getText()), y);
        }
        g2.dispose();
    }
}

/** A small rounded status label. */
class Pill extends JComponent {
    private String text;
    private Tone tone;

    Pill(String text, Tone tone) {
        this.text = text;
        this.tone = tone;
        setOpaque(false);
        setFont(Theme.semibold(11));
    }

    void set(String text, Tone tone) {
        this.text = text;
        this.tone = tone;
        revalidate();
        repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(getFontMetrics(getFont()).stringWidth(text) + 20, 22);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Gfx.g2(g);
        Gfx.roundBox(g2, getWidth(), getHeight(), getHeight(), tone.bg(), null);
        g2.setFont(getFont());
        g2.setColor(tone.text());
        Gfx.centered(g2, text, 0, 0, getWidth(), getHeight());
        g2.dispose();
    }
}

/** A white rounded panel with a thin border, the basic container of every screen. */
class Card extends VBox {
    private Color fill = Color.WHITE;
    private Color edge = Theme.BORDER;

    Card(int gap) {
        super(gap);
        pad(22, 24, 24, 24);
    }

    Card colors(Color fill, Color edge) {
        this.fill = fill;
        this.edge = edge;
        return this;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Gfx.g2(g);
        Gfx.roundBox(g2, getWidth(), getHeight(), 16, fill, edge);
        g2.dispose();
    }
}

/** A coloured message box with an accent bar, a title and an optional action button. */
class Alert extends VBox {
    private final Tone tone;

    Alert(Tone tone, String title, String message, Component action) {
        super(0);
        this.tone = tone;
        pad(14, 22, 14, 16);
        VBox text = new VBox(2);
        JLabel heading = new JLabel(title);
        heading.setFont(Theme.semibold(13));
        heading.setForeground(tone.text());
        text.put(heading);
        if (message != null && !message.isEmpty()) {
            text.put(new Wrap(message, Theme.regular(12.5f), tone.text()).maxWidth(900));
        }
        if (action == null) {
            put(text);
        } else {
            put(new Cols(16, 1, 0).put(text, action));
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Gfx.g2(g);
        g2.setClip(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 12, 12));
        g2.setColor(tone.bg());
        g2.fillRect(0, 0, getWidth(), getHeight());
        g2.setColor(tone.accent());
        g2.fillRect(0, 0, 4, getHeight());
        g2.setClip(null);
        g2.setColor(tone.border());
        g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1, getHeight() - 1, 12, 12));
        g2.dispose();
    }
}

/** A thin horizontal progress bar. */
class Bar extends JComponent {
    private final double value;

    Bar(double value) {
        this.value = Math.max(0, Math.min(1, value));
        setOpaque(false);
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(100, 10);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Gfx.g2(g);
        Gfx.roundBox(g2, getWidth(), getHeight(), getHeight(), new Color(0xE3E9EB), null);
        if (value > 0) {
            Gfx.roundBox(g2, Math.max(getHeight(), (int) (getWidth() * value)), getHeight(), getHeight(), Theme.TEAL, null);
        }
        g2.dispose();
    }
}

/** A one-pixel divider line. */
class Rule extends JComponent {
    Rule() {
        setOpaque(false);
    }

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
