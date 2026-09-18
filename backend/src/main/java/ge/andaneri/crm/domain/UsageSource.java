package ge.andaneri.crm.domain;

/**
 * Where a flavor on the bar comes from. The same taste can reach the glass three ways, and which one it
 * is decides what to pack: a bought syrup can be matched bottle for bottle, a fresh fruit is a season,
 * and one the bar cooks itself is a habit to talk them out of.
 */
public enum UsageSource {

    /** A bought bottle: Monin vanilla, 1883 hazelnut, our own. */
    BRAND,

    /** Fresh produce squeezed or muddled on the spot: lemon, mint, strawberry. */
    FRESH,

    /** The bar makes it in its own kitchen. */
    HOUSE_MADE
}
