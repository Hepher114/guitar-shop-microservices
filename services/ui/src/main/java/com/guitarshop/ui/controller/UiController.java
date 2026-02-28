package com.guitarshop.ui.controller;

import com.guitarshop.ui.client.ServiceClients;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
@Slf4j
public class UiController {

    private final ServiceClients clients;

    // ── Session helper ────────────────────────────────────────────────────────
    private String getCustomerId(HttpSession session) {
        String id = (String) session.getAttribute("customerId");
        if (id == null) {
            id = "guest-" + UUID.randomUUID().toString().substring(0, 8);
            session.setAttribute("customerId", id);
        }
        return id;
    }

    // ── Home / Catalog ─────────────────────────────────────────────────────────
    @GetMapping("/")
    public String home(@RequestParam(required = false) String category,
                       @RequestParam(required = false) String search,
                       HttpSession session, Model model) {
        model.addAttribute("products",   clients.getProducts(category, search));
        model.addAttribute("categories", clients.getCategories());
        model.addAttribute("cart",       clients.getCart(getCustomerId(session)));
        model.addAttribute("category",   category);
        model.addAttribute("search",     search);
        return "pages/home";
    }

    // ── Product Detail ─────────────────────────────────────────────────────────
    @GetMapping("/products/{id}")
    public String productDetail(@PathVariable String id, HttpSession session, Model model) {
        model.addAttribute("product",    clients.getProduct(id));
        model.addAttribute("categories", clients.getCategories());
        model.addAttribute("cart",       clients.getCart(getCustomerId(session)));
        return "pages/product";
    }

    // ── Cart ───────────────────────────────────────────────────────────────────
    @GetMapping("/cart")
    public String viewCart(HttpSession session, Model model) {
        model.addAttribute("cart",       clients.getCart(getCustomerId(session)));
        model.addAttribute("categories", clients.getCategories());
        return "pages/cart";
    }

    @PostMapping("/cart/add")
    public String addToCart(@RequestParam String productId,
                             @RequestParam String name,
                             @RequestParam String brand,
                             @RequestParam double price,
                             @RequestParam(defaultValue = "1") int quantity,
                             @RequestParam(required = false) String imageUrl,
                             HttpSession session) {
        Map<String, Object> item = new HashMap<>();
        item.put("productId", productId);
        item.put("name", name);
        item.put("brand", brand);
        item.put("price", price);
        item.put("quantity", quantity);
        item.put("imageUrl", imageUrl != null ? imageUrl : "/images/placeholder.svg");
        clients.addToCart(getCustomerId(session), item);
        return "redirect:/cart";
    }

    // ── Checkout ───────────────────────────────────────────────────────────────
    @GetMapping("/checkout")
    public String checkoutPage(HttpSession session, Model model) {
        model.addAttribute("cart",       clients.getCart(getCustomerId(session)));
        model.addAttribute("categories", clients.getCategories());
        return "pages/checkout";
    }

    @PostMapping("/checkout")
    public String submitCheckout(@RequestParam String email,
                                  @RequestParam String firstName,
                                  @RequestParam String lastName,
                                  @RequestParam String address,
                                  @RequestParam String city,
                                  @RequestParam String country,
                                  @RequestParam String postalCode,
                                  HttpSession session, Model model) {
        var cart = clients.getCart(getCustomerId(session));

        Map<String, Object> checkoutData = new HashMap<>();
        checkoutData.put("customerId", getCustomerId(session));
        checkoutData.put("email", email);
        checkoutData.put("firstName", firstName);
        checkoutData.put("lastName", lastName);
        checkoutData.put("address", address);
        checkoutData.put("city", city);
        checkoutData.put("country", country);
        checkoutData.put("postalCode", postalCode);
        checkoutData.put("items", cart.get("items"));

        var result = clients.createCheckout(checkoutData);
        model.addAttribute("order", result);
        model.addAttribute("categories", clients.getCategories());
        return "pages/confirmation";
    }

    // ── Orders ─────────────────────────────────────────────────────────────────
    @GetMapping("/orders")
    public String orders(HttpSession session, Model model) {
        model.addAttribute("orders",     clients.getOrdersByCustomer(getCustomerId(session)));
        model.addAttribute("categories", clients.getCategories());
        return "pages/orders";
    }

    // ── Health ─────────────────────────────────────────────────────────────────
    @GetMapping("/health")
    @ResponseBody
    public Map<String, String> health() {
        return Map.of("status", "UP", "service", "guitarshop-ui");
    }
}
