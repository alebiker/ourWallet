package it.abapp.mobile.shoppingtogether.model;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * To work on unit tests, switch the Test Artifact in the Build Variants view.
 */
public class ShopListEntryTest {

    @Test
    public void ShopListEntryFullSetupTest() {
        String name = "item";
        float price = 3.56f;
        int qty = 3;
        ShopListEntry shopListEntry = new ShopListEntry(name, price, qty);
        assertEquals("Setting name to item failed", name,
                shopListEntry.getName());
        assertEquals("Setting price to item failed", price,
                shopListEntry.getPrice(), 0.0);
        assertEquals("Setting quantity to item failed", qty,
                shopListEntry.getQty());
    }

    @Test
    public void ShopListEntryEmptySetupTest() {
        ShopListEntry shopListEntry = new ShopListEntry();
        assertEquals("Setting name to item failed", null,
                shopListEntry.getName());
        assertEquals("Setting price to item failed", 0,
                shopListEntry.getPrice(), 0);
        assertEquals("Setting quantity to item failed", 0,
                shopListEntry.getQty());
    }

}