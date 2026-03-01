package com.guitarshop.orders.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guitarshop.orders.model.Order;
import com.guitarshop.orders.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventListener {

    private final OrderService orderService;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = "${guitarshop.rabbitmq.queue:checkout.events}")
    public void handleCheckoutEvent(String message) {
        try {
            log.info("📨 Received checkout event: {}", message);

            @SuppressWarnings("unchecked")
            Map<String, Object> event = objectMapper.readValue(message, Map.class);

            String eventType = (String) event.get("event");
            if (!"ORDER_CREATED".equals(eventType)) {
                log.warn("Unknown event type: {}", eventType);
                return;
            }

            // Build order from the event payload
            Order order = new Order();
            order.setCustomerId((String) event.get("customerId"));
            order.setEmail((String) event.getOrDefault("email", "unknown@guitarshop.com"));

            Object total = event.get("total");
            if (total != null) {
                order.setTotal(new java.math.BigDecimal(total.toString()));
            }

            orderService.processCheckoutEvent(order);
        } catch (Exception e) {
            log.error("❌ Failed to process checkout event: {}", e.getMessage(), e);
        }
    }
}
