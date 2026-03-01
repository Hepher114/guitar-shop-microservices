package com.guitarshop.ui.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class ServiceClients {

    private final WebClient catalogClient;
    private final WebClient cartClient;
    private final WebClient checkoutClient;
    private final WebClient ordersClient;

    public ServiceClients(
            @Value("${services.catalog.url:http://catalog:8080}") String catalogUrl,
            @Value("${services.cart.url:http://cart:8080}") String cartUrl,
            @Value("${services.checkout.url:http://checkout:8080}") String checkoutUrl,
            @Value("${services.orders.url:http://orders:8080}") String ordersUrl) {
        this.catalogClient  = WebClient.create(catalogUrl);
        this.cartClient     = WebClient.create(cartUrl);
        this.checkoutClient = WebClient.create(checkoutUrl);
        this.ordersClient   = WebClient.create(ordersUrl);
    }

    // ── Catalog ───────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getProducts(String category, String search) {
        try {
            String uri = "/products?limit=50";
            if (category != null && !category.isEmpty()) uri += "&category=" + category;
            if (search != null && !search.isEmpty()) uri += "&search=" + search;
            return catalogClient.get().uri(uri).retrieve().bodyToMono(List.class).block();
        } catch (Exception e) {
            log.error("Failed to fetch products: {}", e.getMessage());
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getProduct(String id) {
        try {
            return catalogClient.get().uri("/products/" + id).retrieve().bodyToMono(Map.class).block();
        } catch (Exception e) {
            log.error("Failed to fetch product {}: {}", id, e.getMessage());
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getCategories() {
        try {
            return catalogClient.get().uri("/categories").retrieve().bodyToMono(List.class).block();
        } catch (Exception e) {
            log.error("Failed to fetch categories: {}", e.getMessage());
            return List.of();
        }
    }

    // ── Cart ──────────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    public Map<String, Object> getCart(String customerId) {
        try {
            return cartClient.get().uri("/cart/" + customerId).retrieve().bodyToMono(Map.class).block();
        } catch (Exception e) {
            log.error("Failed to fetch cart for {}: {}", customerId, e.getMessage());
            return Map.of("customerId", customerId, "items", List.of(), "itemCount", 0, "total", 0.0);
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> addToCart(String customerId, Map<String, Object> item) {
        try {
            return cartClient.post().uri("/cart/" + customerId + "/items")
                    .bodyValue(item).retrieve().bodyToMono(Map.class).block();
        } catch (Exception e) {
            log.error("Failed to add to cart: {}", e.getMessage());
            return Map.of();
        }
    }

    // ── Checkout ──────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    public Map<String, Object> createCheckout(Map<String, Object> checkoutData) {
        try {
            return checkoutClient.post().uri("/checkout")
                    .bodyValue(checkoutData).retrieve().bodyToMono(Map.class).block();
        } catch (Exception e) {
            log.error("Failed to create checkout: {}", e.getMessage());
            return Map.of("error", e.getMessage());
        }
    }

    // ── Orders ────────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getOrdersByCustomer(String customerId) {
        try {
            return ordersClient.get().uri("/orders/customer/" + customerId)
                    .retrieve().bodyToMono(List.class).block();
        } catch (Exception e) {
            log.error("Failed to fetch orders for {}: {}", customerId, e.getMessage());
            return List.of();
        }
    }
}
