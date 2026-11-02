package com.example.app.core.service;

import com.example.app.api.model.CreateOrderRequest;
import com.example.app.api.model.OrderResponse;
import com.example.app.core.repository.OrderImportLogRepository;
import com.example.app.core.web.error.BusinessException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.Optional;

/** Imports one line of the order CSV. */
@ApplicationScoped
public class OrderImportService {

    @Inject
    private OrderService orderService;

    @Inject
    private OrderImportLogRepository importLog;

    /** Creates the service. CDI creates the single instance (it is application-scoped) and injects its fields. */
    public OrderImportService() {}

    /**
     * Imports one line in its own transaction. A bad line does not roll back the whole batch, and the import record is
     * committed together with the order (if the order fails, so does the record, and a rerun imports it again).
     *
     * @param importKey the line's key ({@code ImportCsvLine.importKey})
     * @param request the order the line describes
     * @return the created order, or empty if this line was already imported
     * @throws BusinessException if the customer or a product does not exist (404), or the quantity is below 1 (400)
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public Optional<OrderResponse> importOrder(String importKey, CreateOrderRequest request) {
        if (importLog.isImported(importKey)) {
            return Optional.empty();
        }
        importLog.markImported(importKey);
        return Optional.of(orderService.createOrder(request));
    }
}
