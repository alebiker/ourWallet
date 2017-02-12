package it.abapp.mobile.shoppingtogether.model;

import java.io.Serializable;

/**
 * Created by Alessandro on 01/04/2015.
 */
public class ShopListEntry implements Serializable{

    String name;
    float price;
    int qty;
    ShopList shopList;      //reference to parent

    public ShopListEntry() {
        this("Item", 0, 1);
    }

    public ShopListEntry(String name, float price, int qty) {
        this.name = name;
        this.price = price;
        this.qty = qty;
    }

    public ShopList getShopList(){
        return shopList;
    }

    public void setShopList(ShopList shopList){
        this.shopList = shopList;
    }

    public String getName() {
        return name;
    }

    public float getPrice() {
        return price;
    }

    public int getQty() {
        return qty;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setQty(int qty) {
        this.qty = qty;
    }

    public void setPrice(float price) {
        this.price = price;
    }
}
