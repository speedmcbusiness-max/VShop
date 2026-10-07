package me.vshop;

import java.util.List;

public record Category(String id, String title, int columns, int buttonWidth, boolean search,
                       List<String> aliases, List<ShopEntry> entries) {}
