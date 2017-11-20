package it.abapp.mobile.shoppingtogether;

import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import com.albori.android.utilities.Utilities;

import java.text.SimpleDateFormat;

import it.abapp.mobile.shoppingtogether.model.ShopList;
import it.abapp.mobile.shoppingtogether.model.ShopListEntry;
import it.abapp.mobile.shoppingtogether.model.User;


public class ShowShopListActivity extends AppCompatActivity {

    ShopList sl;
    ViewGroup header;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_show_shop_list);

        Utils.getInstance(getApplicationContext());
        Utilities.getInstance(getApplicationContext());

        Intent intent = getIntent();
        sl = (ShopList) intent.getSerializableExtra("shopList");
        new getShopListAsyncTask().execute();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.

        //noinspection SimplifiableIfStatement
        switch (item.getItemId()) {
            case R.id.action_edit:
                launchEditActivity();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_show_shop_list, menu);
        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        if (requestCode == 1) {
            if(resultCode == RESULT_OK){
                ShopList sl_copy = (ShopList)data.getSerializableExtra("shopList");
                sl = DbFileImplementation.getInstance().getShoppingList(sl.id);
                showShopList();
            }
            if (resultCode == RESULT_CANCELED) {
                //Write your code if there's no result
            }
        }
    }

    private void showShopList() {

        if(header == null) {
            header = (ViewGroup)findViewById(R.id.shop_header);
        }

        ((TextView)header.findViewById(R.id.shop_date)).setText(new SimpleDateFormat("yyyy-MM-dd HH:mm").format(sl.date));

        ((TextView)header.findViewById(R.id.shop_owner)).setText(sl.owner);
        if(sl.paid)
            ((TextView)header.findViewById(R.id.shop_isPaidText)).setText(getResources().getString(R.string.yes));
        else
            ((TextView)header.findViewById(R.id.shop_isPaidText)).setText(getResources().getString(R.string.no));

        ((TextView)header.findViewById(R.id.shop_isPaidText)).setEnabled(sl.paid);


        refreshTable(-1);
        refreshSummary();
    }

    private void refreshSummary(){
        ((TextView)findViewById(R.id.edit_amount_common)).setText(Utilities.formatToCash(sl.common.amount));
        ((TextView)findViewById(R.id.edit_amount_common_single)).setText("("+Utilities.formatToCash(sl.common.amount/sl.users.size())+")");
        ((TextView)findViewById(R.id.amount_total)).setText(Utilities.formatToCash(sl.amount_total));
    }

    private void refreshHeader(TableLayout table){
        TableRow rowHeader = (TableRow)table.findViewById(R.id.edit_table_header);

        for(User u : sl.users){
            TextView userView = (TextView)rowHeader.findViewWithTag(u);
            if(userView == null) {
                LayoutInflater vi;
                vi = LayoutInflater.from(this);
                userView = (TextView)vi.inflate(R.layout.table_user_name, rowHeader, false);
                //userView = (TextView)rowHeader.getVirtualChildAt(rowHeader.getVirtualChildCount()-1);

                TableRow.LayoutParams lParams = (TableRow.LayoutParams)userView.getLayoutParams();
                lParams.column = sl.users.indexOf(u)+1;
                lParams.weight = 1/(float)sl.users.size();
                userView.setLayoutParams(lParams);
                //set textview parameters
                /*userView = new TextView(rowHeader.getContext());

                int width = getResources().getDimensionPixelSize(R.dimen.table_user_col_size);
                //int width = (int) (getResources().getDimensionPixelSize(R.dimen.table_user_col_size)/getResources().getDisplayMetrics().density);
                TableRow.LayoutParams lParams = new TableRow.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT);
                lParams.column = sl.users.indexOf(u)+1;
                userView.setLayoutParams(lParams);
                userView.setTextSize(getResources().getDimension(R.dimen.table_header_text_size));
                userView.setGravity(Gravity.CENTER);
                */
                    /*userView = (TextView)*///vi.inflate(R.layout.edit_table_header_textview, (ViewGroup)rowHeader);
                userView.setTag(u);

                rowHeader.addView(userView,sl.users.indexOf(u)+1);
                //TODO check
                //TableRow.LayoutParams commParams = new TableRow.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT);

                TableRow.LayoutParams commParams =
                        (TableRow.LayoutParams)((TextView)rowHeader.findViewById(R.id.common))
                                .getLayoutParams();

                commParams.column = rowHeader.getVirtualChildCount();
                ((TextView)rowHeader.findViewById(R.id.common)).setLayoutParams(commParams);
            }
            TableRow.LayoutParams lParams = (TableRow.LayoutParams)userView.getLayoutParams();
            lParams.weight = 1/(float)sl.users.size();
            userView.setText(u.name);

            TextView commView = (TextView)rowHeader.findViewById(R.id.common);
            lParams = (TableRow.LayoutParams)commView.getLayoutParams();
            lParams.weight = 1/(float)sl.users.size();

            //TODO align with other column
        }
    }
    private void refreshTable(int row){
        View rootView = findViewById(R.id.activity_edit_id);
        //ListView listView = (ListView) rootView.findViewById(R.id.listView);
        // get data from the table by the ListAdapter
        //ListAdapter customAdapter = new ShoppingListItemAdapter(this, R.layout.shop_list_item_entry_row, sl.items);
        //listView.setAdapter(customAdapter);
        TableLayout table = (TableLayout)rootView.findViewById(R.id.edit_table_items);
        if (row == -1) {
            refreshHeader((TableLayout)rootView.findViewById(R.id.edit_table));
            int c;
            for(c = 0; c < sl.items.size(); c++){
                refreshRow(table,c);
            }
        }
        else {
            refreshRow(table, row);
        }
        refreshPersonal((TableLayout)rootView.findViewById(R.id.edit_table));
    }
    private void refreshPersonal(TableLayout table){
        TableRow rowFooter = (TableRow)table.findViewById(R.id.edit_table_footer);

        for(User u : sl.users){
            TextView userView = (TextView)rowFooter.findViewWithTag(u);
            if(userView == null) {
                LayoutInflater vi = LayoutInflater.from(ShowShopListActivity.this);
                vi.inflate(R.layout.table_footer_textview, rowFooter, true);
                userView = (TextView)rowFooter.getChildAt(rowFooter.getChildCount()-1);

                TableRow.LayoutParams lParams = (TableRow.LayoutParams)userView.getLayoutParams();
                userView.setLayoutParams(lParams);
                lParams.column = sl.users.indexOf(u)+1;

                userView.setTag(u);

/*                userView = new TextView(rowFooter.getContext());

                int width = (int) (getResources().getDimensionPixelSize(R.dimen.table_user_col_size)/getResources().getDisplayMetrics().density);
                TableRow.LayoutParams lParams = new TableRow.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT);
                lParams.column = sl.users.indexOf(u)+1;
                userView.setLayoutParams(lParams);
                userView.setTextSize(getResources().getDimension(R.dimen.table_footer_text_size));
                userView.setTextColor(getResources().getColor(android.R.color.holo_blue_light));
                userView.setTypeface(null, Typeface.BOLD);
                userView.setGravity(Gravity.CENTER);
                userView.setTag(u);

                rowFooter.addView(userView,sl.users.indexOf(u)+1);*/
            }
            userView.setText(Utilities.formatToCash(u.amount+sl.common.amount/sl.users.size()));
        }
    }
    private void refreshRow(TableLayout table, int row) {

        //TODO clickable to edit item info
        //TODO selectable to delete item
        boolean creation = false;

        TableRow rowView =  (TableRow)table.getChildAt(row);
        RelativeLayout item_info;
        ShopListEntry p = sl.items.get(row);
        LinearLayout user_layout;
        LayoutInflater vi;
        int qty;

        // complete recreation of row
        if(rowView != null)
            table.removeViewAt(row);

        vi = LayoutInflater.from(table.getContext());
        rowView = (TableRow)vi.inflate(R.layout.shop_list_item_entry_row, null);
        if(row%2 == 0)
            rowView.setBackgroundColor(Color.parseColor("#E5E5E5"));
        table.addView(rowView,row);
        item_info = (RelativeLayout)rowView.findViewById(R.id.row_item_info);
        item_info.setTag(p);

        user_layout = ((LinearLayout)rowView.findViewById(R.id.row_item_users));

        TextView item_name = (TextView) rowView.findViewById(R.id.row_item_name);
        if (item_name != null) {
            item_name.setText(p.getName());
        }

        TextView item_price = (TextView) item_info.findViewById(R.id.row_item_price);
        if (item_price != null) {
            item_price.setText(Utilities.formatToCash(p.getPrice()));
        }

        TextView item_qty = (TextView) item_info.findViewById(R.id.row_item_qty);
        if (item_qty != null) {
            item_qty.setText(Integer.toString(p.getQty()));
        }


        //TODO check why tags are all lost after an activity switch, or are different
        for(User u : sl.users){
            LinearLayout userView = (LinearLayout)user_layout.findViewWithTag(u);
            if(userView == null) {
                vi = LayoutInflater.from(table.getContext());
                userView = (LinearLayout) vi.inflate(R.layout.item_user_no_button, user_layout, false);
                //userView.setLayoutParams(new TableRow.LayoutParams(sl.users.indexOf(u)+1));
                user_layout.addView(userView,user_layout.getChildCount()-1);
                setColBackground((ViewGroup)user_layout,(View)userView);
                userView.setTag(u);
            }

            LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams) userView.getLayoutParams();
            layoutParams.weight = 1/(float)sl.users.size();

            //adjust common weight
            TextView commonView = (TextView) user_layout.findViewById(R.id.row_item_common_qty);
            layoutParams = (LinearLayout.LayoutParams) commonView.getLayoutParams();
            layoutParams.weight = 1/(float)sl.users.size();

            TextView view_qty = (TextView)userView.findViewById(R.id.item_qty);
            //CheckBox cb = (CheckBox)userView.findViewById(R.id.checkbox);
            view_qty.setText(Integer.toString(0));
            //cb.setChecked(false);

            if (sl.m_user_item_list.get(u).containsKey(p))
                if (sl.m_user_item_list.get(u).get(p) > 0) {
                    qty = sl.m_user_item_list.get(u).get(p);
                    view_qty.setText(Integer.toString(qty));
                    //cb.setChecked(true);
                }
        }

        // add the common
        TextView common_qty = (TextView)rowView.findViewById(R.id.row_item_common_qty);
        if(common_qty != null) {
            //CheckBox cb = new CheckBox(getContext());
            qty = sl.m_user_item_list.get(sl.common).get(p);
            common_qty.setText(Integer.toString(qty));
        }
    }

    private void setColBackground(ViewGroup parent, View view) {
        // refresh background
        if(parent.indexOfChild(view)%2 == 0)
            view.setBackgroundColor(getResources().getColor(R.color.row_alt));
    }

    private void launchEditActivity(){
        // create and initialize the intent
        Intent intent = new Intent(ShowShopListActivity.this, EditShopListActivity.class);

        Bundle b = new Bundle();
        // TODO check
        b.putSerializable("shopList", sl);
        intent.putExtras(b);

        startActivityForResult(intent,1);
    }

    private class getShopListAsyncTask extends AsyncTask<Void, Void, Void> {

        ProgressDialog dialog;
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            dialog = ProgressDialog.show(ShowShopListActivity.this, null, getResources().getString(R.string.loading), true, false);
        }

        @Override
        protected Void doInBackground(Void... params) {
            sl = DbFileImplementation.getInstance().getShoppingList(sl.id);
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {

            showShopList();
            dialog.dismiss();
        }
    }

}
