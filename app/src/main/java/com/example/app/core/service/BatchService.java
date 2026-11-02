package com.example.app.core.service;

import com.example.app.api.model.BatchExecutionResponse;
import com.example.app.api.model.BatchStartResponse;
import com.example.app.api.model.StepExecutionResponse;
import com.example.app.core.batch.ImportFileResolver;
import com.example.app.core.web.error.BusinessException;
import com.example.app.domain.BusinessTime;
import jakarta.batch.operations.JobExecutionAlreadyCompleteException;
import jakarta.batch.operations.JobExecutionNotMostRecentException;
import jakarta.batch.operations.JobOperator;
import jakarta.batch.operations.JobRestartException;
import jakarta.batch.operations.NoSuchJobException;
import jakarta.batch.operations.NoSuchJobExecutionException;
import jakarta.batch.runtime.BatchRuntime;
import jakarta.batch.runtime.BatchStatus;
import jakarta.batch.runtime.JobExecution;
import jakarta.batch.runtime.JobInstance;
import jakarta.batch.runtime.Metric;
import jakarta.batch.runtime.StepExecution;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Properties;

/**
 * Starting, inspecting and restarting the Jakarta Batch jobs (invoice generation and order CSV import).
 *
 * <p>{@code JobOperator} runs jobs asynchronously, so starting one returns immediately.
 */
@ApplicationScoped
public class BatchService {

    /** The job XML name of the monthly invoice job ({@code META-INF/batch-jobs/invoiceJob.xml}). */
    public static final String INVOICE_JOB = "invoiceJob";
    /** The job XML name of the order CSV import ({@code META-INF/batch-jobs/orderImportJob.xml}). */
    public static final String IMPORT_JOB = "orderImportJob";

    /** Quarkus (JBeret) provides JobOperator as a CDI bean. On the EE servers it comes from {@link BatchRuntime}. */
    @Inject
    private Instance<JobOperator> jobOperatorInstance;

    @Inject
    private ImportFileResolver importFileResolver;

    /** Creates the service. CDI creates the single instance (it is application-scoped) and injects its fields. */
    public BatchService() {}

    /**
     * Starts the invoice generation job.
     *
     * @param month billing month ({@code yyyy-MM}); null or empty means last month
     * @return the new execution's ID, job name and status
     * @throws BusinessException when the month is malformed (400)
     */
    public BatchStartResponse startInvoiceJob(String month) {
        String billingMonth = normalizeMonth(month);
        Properties parameters = new Properties();
        parameters.setProperty("billingMonth", billingMonth);

        long executionId = operator().start(INVOICE_JOB, parameters);
        return startResponse(executionId, INVOICE_JOB);
    }

    /**
     * Starts the order CSV import job.
     *
     * @param inputFile a file name inside the import directory; null or empty means the bundled sample CSV
     * @return the new execution's ID, job name and status
     * @throws BusinessException when the file name is invalid or the file does not exist (400)
     */
    public BatchStartResponse startImportJob(String inputFile) {
        Properties parameters = new Properties();
        if (inputFile != null && !inputFile.isBlank()) {
            try {
                importFileResolver.resolve(inputFile);
            } catch (IllegalArgumentException exception) {
                throw BusinessException.badRequest(exception.getMessage());
            }
            parameters.setProperty("inputFile", inputFile);
        }

        long executionId = operator().start(IMPORT_JOB, parameters);
        return startResponse(executionId, IMPORT_JOB);
    }

    /**
     * Returns one execution with its step details.
     *
     * @param executionId the execution ID
     * @return the execution
     * @throws BusinessException when the execution is not found (404)
     */
    public BatchExecutionResponse getExecution(long executionId) {
        // The specification throws for an unknown ID, but be ready for implementations that return null.
        JobExecution execution;
        try {
            execution = operator().getJobExecution(executionId);
        } catch (NoSuchJobExecutionException exception) {
            execution = null;
        }
        if (execution == null) {
            throw executionNotFound(executionId);
        }
        return toResponse(execution, true);
    }

    /**
     * Returns the latest executions, newest first.
     *
     * @param jobName the job, or null or empty for both
     * @param count how many, clamped to 1..50
     * @return the executions, without step details
     */
    public List<BatchExecutionResponse> listExecutions(String jobName, int count) {
        int limit = Math.max(1, Math.min(count, 50));
        List<String> jobNames;
        if (jobName == null || jobName.isBlank()) {
            jobNames = List.of(INVOICE_JOB, IMPORT_JOB);
        } else {
            jobNames = List.of(jobName);
        }

        List<JobExecution> executions = new ArrayList<>();
        for (String name : jobNames) {
            try {
                for (JobInstance instance : operator().getJobInstances(name, 0, limit)) {
                    executions.addAll(operator().getJobExecutions(instance));
                }
            } catch (NoSuchJobException exception) {
                // Liberty throws for a job that has never been started.
            }
        }

        return executions.stream()
                .sorted(Comparator.comparingLong(JobExecution::getExecutionId).reversed())
                .limit(limit)
                .map(execution -> toResponse(execution, false))
                .toList();
    }

