package com.guitarshop.cart.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Cart implements Serializable {
    private String customerId;
    private List<CartItem> items = new ArrayList<>();

    public double getTotal() {
        return items.stream()
                .mapToDouble(i -> i.getPrice() * i.getQuantity())
                .sum();
    }

    public int getItemCount() {
        return items.stream().mapToInt(CartItem::getQuantity).sum();
    }
}
