package me.vshop;

import java.util.List;

public record BookCategory(String id, String name, List<Node> nodes) {
    /** group=true: opens a sub dialog listing every level. group=false: variants has exactly one book. */
    public record Node(String id, String name, boolean group, List<ShopEntry> variants) {}
}
