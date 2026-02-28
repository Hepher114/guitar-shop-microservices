package com.guitarshop.cart.service;

import com.guitarshop.cart.model.Cart;
import com.guitarshop.cart.model.CartItem;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@Service
public class CartService {

    private static final String CART_KEY_PREFIX = "guitarshop:cart:";
    private static final Duration CART_TTL = Duration.ofDays(7);

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private String cartKey(String customerId) {
        return CART_KEY_PREFIX + customerId;
    }

    public Cart getCart(String customerId) {
        Object cached = redisTemplate.opsForValue().get(cartKey(customerId));
        if (cached instanceof Cart cart) {
            return cart;
        }
        return new Cart(customerId, new java.util.ArrayList<>());
    }

    public Cart addItem(String customerId, CartItem newItem) {
        Cart cart = getCart(customerId);

        Optional<CartItem> existing = cart.getItems().stream()
                .filter(i -> i.getProductId().equals(newItem.getProductId()))
                .findFirst();

        if (existing.isPresent()) {
            existing.get().setQuantity(existing.get().getQuantity() + newItem.getQuantity());
        } else {
            cart.getItems().add(newItem);
        }

        saveCart(cart);
        return cart;
    }

    public Cart updateItem(String customerId, String productId, int quantity) {
        Cart cart = getCart(customerId);

        if (quantity <= 0) {
            cart.getItems().removeIf(i -> i.getProductId().equals(productId));
        } else {
            cart.getItems().stream()
                    .filter(i -> i.getProductId().equals(productId))
                    .findFirst()
                    .ifPresent(i -> i.setQuantity(quantity));
        }

        saveCart(cart);
        return cart;
    }

    public Cart removeItem(String customerId, String productId) {
        Cart cart = getCart(customerId);
        cart.getItems().removeIf(i -> i.getProductId().equals(productId));
        saveCart(cart);
        return cart;
    }

    public void clearCart(String customerId) {
        redisTemplate.delete(cartKey(customerId));
    }

    private void saveCart(Cart cart) {
        redisTemplate.opsForValue().set(cartKey(cart.getCustomerId()), cart, CART_TTL);
    }
}
