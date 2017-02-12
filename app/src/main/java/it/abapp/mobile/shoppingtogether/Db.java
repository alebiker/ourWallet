package it.abapp.mobile.shoppingtogether;

import java.util.ArrayList;

import it.abapp.mobile.shoppingtogether.model.ShopList;

/**
 * Created by Alessandro on 11/04/2015.
 */
public interface
        Db {

    public boolean initialize();

    public ArrayList<ShopList> getSummaryShoppingLists();

    public ShopList getShoppingList(long id);

    public boolean setShoppingList(ShopList shopList);
}
