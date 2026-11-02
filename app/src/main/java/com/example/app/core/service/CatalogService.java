package com.example.app.core.service;

import com.example.app.api.model.CustomerResponse;
import com.example.app.api.model.ProductResponse;
import com.example.app.core.repository.CustomerRepository;
import com.example.app.core.repository.ProductRepository;
import com.example.app.core.web.error.BusinessException;
import com.example.app.core.web.paging.PageQuery;
import com.example.app.core.web.paging.PageResponse;
import com.example.app.domain.Customer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/** Product and customer lists. */
@ApplicationScoped
public class CatalogService {

    @Inject
    private ProductRepository productRepository;

    @Inject
    private CustomerRepository customerRepository;

    /** Creates the service. CDI creates the single instance (it is application-scoped) and injects its fields. */
    public CatalogService() {}

    /**
     * Returns one page of products and the total.
     *
     * @param page the page, its size and the sort
     * @return the page
     * @throws BusinessException if a sort field is not allowed (400)
     */
    @Transactional
    public PageResponse<ProductResponse> listProducts(PageQuery page) {
        var products = productRepository.findPage(page.offset(), page.size(), page.sort());
        long total = productRepository.count();
        return PageResponse.of(products.stream().map(this::toProductResponse).toList(), page, total);
    }

    /**
     * Returns one page of customers and the total.
     *
     * @param page the page, its size and the sort
     * @return the page
     * @throws BusinessException if a sort field is not allowed (400)
     */
    @Transactional
    public PageResponse<CustomerResponse> listCustomers(PageQuery page) {
        var customers = customerRepository.findPage(page.offset(), page.size(), page.sort());
        long total = customerRepository.count();
        return PageResponse.of(customers.stream().map(this::toCustomerResponse).toList(), page, total);
    }

    private ProductResponse toProductResponse(ProductRepository.ProductRow product) {
        ProductResponse response = new ProductResponse();
        response.setCode(product.code());
        response.setName(product.name());
        response.setUnitPrice(product.unitPrice());
        response.setStock(product.stock());
        return response;
    }

    private CustomerResponse toCustomerResponse(Customer customer) {
        CustomerResponse response = new CustomerResponse();
        response.setCode(customer.getCode());
        response.setName(customer.getName());
        response.setEmail(customer.getEmail());
        return response;
    }
}
