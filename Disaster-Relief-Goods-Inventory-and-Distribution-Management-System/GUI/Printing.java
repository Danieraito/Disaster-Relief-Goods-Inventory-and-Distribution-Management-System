package GUI;

import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;

/** Sends a component (a receipt) to the printer, scaled to fit one page. */
final class Printing {
    private Printing() {
    }

    static void print(Component parent, Component receipt, String jobName) {
        PrinterJob job = PrinterJob.getPrinterJob();
        job.setJobName(jobName);
        job.setPrintable(new Printable() {
            @Override
            public int print(Graphics graphics, PageFormat format, int page) {
                if (page > 0) {
                    return NO_SUCH_PAGE;
                }
                Graphics2D g2 = (Graphics2D) graphics;
                g2.translate(format.getImageableX(), format.getImageableY());
                double scale = Math.min(format.getImageableWidth() / Math.max(1, receipt.getWidth()),
                        format.getImageableHeight() / Math.max(1, receipt.getHeight()));
                if (scale < 1) {
                    g2.scale(scale, scale);
                }
                receipt.printAll(g2);
                return PAGE_EXISTS;
            }
        });
        try {
            if (job.printDialog()) {
                job.print();
            }
        } catch (PrinterException error) {
            Dialogs.error(parent, error);
        }
    }
}
