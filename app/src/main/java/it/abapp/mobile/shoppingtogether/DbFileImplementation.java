package it.abapp.mobile.shoppingtogether;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Created by Alessandro on 08/04/2015.
 */
public class DbFileImplementation implements Db {

    private static DbFileImplementation instance;
    private ArrayList<ShopList> m_shopList;
    private HashMap<ShopList,String> m_shopListFilename;
    public DbFileImplementation() {
    }

    public boolean initialize() {
        try {
            m_shopListFilename = new HashMap<ShopList,String>();
            m_shopList = new ArrayList<>();
            //get the directory of headers
            File[] headerFiles = getAllSLHeaders();
            StringBuilder filename;
            for(File f_Header : headerFiles){
                filename = new StringBuilder();
                ShopList slHeader = getShopListHeader(f_Header, filename);
                m_shopList.add(slHeader);
                m_shopListFilename.put(slHeader, filename.toString());
            }

            return true;
        } catch (JSONException | ParseException e) {
            e.printStackTrace();
            return false;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    public static synchronized DbFileImplementation getInstance() {
        if (instance == null)
            instance = new DbFileImplementation();

        return instance;
    }

    private File[] getAllSLHeaders(){
        File dir = Utils.getShopListHeaderStorageDir();
        return dir.listFiles();
    }

    private ShopList getShopListHeader(File fileHeader, StringBuilder fileShopList) throws JSONException, ParseException, IOException {
        String fPathHeader;

        JSONObject jsonObject = Utils.loadJSONFromFile(fileHeader);
        ShopList slh = Utils.getSLHeader(jsonObject, fileShopList);
        return slh;
    }

    public ArrayList<ShopList> getSummaryShoppingLists() {
        return m_shopList;
    }

    public ShopList getShoppingList(long id) {
        // search the shopList of passed id and load the file retrived by map

        try {
            String filename = null;
            JSONObject jsonObject = null;
            for(ShopList sl : m_shopList){
                if (sl.id == id) {
                    jsonObject = Utils.loadJSONFromFile(new File(Utils.getShopListPath(m_shopListFilename.get(sl))));
                    ShopList shopList_tmp = Utils.getShopList(jsonObject);
                    sl.m_items = shopList_tmp.m_items;
                    sl.items = shopList_tmp.items;
                    sl.m_user_item_list = shopList_tmp.m_user_item_list;
                    sl.m_users = shopList_tmp.m_users;
                    sl.users = shopList_tmp.users;
                    sl.common = shopList_tmp.common;
                    sl.updateCommon();
                    for(User u : sl.users) {
                        sl.updateUserAmount(u);
                    }
                    return  sl;
                }
            }
            return  null;
        } catch (JSONException e) {
            e.printStackTrace();
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return null;
    }

    public boolean setShoppingList(ShopList shopList){
        String sl_filename, slh_filename, date;
        date = new SimpleDateFormat("yyyy_MM_dd_HH_mm").format(shopList.date);
        sl_filename = "sl_" + date;
        slh_filename = "slh_" + date;
        try{
            JSONObject jShopList = Utils.getJSONObjFromShopList(shopList);
            Utils.writeJSONShopListToFile(sl_filename, jShopList);
            if(!m_shopList.contains(shopList))
                m_shopList.add(shopList);
            m_shopListFilename.put(shopList,sl_filename);
            jShopList = Utils.getJSONObjFromShopListHeader(shopList,sl_filename);
            Utils.writeJSONSLHeaderToFile(slh_filename, jShopList);
            return true;
        } catch (ParseException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return false;
    }

    public void deleteShopList(ShopList shopList) {
        String sl_filename, slh_filename, date;
        date = new SimpleDateFormat("yyyy_MM_dd_HH_mm").format(shopList.date);
        slh_filename = "slh_" + date;

        Utils.deleteShopListFile(m_shopListFilename.get(shopList));
        m_shopListFilename.remove(shopList);
        m_shopList.remove(shopList);
        Utils.deleteShopListHeaderFile(slh_filename);
    }
}