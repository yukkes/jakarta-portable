package com.example.app.core.batch;

import com.example.app.api.model.CreateOrderLine;
import com.example.app.api.model.CreateOrderRequest;
import com.example.app.api.model.OrderResponse;
import com.example.app.core.service.OrderImportService;
import com.example.app.core.web.error.BusinessException;
import com.example.app.domain.BusinessTime;
import jakarta.batch.api.BatchProperty;
import jakarta.batch.api.Batchlet;
import jakarta.batch.runtime.context.JobContext;
import jakarta.batch.runtime.context.StepContext;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Batchlet that imports the order CSV line by line, one transaction per line.
 *
 * <p>It records each line's deduplication key ({@link ImportCsvLine#importKey}), so a rerun after an interruption
 * does not import committed lines again. The counts go to the exit status (for example
 * {@code COMPLETED created=3 skipped=0 failed=0}) and to the log.
 */
@Dependent
@Named("orderImportBatchlet")
public class OrderImportBatchlet implements Batchlet {

    private static final Logger LOG = Logger.getLogger(OrderImportBatchlet.class.getName());
    private static final String CLASSPATH_CSV = "/data/orders-import.csv";

    @Inject
    private OrderImportService orderImportService;

    @Inject
    private ImportFileResolver importFileResolver;

    @Inject
    private JobContext jobContext;

    @Inject
    private StepContext stepContext;

    @Inject
    @BatchProperty(name = "inputFile")
    private String inputFile;

    private volatile boolean stopped;

    /**
     * Creates the batchlet. The batch runtime creates it per step (it is a {@code @Named} batch artifact) and injects
     * its fields.
     */
    public OrderImportBatchlet() {}

    @Override
    public String process() throws Exception {
        String source = sourceName();
        int created = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();
        // Occurrences of each identical line, keyed by the first occurrence's key: 64 characters per line whatever its
        // length.
        Map<String, Integer> occurrences = new HashMap<>();

        // The file size is unknown, so read one line at a time. A stop request is honored within one line as well.
        try (BufferedReader reader = open(source)) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (stopped) {
                    errors.add("stopped by request");
                    break;
                }
                if (ImportCsvLine.isIgnorable(line)) {
                    continue;
                }
                // Count before parsing, so the numbering stays the same on a rerun even if a line in between fails.
                int occurrence = occurrences.merge(ImportCsvLine.importKey(source, line), 1, Integer::sum);
                try {
                    ImportCsvLine row = ImportCsvLine.parse(line);
                    Optional<OrderResponse> imported = orderImportService.importOrder(
                            ImportCsvLine.importKey(source, line, occurrence), toRequest(row));
                    if (imported.isPresent()) {
                        created++;
                    } else {
                        skipped++;
                    }
                } catch (RuntimeException exception) {
                    // The line content is external data, so it is never put into the error message.
                    errors.add("line " + lineNumber + ": " + describe(exception));
                    LOG.log(Level.WARNING, "import failed at line {0}: {1}", new Object[] {
                        lineNumber, exception.getClass().getSimpleName()
                    });
                }
            }
        }

        LOG.log(Level.INFO, "import finished: source={0}, created={1}, skipped={2}, failed={3}", new Object[] {
            source, created, skipped, errors.size()
        });
        for (String error : errors) {
            LOG.log(Level.WARNING, "import error: {0}", error);
        }
        // Also set it as the job's exit status. Otherwise it is the batch status name and the API shows no counts.
        String exitStatus = (errors.isEmpty() ? "COMPLETED" : "COMPLETED_WITH_ERRORS") + " created=" + created
                + " skipped=" + skipped + " failed=" + errors.size();
        stepContext.setExitStatus(exitStatus);
        jobContext.setExitStatus(exitStatus);
        return exitStatus;
    }

    @Override
    public void stop() {
        stopped = true;
    }

    private static CreateOrderRequest toRequest(ImportCsvLine row) {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setCustomerCode(row.customerCode());
        request.setOrderDate(row.orderDate() != null ? row.orderDate() : BusinessTime.today());
        CreateOrderLine orderLine = new CreateOrderLine();
        orderLine.setProductCode(row.productCode());
        orderLine.setQuantity(row.quantity());
        request.setLines(List.of(orderLine));
        return request;
    }

    private String describe(RuntimeException exception) {
        if (exception instanceof BusinessException || exception instanceof CsvFormatException) {
            return exception.getMessage();
        }
        return "Import failed (" + exception.getClass().getSimpleName() + ")";
    }

    private String sourceName() {
        return inputFile != null && !inputFile.isBlank() ? inputFile : CLASSPATH_CSV;
    }

    private BufferedReader open(String source) throws IOException {
        if (inputFile != null && !inputFile.isBlank()) {
            // Restarts and direct JobOperator calls bypass BatchService's validation, so check here as well.
            return Files.newBufferedReader(importFileResolver.resolve(source), StandardCharsets.UTF_8);
        }
        InputStream stream = getClass().getResourceAsStream(CLASSPATH_CSV);
        if (stream == null) {
            throw new IllegalStateException("CSV not found: " + CLASSPATH_CSV);
        }
        return new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
    }
}
