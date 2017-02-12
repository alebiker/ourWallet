package it.abapp.mobile.shoppingtogether.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Created by Alessandro on 01/04/2015.
 */
public class ShopList implements Serializable{
    public long id;
    public Date date;
    public float amount_total;
    public String owner;
    public float amount_common;        // to divide by all users
    public boolean paid;
    public ArrayList<ShopListEntry> items;
    public ArrayList<User> users;
    public User common;
    public HashMap<String,User> m_users;
    public HashMap<String,ShopListEntry> m_items;
    public HashMap<User,HashMap<ShopListEntry,Integer>> m_user_item_list;

    //TODO matrix or hash map that map 2 input(int,int) to int value

    public ShopList() {
        id = Calendar.getInstance().getTimeInMillis();
        this.amount_total = 0;
        this.items = new ArrayList<ShopListEntry>();
        this.users = new ArrayList<User>();
        m_users = new HashMap<String,User>();
        m_items = new HashMap<String,ShopListEntry>();
        m_user_item_list = new HashMap<User,HashMap<ShopListEntry,Integer>>();

        common = new User("common");
        common.amount = this.amount_total;
        HashMap<ShopListEntry,Integer> m_user_item = new HashMap<ShopListEntry,Integer>();
        m_user_item_list.put(common, m_user_item);
        // fill the shop list as one big item
        ShopListEntry item = new ShopListEntry("other",this.amount_total,1);

        items.add(item);
        m_items.put(item.name,item);
        m_user_item_list.get(common).put(item, item.qty);
        item.shopList = this;
    }

    public void setTotal(float amount){
        int amount_users = 0;
        for(User u : users)
            amount_users += u.amount;

        if(amount >= amount_users + common.amount - m_items.get("other").price) {
            this.amount_total = amount;
            m_items.get("other").price = amount - (amount_users + common.amount - m_items.get("other").price);

            for (User u : users) {
                updateUserAmount(u);
            }
            updateCommon();
        }
    }

    public void setUsers(int nUsers) {
        if(this.users.size() < nUsers) {
            int toAdd = nUsers - this.users.size();
            for (int c = 0; c < toAdd; c++) {
                User u = new User("u" + (users.size() + 1));
                addUser(u);
            }
        }
    }

    public void initUsers(int n_users){
        int c;
        for(c = 0; c < n_users; c++){
            User u = new User("u"+c);
            addUser(u);
        }
    }

    public void updateUserAmount(User user){
        float user_amount = 0;
        for(Map.Entry<ShopListEntry,Integer> entry : m_user_item_list.get(user).entrySet()){
            user_amount += entry.getKey().price*entry.getValue();
        }
        user.amount = user_amount;
    }

    public void addUser(User user){
        users.add(user);
        m_users.put(user.name,user);
        HashMap<ShopListEntry,Integer> m_user_item = new HashMap<ShopListEntry,Integer>();
        m_user_item_list.put(user, m_user_item);
    }

    public void delUser(String name){
        //TODO check implementation
        User user = m_users.get(name);
        users.remove(user);
        m_users.remove(user.name);
        HashMap<ShopListEntry,Integer> m_itemUser = m_user_item_list.get(user);
        // retrive all item and remove all one by one
        Set<ShopListEntry> items = new HashSet<>(m_itemUser.keySet());
        Iterator<ShopListEntry> ite = items.iterator();
        while(ite.hasNext()){
            ShopListEntry item = ite.next();
            if (m_itemUser.get(item) > 0)
                remUserItem(user,item);
        }
    }

    private void remUserItem(User user, ShopListEntry item) {
        int qty = m_user_item_list.get(user).get(item);
        int comm_qty = m_user_item_list.get(common).get(item);
        m_user_item_list.get(common).put(item,comm_qty+qty);
        m_user_item_list.get(user).remove(item);
    }

    // addItem to the shop list, default in the common list
    public boolean addItem(ShopListEntry sle){
        items.add(sle);
        m_items.put(sle.name,sle);
        m_user_item_list.get(common).put(sle, sle.qty);
        m_items.get("other").price -= sle.price*sle.qty;
        if(m_items.get("other").price <= 0) {
            amount_total += (-m_items.get("other").price);
            m_items.get("other").price = 0;
            //items.remove(m_items.get("other"));
        }
        sle.shopList = this;
        return true;
    }

