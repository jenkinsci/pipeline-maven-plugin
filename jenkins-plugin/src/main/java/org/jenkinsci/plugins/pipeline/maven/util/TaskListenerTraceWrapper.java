package org.jenkinsci.plugins.pipeline.maven.util;

import hudson.model.TaskListener;
import java.io.IOException;
import java.io.PrintStream;

/**
 * This class provides methods which wrap the original {@link TaskListener}.
 * All {@code trace*} methods check if traceability is enabled before doing anything.
 */
public class TaskListenerTraceWrapper {

  private final TaskListener taskListener;
  private final boolean traceability;
  private final PrintStream console;


  /**
   * Wrap the given TaskListener.
   *
   * @param taskListener the wrapped listener
   * @param traceability the boolean flag for traceability
   */
  public TaskListenerTraceWrapper(final TaskListener taskListener, final boolean traceability) {
    this.taskListener = taskListener;
    this.traceability = traceability;
    this.console = taskListener.getLogger();
  }


  /**
   * Prints the given String to the underlying TaskListener if traceability is enabled.
   * @param s the string to print
   */
  public void trace(final String s) {
    if (traceability) {
      console.println(s);
    }
  }

  /**
   * Prints the given Object to the underlying TaskListener if traceability is enabled.
   * @param o the object to print
   */
  public void trace(final Object o) {
    if (traceability) {
      console.println(o);
    }
  }

  /**
   * Wraps the {@link TaskListener#hyperlink(String, String)} function. If traceability is disabled do nothing.
   * @param url {@link TaskListener#hyperlink}
   * @param text {@link TaskListener#hyperlink}
   */

  public void traceHyperlink(final String url, final String text) throws IOException {
    if (traceability) {
      taskListener.hyperlink(url, text);
    }
  }

  /**
   * Wraps {@link TaskListener#getLogger()} println calls.
   * @param s
   */
  public void println(final CharSequence s) {
      console.println(s);
  }

  /**
   * Wraps {@link TaskListener#getLogger()} format calls.
   * @param format
   * @param args
   * @return
   */
  public PrintStream format(final String format, Object ... args ) {
      return console.format(format, args);
  }


  /**
   * Wraps {@link TaskListener#getLogger()} format calls.
   * If traceability is disabled do nothing.
   * @param format
   * @param args
   * @return
   */
  public PrintStream formatTrace(final String format,Object ... args ) {
    if (traceability) {
      return console.format(format, args);
    }
    return console;
  }
}
