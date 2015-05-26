package it.abapp.mobile.shoppingtogether;

import java.io.Serializable;
import java.util.HashMap;

/**
 * Created by Alessandro on 01/04/2015.
 */
public class ShopListEntry implements Serializable{

    String name;
    float price;
    int qty;
    ShopList shopList;      //reference to parent

    public ShopListEntry(String name, float price, int qty) {
        this.name = name;
        this.price = price;
        this.qty = qty;
    }

}
