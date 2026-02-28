package com.guitarshop.cart.controller;

import com.guitarshop.cart.model.Cart;
import com.guitarshop.cart.model.CartItem;
import com.guitarshop.cart.service.CartService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/cart")
@CrossOrigin(origins = "*")
public class CartController {

    @Autowired
    private CartService cartService;

    @GetMapping("/{customerId}")
    public ResponseEntity<Cart> getCart(@PathVariable String customerId) {
        return ResponseEntity.ok(cartService.getCart(customerId));
    }

    @PostMapping("/{customerId}/items")
    public ResponseEntity<Cart> addItem(@PathVariable String customerId,
                                         @RequestBody CartItem item) {
        return ResponseEntity.ok(cartService.addItem(customerId, item));
    }

    @PutMapping("/{customerId}/items/{productId}")
    public ResponseEntity<Cart> updateItem(@PathVariable String customerId,
                                            @PathVariable String productId,
                                            @RequestBody Map<String, Integer> body) {
        int qty = body.getOrDefault("quantity", 0);
        return ResponseEntity.ok(cartService.updateItem(customerId, productId, qty));
    }

    @DeleteMapping("/{customerId}/items/{productId}")
    public ResponseEntity<Cart> removeItem(@PathVariable String customerId,
                                            @PathVariable String productId) {
        return ResponseEntity.ok(cartService.removeItem(customerId, productId));
    }

    @DeleteMapping("/{customerId}")
    public ResponseEntity<Void> clearCart(@PathVariable String customerId) {
        cartService.clearCart(customerId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "guitarshop-cart"
        ));
    }
}