    /**
     * Restarts a stopped or failed execution.
     *
     * @param executionId the execution to restart
     * @return the ID of the new execution
     * @throws BusinessException execution not found (404), or not restartable, e.g. already completed (409)
     */
    public long restart(long executionId) {
        try {
            return operator().restart(executionId, new Properties());
        } catch (NoSuchJobExecutionException exception) {
            throw executionNotFound(executionId);
        } catch (JobExecutionAlreadyCompleteException exception) {
            throw BusinessException.conflict("A completed job execution cannot be restarted: " + executionId);
        } catch (JobExecutionNotMostRecentException exception) {
            throw BusinessException.conflict("Only the latest job execution can be restarted: " + executionId);
        } catch (JobRestartException exception) {
            throw BusinessException.conflict("This job execution cannot be restarted: " + executionId);
        }
    }

    private static BusinessException executionNotFound(long executionId) {
        return BusinessException.notFound("Job execution not found: " + executionId);
    }

    private JobOperator operator() {
        if (jobOperatorInstance != null && !jobOperatorInstance.isUnsatisfied()) {
            return jobOperatorInstance.get();
        }
        JobOperator operator = BatchRuntime.getJobOperator();
        if (operator == null) {
            throw new IllegalStateException("JobOperator is not available");
        }
        return operator;
    }

    private BatchStartResponse startResponse(long executionId, String jobName) {
        BatchStartResponse response = new BatchStartResponse();
        response.setExecutionId(executionId);
        response.setJobName(jobName);
        response.setStatus(statusOf(executionId));
        return response;
    }

    private String statusOf(long executionId) {
        JobExecution execution = operator().getJobExecution(executionId);
        return execution != null ? execution.getBatchStatus().name() : BatchStatus.STARTING.name();
    }

    private String normalizeMonth(String month) {
        if (month == null || month.isBlank()) {
            return BusinessTime.currentMonth().minusMonths(1).toString();
        }
        try {
            return YearMonth.parse(month).toString();
        } catch (DateTimeParseException exception) {
            throw BusinessException.badRequest("Specify the month as YYYY-MM: " + month);
        }
    }

    private BatchExecutionResponse toResponse(JobExecution execution, boolean includeSteps) {
        BatchExecutionResponse response = new BatchExecutionResponse();
        response.setExecutionId(execution.getExecutionId());
        response.setJobName(execution.getJobName());
        response.setStatus(execution.getBatchStatus().name());
        response.setExitStatus(execution.getExitStatus());
        response.setStartTime(toLocalDateTime(execution.getStartTime()));
        response.setEndTime(toLocalDateTime(execution.getEndTime()));

        if (includeSteps) {
            List<StepExecutionResponse> steps = operator().getStepExecutions(execution.getExecutionId()).stream()
                    .map(this::toStepResponse)
                    .toList();
            response.setSteps(steps);
        }
        return response;
    }

    private StepExecutionResponse toStepResponse(StepExecution step) {
        StepExecutionResponse response = new StepExecutionResponse();
        response.setStepName(step.getStepName());
        response.setStatus(step.getBatchStatus().name());
        response.setExitStatus(step.getExitStatus());
        response.setReadCount(0L);
        response.setWriteCount(0L);
        response.setCommitCount(0L);
        response.setRollbackCount(0L);
        response.setSkipCount(0L);

        for (Metric metric : step.getMetrics()) {
            switch (metric.getType()) {
                case READ_COUNT -> response.setReadCount(metric.getValue());
                case WRITE_COUNT -> response.setWriteCount(metric.getValue());
                case COMMIT_COUNT -> response.setCommitCount(metric.getValue());
                case ROLLBACK_COUNT -> response.setRollbackCount(metric.getValue());
                case READ_SKIP_COUNT, PROCESS_SKIP_COUNT, WRITE_SKIP_COUNT ->
                    response.setSkipCount(response.getSkipCount() + metric.getValue());
                default -> {
                    // FILTER_COUNT is not shown.
                }
            }
        }
        return response;
    }

    private LocalDateTime toLocalDateTime(Date date) {
        if (date == null) {
            return null;
        }
        return date.toInstant().atZone(BusinessTime.zone()).toLocalDateTime();
    }
}
