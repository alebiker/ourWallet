package it.abapp.mobile.shoppingtogether;

import android.content.Context;
import android.os.Environment;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Created by Alessandro on 16/03/2015.
 */

public class Utils {

    private static Context context = null;
    private static HashMap<ShopList,String> m_shoplistFilename;

    public static void setContext (Context cntx) {
        context = cntx;
    }

    public static Context getContext () {
        return context;
    }

    public static JSONObject loadJSONFromAsset(String filename) {
        String json = null;
        try {

            InputStream is = context.getAssets().open(filename);

            int size = is.available();

            byte[] buffer = new byte[size];

            is.read(buffer);

            is.close();

            json = new String(buffer, "UTF-8");

            return new JSONObject(json);
        } catch (IOException ex) {
            ex.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }
/*
    public static ArrayList<ShopList> getShopListHeaders(JSONObject jObjProf , ArrayList<String> fileNames) throws JSONException, ParseException {
    //TODO check fetching of the shop list headers
        JSONArray m_jArry = jObjProf.getJSONArray("shopListsHeader");
        ArrayList<ShopList> shopListsHeader = new ArrayList<ShopList>();
        m_shoplistFilename = new HashMap<ShopList,String>();

        for (int i = 0; i < m_jArry.length(); i++) {
            JSONObject jo_inside = m_jArry.getJSONObject(i);
            //TODO verify bug correction
            ShopList sl = new ShopList(jo_inside.getJSONArray("users").length(),Float.parseFloat(jo_inside.getString("amount")));

            sl.id = jo_inside.getInt("id");

            fileNames.add(jo_inside.getString("file"));

            JSONArray users_jArry = jo_inside.getJSONArray("users");
            ArrayList<User> users = new ArrayList<User>();

            // add all the users that have part in the shopping without specify the bought items
            for(int c = 0; c < users_jArry.length(); c++){
                //HashMap<ShopListEntry,Integer> user_sl = new HashMap<ShopListEntry,Integer>();
                User u = new User(users_jArry.getString(c));
                users.add(u);
            }
            sl.users = users;

            Calendar c = Calendar.getInstance();
            c.setTime(new SimpleDateFormat("yyyy/MM/dd HH:mm").parse(jo_inside.getString("date")));
            sl.date = c.getTime();

            sl.owner = jo_inside.getString("owner");
            sl.paid = jo_inside.getBoolean("isPaid");
            //Log.d("Details-->", jo_inside.getString("name"));

            shopListsHeader.add(sl);
            //Add your values in your `ArrayList` as below:

            //Same way for other value...
        }
        return shopListsHeader;
    }
*/
    public static JSONObject loadJSONFromInternal(String filename ) throws JSONException {
        //TODO check

        FileInputStream iS;
        String json;
        try {
            iS = context.openFileInput(filename);

            int size = iS.available();

            byte[] buffer = new byte[size];

            iS.read(buffer);

            iS.close();

            json = new String(buffer, "UTF-8");

            return new JSONObject(json);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static JSONObject loadJSONFromFile(File file ) throws JSONException {

        FileInputStream iS;
        String json;
        try {
            iS = new FileInputStream(file);

            int size = iS.available();

            byte[] buffer = new byte[size];

            iS.read(buffer);

            iS.close();

            json = new String(buffer, "UTF-8");

            return new JSONObject(json);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }


    public static ShopList getShopList(JSONObject jsonObject) throws JSONException, ParseException {
        //TODO implementing json shop list details fetching

        JSONObject jo_inside = jsonObject.getJSONObject("shopList");
        ShopList sl = new ShopList();
        sl.setTotal(new Float(jo_inside.getDouble("amount")));
        sl.id = jo_inside.getLong("id");
        sl.date = new SimpleDateFormat(context.getString(R.string.date_pattern)).parse(jo_inside.getString("date"));
        sl.owner = jo_inside.getString("owner");
        // map ID to ShopListentry
        JSONArray items_jArry = jo_inside.getJSONArray("items");
        for(int c = 0; c < items_jArry.length(); c++){
            JSONObject jo_item = items_jArry.getJSONObject(c);
            String id = jo_item.getString("id");
            Float price = new Float(jo_item.getDouble("price"));
            int qty = jo_item.getInt("qty");

            // for not add another item other in the list
            if (!id.contains("other"))
                sl.addItem(new ShopListEntry(id, price, qty));
        }

        JSONArray users_jArry = jo_inside.getJSONArray("users");

        // add all the users that have part in the shopping without specify the bought items
        for(int c = 0; c < users_jArry.length(); c++){
            JSONObject jo_user = users_jArry.getJSONObject(c);

            User user = new User(jo_user.getString("name"));
            sl.addUser(user);

            JSONArray user_items_jArry = jo_user.getJSONArray("items");
            for(int b = 0; b < user_items_jArry.length(); b++){
                JSONObject jo_item = user_items_jArry.getJSONObject(b);
                String id = jo_item.getString("id");
                int qty = jo_item.getInt("qty");

                sl.addUserItem(user, sl.m_items.get(id), qty);
            }
        }
        sl.paid = jo_inside.getBoolean("isPaid");
        //Log.d("Details-->", jo_inside.getString("name"));

        //Add your values in your `ArrayList` as below:

        //Same way for other value...
        return sl;
    }

    public static ShopList getSLHeader(JSONObject jsonObject, StringBuilder filename) throws JSONException, ParseException {
        //TODO implementing json shop list header fetching

        ShopList shopListsHeader = new ShopList();
        shopListsHeader.setTotal((float)jsonObject.getDouble("amount"));
        shopListsHeader.id = jsonObject.getLong("id");
        shopListsHeader.owner = jsonObject.getString("owner");
        filename.append(jsonObject.getString("filename"));

        Calendar c = Calendar.getInstance();
        c.setTime(new SimpleDateFormat(context.getString(R.string.date_pattern)).parse(jsonObject.getString("date")));
        shopListsHeader.date = c.getTime();

        shopListsHeader.paid = jsonObject.getBoolean("isPaid");
        JSONArray jUsers = jsonObject.getJSONArray("users");
        int i = 0;
        for (i = 0 ; i < jUsers.length(); i++) {
            String user = jUsers.getString(i);
            User u = new User(user);
            shopListsHeader.addUser(u);
        }
        return shopListsHeader;
    }

    public static boolean writeJSONShopListToFile(String slFilename, JSONObject jsonObject) throws ParseException {
        FileOutputStream outputStream;
        try {
            outputStream = new FileOutputStream(new File(getShopListStorageDir(),slFilename));
            //outputStream = context.getfopenFileOutput(slFilename, Context.MODE_PRIVATE);
            outputStream.write(jsonObject.toString().getBytes());
            outputStream.close();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean writeJSONSLHeaderToFile(String slhFilename, JSONObject jsonObject) throws ParseException {
        FileOutputStream outputStream;
        try {

            File f_header =  new File(getShopListHeaderStorageDir(),  slhFilename);
            outputStream = new FileOutputStream(f_header);
            //outputStream = context.getfopenFileOutput( slhFilename, Context.MODE_PRIVATE);
            outputStream.write(jsonObject.toString().getBytes());
            outputStream.close();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static JSONObject getJSONObjFromShopList(ShopList shopList) throws JSONException {
        JSONObject jsonObject = new JSONObject();
        JSONObject jsonShopList = new JSONObject();
        jsonShopList.put("id",shopList.id);
        jsonShopList.put("date", new SimpleDateFormat(context.getString(R.string.date_pattern)).format(shopList.date));
        jsonShopList.put("amount", shopList.amount_total);
        jsonShopList.put("owner", shopList.owner);
        JSONArray jUsers = new JSONArray();
        for (User u : shopList.users){
            JSONObject jUser = new JSONObject();
            jUser.put("name", u.name);
            JSONArray jUserItems = new JSONArray();
            for(Map.Entry<ShopListEntry,Integer> m_item : shopList.m_user_item_list.get(u).entrySet()){
                if(m_item.getValue() > 0) {
                    JSONObject jItem = new JSONObject();
                    jItem.put("id", m_item.getKey().name);
                    jItem.put("qty", m_item.getValue());
                    jUserItems.put(jItem);
                }
            }
            jUser.put("items",jUserItems);
            jUsers.put(jUser);
        }
        jsonShopList.put("users", jUsers);
        JSONArray jItems = new JSONArray();
        for(ShopListEntry item : shopList.items){
            JSONObject jItem = new JSONObject();
            jItem.put("id",item.name);
            jItem.put("qty",item.qty);
            jItem.put("price",item.price);
            jItems.put(jItem);
        }

        jsonShopList.put("items", jItems);
        jsonShopList.put("isPaid", shopList.paid);

        jsonObject.put("shopList",jsonShopList);
        return jsonObject;
    }

    public static JSONObject getJSONObjFromShopListHeader(ShopList shopList, String filename) throws JSONException {
        //TODO check
        JSONObject jsonSLHeader = new JSONObject();
        jsonSLHeader.put("id", shopList.id);
        jsonSLHeader.put("date", new SimpleDateFormat(context.getString(R.string.date_pattern)).format(shopList.date));
        jsonSLHeader.put("filename", filename);
        jsonSLHeader.put("amount", shopList.amount_total);
        jsonSLHeader.put("owner", shopList.owner);
        JSONArray jUsers = new JSONArray();
        for (User u : shopList.users)
            jUsers.put(u.name);
        jsonSLHeader.put("users", jUsers);

        jsonSLHeader.put("isPaid", shopList.paid);

        return jsonSLHeader;
    }

    public static ShopList getShopListHeader(ShopList shopList) throws JSONException {
        //TODO check
        ShopList header = new ShopList();
        header.setTotal(shopList.amount_total);
        header.id = shopList.id;
        header.users.addAll(shopList.users);
        header.date = shopList.date;
        header.owner = shopList.owner;
        header.paid = shopList.paid;
        return  header;
    }

    public static String getShopListPath(String filname){
        return getShopListStorageDir().getPath()+"/"+filname;
    }

    public static File getShopListStorageDir() {
        // Get the directory for the app's private pictures directory.
        File file = context.getDir("shopList", Context.MODE_PRIVATE);
        if (!file.mkdirs()) {
            Log.e("ERR", "Directory not created");
        }
        return file;
    }
    public static File getShopListHeaderStorageDir() {
        // Get the directory for the app's private pictures directory.
        File file = context.getDir("headers", Context.MODE_PRIVATE);
        if (!file.mkdirs()) {
            Log.e("ERR", "Directory not created");
        }
        return file;
    }
    public static File getShopListExtStorageDir() {
        // Get the directory for the app's private pictures directory.
        File file = new File(context.getExternalFilesDir(
                Environment.DIRECTORY_DOCUMENTS), "shopList");
        if (!file.mkdirs()) {
            Log.e("ERR", "Directory not created");
        }
        return file;
    }
    public static File getShopListHeaderExtStorageDir() {
        // Get the directory for the app's private pictures directory.
        File file = new File(context.getExternalFilesDir(
                Environment.DIRECTORY_DOCUMENTS), "headers");
        if (!file.mkdirs()) {
            Log.e("ERR", "Directory not created");
        }
        return file;
    }

    public static String formatToCash(Float amount){
        String out = context.getString(R.string.euro);
        out += " "+String.format("%.2f", amount);
        return out;

    }

    public static int dpToPx(int dp) {
        DisplayMetrics displayMetrics = context.getResources().getDisplayMetrics();
        int px = Math.round(dp * (displayMetrics.xdpi / DisplayMetrics.DENSITY_DEFAULT));
        return px;
    }

    public static boolean deleteShopListFile (String filename){
        File file = new File(getShopListStorageDir(), filename);
        return file.delete();
    }

    public static boolean deleteShopListHeaderFile (String filename){
        File file = new File(getShopListHeaderStorageDir(), filename);
        return file.delete();
    }

    /*
    public static boolean deleteFileFromInternal(String filename){
        File file = new File(getShopListStorageDir(), filename);
        return file.delete();
    }
    */

    public static File createImageFile() throws IOException {
        // Create an image file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = context.getExternalFilesDir(
                Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );

        // Save a file: path for use with ACTION_VIEW intents
        //mCurrentPhotoPath = "file:" + image.getAbsolutePath();
        return image;
    }

}