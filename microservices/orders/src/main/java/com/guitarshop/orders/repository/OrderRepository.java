package com.guitarshop.orders.repository;

import com.guitarshop.orders.model.Order;
import com.guitarshop.orders.model.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {
    List<Order> findByCustomerIdOrderByCreatedAtDesc(String customerId);
    List<Order> findByStatus(OrderStatus status);
}
