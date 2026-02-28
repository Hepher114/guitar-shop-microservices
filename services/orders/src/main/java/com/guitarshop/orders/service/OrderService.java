package com.guitarshop.orders.service;

import com.guitarshop.orders.model.Order;
import com.guitarshop.orders.model.OrderStatus;
import com.guitarshop.orders.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;

    public List<Order> getAllOrders() {
        return orderRepository.findAll();
    }

    public Optional<Order> getOrderById(UUID id) {
        return orderRepository.findById(id);
    }

    public List<Order> getOrdersByCustomer(String customerId) {
        return orderRepository.findByCustomerIdOrderByCreatedAtDesc(customerId);
    }

    @Transactional
    public Order createOrder(Order order) {
        order.setStatus(OrderStatus.PENDING);
        Order saved = orderRepository.save(order);
        log.info("✅ Created order {} for customer {}", saved.getId(), saved.getCustomerId());
        return saved;
    }

    @Transactional
    public Order updateOrderStatus(UUID id, OrderStatus status) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Order not found: " + id));
        order.setStatus(status);
        Order updated = orderRepository.save(order);
        log.info("📦 Order {} status updated to {}", id, status);
        return updated;
    }

    @Transactional
    public Order processCheckoutEvent(Order incomingOrder) {
        incomingOrder.setStatus(OrderStatus.CONFIRMED);
        Order saved = orderRepository.save(incomingOrder);
        log.info("📨 Processed checkout event → Order {} CONFIRMED", saved.getId());
        return saved;
    }
}