    public void delItem(ShopListEntry sle){
        for (User u : users){
            if(m_user_item_list.get(u).containsKey(sle)) {
                u.amount -= m_user_item_list.get(u).get(sle) * sle.price;
                m_user_item_list.get(u).remove(sle);
            }
        }
        if(m_user_item_list.get(common).containsKey(sle)){
            m_user_item_list.get(common).remove(sle);
        }

        ShopListEntry other = m_items.get("other");
        other.price += sle.price*sle.qty;
        m_items.remove(sle.name);
        items.remove(sle);
        updateCommon();
    }

    public void incUserItem(User user, ShopListEntry item){
        int common_qty = m_user_item_list.get(common).get(item);
        if(common_qty > 0) {
            int qty = 0;
            if(m_user_item_list.get(user).containsKey(item))
                qty = m_user_item_list.get(user).get(item);
            m_user_item_list.get(user).put(item, qty + 1);
            user.amount += item.price;
            m_user_item_list.get(common).put(item, common_qty - 1);
            updateCommon();
        }
    }

    public void decUserItem(User user, ShopListEntry item){
        int common_qty = m_user_item_list.get(common).get(item);
        int qty = 0;
        if(m_user_item_list.get(user).containsKey(item))
            qty = m_user_item_list.get(user).get(item);
        if(qty > 0) {
            m_user_item_list.get(user).put(item, qty - 1);
            user.amount -= item.price;
            m_user_item_list.get(common).put(item,common_qty + 1);
            updateCommon();
        }else
            ;

        // cannot decrement
    }

    public void addUserItem(User user, ShopListEntry item, int qty){
        //TODO implement add specific qty of item to user

        int common_qty = m_user_item_list.get(common).get(item);
        if(qty > common_qty)
            return;

        m_user_item_list.get(user).put(item,qty);
        user.amount += item.price * qty;
        m_user_item_list.get(common).put(item,common_qty - qty);
        common.amount -= item.price * qty;
    }

    public void updateCommon(){
        float common_amount = 0;
        for(Map.Entry<ShopListEntry,Integer> entry : m_user_item_list.get(common).entrySet()){
            common_amount += entry.getKey().price*entry.getValue();
        }
        common.amount = common_amount;
    }

    // autoSet the common qty of specific item based on others users qty
    public void updateCommonItemQty(ShopListEntry item){
        int common_qty = item.qty;
        for(User u : users){
            common_qty -= m_user_item_list.get(u).get(item);
        }
        if(common_qty >= 0)
            // OK
            m_user_item_list.get(users).put(item,common_qty);
        else
            ;
            // inconsistency error
    }

    public boolean editUserName(User user, String name) {
        if (!m_users.containsKey(name)){
                m_users.put(name,m_users.remove(user.name));
                user.name = name;
                return true;
            }
        else
        // name already used
            return false;
    }

    public boolean editItem(ShopListEntry item, ShopListEntry new_item) {
        if (m_items.containsKey(item.name)){

            if(m_items.get("other").price +(item.price*item.qty) < new_item.price*new_item.qty)
                return false;
            //TODO check bugs
            // update other item, and common part
            m_items.get("other").price -= new_item.price*new_item.qty - item.price*item.qty;
            for(User u : users){
                HashMap<ShopListEntry,Integer> hm = m_user_item_list.get(u);
                if(hm.containsKey(m_items.get("other")))
                    updateUserAmount(u);
            }
            //reset the common items
            int com_new_qty = 0;
            int com_qty = m_user_item_list.get(common).get(item);

            int qty_set = 0;
            for(User u : users){
                HashMap<ShopListEntry,Integer> hm = m_user_item_list.get(u);
                if(hm.containsKey(item))
                    qty_set += hm.get(item);
            }
            com_new_qty = new_item.qty;
            if(qty_set > new_item.qty){
                //TOCO reset all item and amounts
                int qty = 0;
                for(User u : users){
                    HashMap<ShopListEntry,Integer> hm = m_user_item_list.get(u);
                    hm.put(item,0);
                    updateUserAmount(u);
                }
                com_new_qty = qty_set;
            }else{
                com_new_qty -= qty_set;
            }

            // add the remaining qty to common user and add the amount
            m_user_item_list.get(common).put(item, com_new_qty);

            // update the old key with the new
            m_items.put(new_item.name,m_items.remove(item.name));
            item.name = new_item.name;
            item.price = new_item.price;
            item.qty = new_item.qty;

            // update all the amount of the user that have the item
            for(User u : users) {
                if (m_user_item_list.get(u).containsKey(item))
                    if (m_user_item_list.get(u).get(item) > 0)
                        updateUserAmount(u);
            }
            updateCommon();
            return true;
        }else
            //error, old name non present
        return false;


    }

}
