package it.abapp.mobile.shoppingtogether;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import it.abapp.mobile.shoppingtogether.model.ShopList;
import it.abapp.mobile.shoppingtogether.model.User;

/**
 * Created by Alessandro on 08/04/2015.
 */
public class ShoppingListAdapter extends ArrayAdapter<ShopList> {
    private Context mContext;
    private HashMap<Integer, Integer> selectionValueMap;

    public ShoppingListAdapter(Context context, int resource, List<ShopList> objects) {
        super(context, resource, objects);
        mContext = context;
        selectionValueMap = new HashMap<>();
    }


    public void selectedItem(int postion ,int flag){
        selectionValueMap.put(postion, flag);
        //notifyDataSetChanged();
    }

    public void removeSelection(int position){
        selectionValueMap.remove(position);
        //notifyDataSetChanged();
    }

    public void removeItem(){
        List<ShopList> shopListsToRemove = new ArrayList<>();
        Set<Integer> mapKeySet = selectionValueMap.keySet();
        Iterator keyIterator = mapKeySet.iterator();
        while(keyIterator.hasNext()){
            int key = (Integer) keyIterator.next();
            shopListsToRemove.add(getItem(key));
            //DataValueList.remove(selectionValueMap.get(key));
        }
        int i;
        for(i = 0; i < shopListsToRemove.size(); i++){
            remove(shopListsToRemove.get(i));
            DbFileImplementation.getInstance().deleteShopList(shopListsToRemove.get(i));
            Log.d("remove_shopList", new SimpleDateFormat("yyyy-MM-dd HH:mm").format(shopListsToRemove.get(i).date));
        }
        selectionValueMap.clear();
        notifyDataSetChanged();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View v = convertView;
        ViewHolder holder;

        if (convertView == null) {
            holder = new ViewHolder();

            LayoutInflater vi = LayoutInflater.from(mContext);
            v = vi.inflate(R.layout.shop_list_entry_row, parent, false);

            holder.shop_id = (TextView) v.findViewById(R.id.shop_id);
            holder.shop_date = (TextView) v.findViewById(R.id.shop_date);
            holder.shop_amount = (TextView) v.findViewById(R.id.shop_amount);
            holder.shop_user = (TextView) v.findViewById(R.id.shop_user);
            holder.shop_people =  (TextView) v.findViewById(R.id.shop_people);
            holder.shop_paid = (ImageView) v.findViewById(R.id.shop_isPaid);

            v.setTag(holder);
        }else{
            holder = (ViewHolder)v.getTag();
        }

        // refresh background
        if(position%2 == 0){
            int sdk = android.os.Build.VERSION.SDK_INT;
            if(sdk < android.os.Build.VERSION_CODES.JELLY_BEAN) {
                v.setBackgroundDrawable(mContext.getResources().getDrawable(R.drawable.table_item_background_alt));
            } else {
                v.setBackground(mContext.getResources().getDrawable(R.drawable.table_item_background_alt));
            }
        }else{
            int sdk = android.os.Build.VERSION.SDK_INT;
            if(sdk < android.os.Build.VERSION_CODES.JELLY_BEAN) {
                v.setBackgroundDrawable(mContext.getResources().getDrawable(R.drawable.table_item_background));
            } else {
                v.setBackground(mContext.getResources().getDrawable(R.drawable.table_item_background));
            }
        }

        final ShopList p = (ShopList)getItem(position);

        if (p != null) {

            if (holder.shop_id != null) {
                holder.shop_id.setText(Long.toString(p.id));
            }
            if (holder.shop_date != null) {
                holder.shop_date.setText(new SimpleDateFormat("yyyy-MM-dd HH:mm").format(p.date));
            }
            if (holder.shop_amount != null) {

                holder.shop_amount.setText(mContext.getString(R.string.euro)+" "+Float.toString(p.amount_total));
            }
            if (holder.shop_user != null) {
                holder.shop_user.setText("By "+p.owner);
            }

            if (holder.shop_people != null) {
                holder.shop_people.setText("");
                List<String> names = new ArrayList<>();
                for (User u : p.users)
                    names.add(u.name);
                holder.shop_people.append(TextUtils.join(", ", names));

            }

            holder.shop_paid.setVisibility(View.VISIBLE);
            if (holder.shop_paid != null) {
                if(p.paid)
                    holder.shop_paid.setVisibility(View.VISIBLE);
                else
                    holder.shop_paid.setVisibility(View.INVISIBLE);
            }
        }

        return v;

    }

    static class ViewHolder {
        int position;
        public TextView shop_id;
        public TextView shop_date;
        public TextView shop_amount;
        public TextView shop_user;
        public TextView shop_people;
        public ImageView shop_paid;
    }

}
