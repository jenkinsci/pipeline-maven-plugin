/*
 * The MIT License
 *
 * Copyright 2018 CloudBees, Inc.
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

package org.jenkinsci.plugins.pipeline.maven.fix.jenkins49337;

import hudson.security.ACL;
import hudson.security.ACLContext;
import jenkins.model.Jenkins;
import org.acegisecurity.Authentication;
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.BodyInvoker;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;

import javax.annotation.Nonnull;

/**
 * Generalization of {@link SynchronousNonBlockingStepExecution} that can be used for {@linkplain StepDescriptor#takesImplicitBlockArgument block-scoped steps}.
 * The step may at any given time either be running CPS VM code, running background code,
 * or waiting for events (for example running a block).
 */
public abstract class GeneralNonBlockingStepExecution extends StepExecution {

    private static final long serialVersionUID = 1L;

    private transient volatile Future<?> task;
    private String threadName;
    private transient boolean stopping;

    protected GeneralNonBlockingStepExecution(StepContext context) {
        super(context);
    }

    /**
     * Block to be passed to {@link #run}.
     * like {@link Callable}{@code <}{@link Void}{@code >}, may throw an exception;
     * yet like {@link Runnable}, it need not {@code return null;} at the end.
     */
    @FunctionalInterface
    protected interface Block {
        void run() throws Exception;
    }

    /**
     * Initiate background work that should not block the CPS VM thread.
     * Call this from a CPS VM thread, such as from {@link #start} or {@link BodyExecutionCallback#onSuccess}.
     * The block may finish by calling {@link BodyInvoker#start}, {@link StepContext#onSuccess}, etc.
     * @param block some code to run in a utility thread
     */
    protected final void run(Block block) {
        if (stopping) {
            return;
        }
        final Authentication auth = Jenkins.getAuthentication();
        task = GeneralNonBlockingStepExecutionUtils.getExecutorService().submit(() -> {
            threadName = Thread.currentThread().getName();
            try {
                try (ACLContext acl = ACL.as(auth)) {
                    block.run();
                }
            } catch (Throwable e) {
                if (!stopping) {
                    getContext().onFailure(e);
                }
            } finally {
                threadName = null;
                task = null;
            }
        });
    }

    /**
     * If the computation is going synchronously, try to cancel that.
     */
    @Override
    public void stop(Throwable cause) throws Exception {
        stopping = true;
        if (task != null) {
            task.cancel(true);
        }
        super.stop(cause);
    }

    @Override
    public void onResume() {
        if (threadName != null) {
            getContext().onFailure(new Exception("Resume after a restart not supported while running background code"));
        }
    }

    @Override public @Nonnull String getStatus() {
        if (threadName != null) {
            return "running in thread: " + threadName;
        } else {
            return "not currently scheduled, or running blocks";
        }
    }

    /**
     * Variant of {@link BodyExecutionCallback.TailCall} which wraps {@link #finished} in {@link #run}.
     */
    protected abstract class TailCall extends BodyExecutionCallback {

        private static final long serialVersionUID = 1L;

        /**
         * Called when the body is finished.
         * @param context the body context as passed to {@link #onSuccess} or {@link #onFailure}
         * @throws Exception if anything is thrown here, the step fails too
         */
        protected abstract void finished(StepContext context) throws Exception;

        @Override public final void onSuccess(StepContext context, Object result) {
            run(() -> {
                try {
                    finished(context);
                } catch (Exception x) {
                    context.onFailure(x);
                    return;
                }
                context.onSuccess(result);
            });
        }

        @Override public final void onFailure(StepContext context, Throwable t) {
            run(() -> {
                try {
                    finished(context);
                } catch (Exception x) {
                    t.addSuppressed(x);
                }
                context.onFailure(t);
            });
        }

    }

}
