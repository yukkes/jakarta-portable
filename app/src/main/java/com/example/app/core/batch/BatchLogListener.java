package com.example.app.core.batch;

import jakarta.batch.api.listener.JobListener;
import jakarta.batch.api.listener.StepListener;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Named;
import java.util.logging.Logger;

/** Listener that logs the start and end of jobs and steps. */
@Dependent
@Named("batchLogListener")
public class BatchLogListener implements JobListener, StepListener {

    private static final Logger LOG = Logger.getLogger(BatchLogListener.class.getName());

    /** Creates the listener. The batch runtime creates it per job or step (it is a {@code @Named} batch artifact). */
    public BatchLogListener() {}

    @Override
    public void beforeJob() {
        LOG.info("[batch] job started");
    }

    @Override
    public void afterJob() {
        LOG.info("[batch] job finished");
    }

    @Override
    public void beforeStep() {
        LOG.info("[batch] step started");
    }

    @Override
    public void afterStep() {
        LOG.info("[batch] step finished");
    }
}
