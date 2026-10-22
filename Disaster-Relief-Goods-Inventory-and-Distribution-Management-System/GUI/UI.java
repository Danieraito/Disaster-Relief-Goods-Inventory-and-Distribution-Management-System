package GUI;

import GUI.Theme.Tone;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.List;
import java.util.function.IntConsumer;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

/** Short factory methods so screens read like the design. */
final class UI {
    private UI() {
    }

    // ---- text
    static JLabel label(String text, Font font, Color color) {
        JLabel label = new JLabel(text == null ? "" : text);
        label.setFont(font);
        label.setForeground(color);
        return label;
    }

    static JLabel h1(String text) {
        return label(text, Theme.semibold(28), Theme.TEXT);
    }

    static JLabel h2(String text) {
        return label(text, Theme.semibold(17), Theme.TEXT);
    }

    static JLabel h3(String text) {
        return label(text, Theme.semibold(14), Theme.TEXT);
    }

    static JLabel body(String text) {
        return label(text, Theme.regular(13), Theme.TEXT);
    }

    static JLabel strong(String text) {
        return label(text, Theme.semibold(13), Theme.TEXT);
    }

    static JLabel muted(String text) {
        return label(text, Theme.regular(12.5f), Theme.MUTED);
    }

    static JLabel small(String text) {
        return label(text, Theme.regular(11.5f), Theme.MUTED);
    }

    static JLabel tone(String text, Font font, Tone tone) {
        return label(text, font, tone.text());
    }

    static Wrap para(String text) {
        return new Wrap(text, Theme.regular(13), Theme.BODY);
    }

    static Wrap mutedPara(String text) {
        return new Wrap(text, Theme.regular(12.5f), Theme.MUTED);
    }

    // ---- buttons
    static Btn primary(String text) {
        return new Btn(text, Btn.Kind.PRIMARY);
    }

    static Btn secondary(String text) {
        return new Btn(text, Btn.Kind.SECONDARY);
    }

    static Btn danger(String text) {
        return new Btn(text, Btn.Kind.DANGER);
    }

    static Btn link(String text) {
        return new Btn(text, Btn.Kind.LINK);
    }

    static Btn primary(String text, Runnable action) {
        Btn button = primary(text);
        button.addActionListener(event -> action.run());
        return button;
    }

    static Btn secondary(String text, Runnable action) {
        Btn button = secondary(text);
        button.addActionListener(event -> action.run());
        return button;
    }

    static Btn link(String text, Runnable action) {
        Btn button = link(text);
        button.addActionListener(event -> action.run());
        return button;
    }

    static Pill pill(String text, Tone tone) {
        return new Pill(text, tone);
    }

    // ---- layout pieces
    static Component gap(int height) {
        return Lay.space(height);
    }

    /** A panel that keeps its child at its natural width, flush left. */
    static JPanel left(Component child) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0)) {
            @Override
            public java.awt.Dimension getPreferredSize() {
                return child.getPreferredSize();
            }
        };
        panel.setOpaque(false);
        panel.add(child);
        return panel;
    }

    /** Title on the left, actions on the right. */
    static Cols titleRow(Component title, Component actions) {
        Cols row = new Cols(16, 1, 0);
        JPanel empty = new JPanel();
        empty.setOpaque(false);
        row.put(title, actions == null ? empty : actions);
        return row;
    }

    static Row actions(Component... buttons) {
        return new Row(10).put(buttons);
    }

    static Row rightActions(Component... buttons) {
        return new Row(10).right().put(buttons);
    }

    /** Page heading: title, subtitle and (optionally) action buttons on the right. */
    static Cols pageHeader(String title, String subtitle, Component actions) {
        VBox text = new VBox(6);
        text.put(h1(title));
        if (subtitle != null && !subtitle.isEmpty()) {
            text.put(muted(subtitle));
        }
        return titleRow(text, actions);
    }

    /** A card with a heading and a short description, followed by the given body. */
    static Card section(String title, String subtitle, Component... body) {
        Card card = new Card(14);
        VBox head = new VBox(4);
        head.put(h2(title));
        if (subtitle != null && !subtitle.isEmpty()) {
            head.put(muted(subtitle));
        }
        card.put(head);
        for (Component part : body) {
            card.put(part);
        }
        return card;
    }

    static Card sectionAction(String title, String subtitle, Component action, Component... body) {
        Card card = new Card(14);
        VBox head = new VBox(4);
        head.put(h2(title));
        if (subtitle != null && !subtitle.isEmpty()) {
            head.put(muted(subtitle));
        }
        card.put(action == null ? head : titleRow(head, action));
        for (Component part : body) {
            card.put(part);
        }
        return card;
    }

    static Card stat(String label, String value, String caption, Tone tone) {
        Card card = new Card(6);
        card.pad(20, 22, 20, 22);
        JLabel number = label(value, Theme.semibold(32), tone == Tone.NEUTRAL ? Theme.TEXT
                : tone == Tone.TEAL ? Theme.TEAL : tone.text());
        card.put(muted(label), number, small(caption));
        return card;
    }

    static Alert alert(Tone tone, String title, String message) {
        return new Alert(tone, title, message, null);
    }

    static Alert alert(Tone tone, String title, String message, Component action) {
        return new Alert(tone, title, message, action);
    }

    /** "Key · value" lines used by the summary cards; long values wrap. */
    static Cols fact(String key, String value) {
        Cols row = new Cols(12, -150, 1);
        row.put(label(key, Theme.regular(12.5f), Theme.MUTED),
                new Wrap(value, Theme.medium(12.5f), Theme.TEXT).maxWidth(900));
        return row;
    }

    static Wrap smallPara(String text) {
        return new Wrap(text, Theme.regular(11.5f), Theme.MUTED).maxWidth(900);
    }

    static Tabs tabs(List<String> labels, int selected, IntConsumer onSelect) {
        return new Tabs(labels, selected, onSelect);
    }

    static Component rule() {
        return new Rule();
    }

    /** A framed block (light fill) for notes inside a card. */
    static Card note(Tone tone, String title, String message) {
        Card card = new Card(4);
        card.pad(14, 18, 14, 18);
        card.colors(tone.bg(), tone.border());
        card.put(label(title, Theme.semibold(12.5f), tone.text()), new Wrap(message, Theme.regular(12.5f), tone.text()));
        return card;
    }

    /** Previous / next controls under a table. */
    static Cols pager(int page, int pages, String summary, Runnable previous, Runnable next) {
        Btn prev = secondary("Previous");
        prev.setEnabled(page > 1);
        prev.addActionListener(event -> previous.run());
        Btn following = secondary("Next");
        following.setEnabled(page < pages);
        following.addActionListener(event -> next.run());
        return titleRow(small(summary), actions(prev, following));
    }

    /** A page-wide colored band, used for the "selected disaster" strip. */
    static VBox band(Component content) {
        VBox box = new VBox(0) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = Gfx.g2(g);
                Gfx.roundBox(g2, getWidth(), getHeight(), 14, Theme.TEAL_TINT, Theme.TEAL_BORDER);
                g2.dispose();
            }
        };
        box.pad(14, 20, 14, 20);
        box.put(content);
        return box;
    }

    static EmptyBorder pad(int top, int left, int bottom, int right) {
        return new EmptyBorder(top, left, bottom, right);
    }
}
