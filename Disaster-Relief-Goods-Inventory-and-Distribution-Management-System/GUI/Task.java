package GUI;

import java.sql.SQLException;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import javax.swing.SwingWorker;

/** Runs database work off the screen thread and hands the result back on it. */
final class Task {
    private Task() {
    }

    static <T> void run(Callable<T> work, Consumer<T> ok, Consumer<Throwable> failed) {
        new SwingWorker<T, Void>() {
            @Override
            protected T doInBackground() throws Exception {
                return work.call();
            }

            @Override
            protected void done() {
                try {
                    ok.accept(get());
                } catch (java.util.concurrent.ExecutionException exception) {
                    failed.accept(exception.getCause());
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            }
        }.execute();
    }

    /** Same, for work that returns nothing. */
    static void run(ThrowingRunnable work, Runnable ok, Consumer<Throwable> failed) {
        run(() -> {
            work.run();
            return Boolean.TRUE;
        }, done -> ok.run(), failed);
    }

    interface ThrowingRunnable {
        void run() throws Exception;
    }

    /** A message that is safe and useful to show on screen. */
    static String message(Throwable error) {
        if (error instanceof IllegalArgumentException || error instanceof IllegalStateException
                || error instanceof SecurityException) {
            return error.getMessage();
        }
        if (error instanceof SQLException) {
            String text = error.getMessage() == null ? "" : error.getMessage();
            int cause = text.indexOf("\n");
            if (cause > 0) {
                text = text.substring(0, cause);
            }
            return "Database problem: " + text;
        }
        return error.getClass().getSimpleName() + (error.getMessage() == null ? "" : ": " + error.getMessage());
    }
}
