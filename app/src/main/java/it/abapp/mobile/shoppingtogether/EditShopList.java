package it.abapp.mobile.shoppingtogether;

import android.annotation.TargetApi;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.ActionBarActivity;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;


public class EditShopList extends ActionBarActivity {

    static final int SELECT_MAIN_INFO_REQUEST = 1;

    private ShopList sl;
    private TableLayout table;
    private TableRow selected_row;
    private View selected_col;
    private boolean row_column;
    private boolean new_edit = true;
    private ViewGroup header;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_shop_list);

        // restore if needed
        if( savedInstanceState != null ) {
            sl = (ShopList)savedInstanceState.getSerializable("shopList");
            showShopList();
            return;
        }

        Intent intent = getIntent();
        sl = (ShopList) intent.getSerializableExtra("shopList");
        if(sl == null) {
            // is request a new shop list
            DialogSetShopInfoFragment my_dialog = new DialogSetShopInfoFragment();
            //my_dialog.setTargetFragment(this, SELECT_MAIN_INFO_REQUEST);
            // show shop list dialog initialization
            FragmentManager fm = this.getSupportFragmentManager();
            // Create and show the dialog.
            DialogSetShopInfoFragment newFragment = DialogSetShopInfoFragment.newInstance(null);

//            DialogSetShopInfoFragment newFragment = DialogSetShopInfoFragment.newInstance(new Date(),null,0,0);
            newFragment.show(fm, "dialog");

            sl = new ShopList();
            sl.date = Calendar.getInstance().getTime();
            sl.setUsers(0);
            sl.setTotal(0);
            sl.owner = "?";
        }
        else {
            //fill header with detailed info
            // check if it passed a complete shop list
            if (sl.items == null)
                new_edit = false;
            new getShopListAsyncTask().execute();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_edit_shop_list, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.

        //noinspection SimplifiableIfStatement
        switch (item.getItemId()) {
            case R.id.action_save:
                new saveShopAsyncTask().execute();
                return true;
            case R.id.action_add_user:
                showUserDialog();
                return true;
            case R.id.action_add_item:
                showItemDialog();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }

    }

    @Override
    public void onCreateContextMenu(ContextMenu menu,View v,ContextMenu.ContextMenuInfo menuInfo){
        super.onCreateContextMenu(menu, v, menuInfo);
        // set the selected view to be called in onContextItemSelected
        if(row_column){
            selected_row = (TableRow)v;
        }else {
            selected_col = v;
        }
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_contentual_edit_shop_list, menu);
    }

    @Override
    public void onContextMenuClosed(Menu menu) {
        super.onContextMenuClosed(menu);
        if(selected_row != null)
            selected_row.setSelected(false);
        selected_row = null;
        if(selected_col != null)
            selected_col.setSelected(false);
        selected_col = null;
    }

    public boolean onContextItemSelected(MenuItem item) {

        switch (item.getItemId()) {
            case R.id.action_delete:
                if(selected_row != null){
                    delItem((ShopListEntry) selected_row.getTag());
                }else if(selected_col != null){
                    delUser((User) selected_col.getTag());
                }
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable("shopList", sl);
    }

    public void showShopList(){
        if (header == null) {
            header = (ViewGroup) findViewById(R.id.shop_header);
        }

        ((TextView)header.findViewById(R.id.shop_date)).setText(new SimpleDateFormat("yyyy-MM-dd HH:mm").format(sl.date));
        ((TextView)header.findViewById(R.id.shop_owner)).setText(sl.owner);
        ((CheckBox)header.findViewById(R.id.shop_isPaid)).setChecked(sl.paid);
        ((CheckBox)header.findViewById(R.id.shop_isPaid)).setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                sl.paid = isChecked;
            }
        });

        //TODO debug
        refreshTable(null);
        refreshSummary();
        // modify the listView with custom adapter
    }

    public void updateMainInfo(Date date, int n_users, float amount, String owner) {

        sl.date = date;
        sl.setUsers(n_users);
        sl.setTotal(amount);
        sl.owner = owner;

        ((TextView)findViewById(R.id.shop_date)).setText(getString(R.string.date)+": "+new SimpleDateFormat(getString(R.string.date_pattern)).format(sl.date));
        ((TextView)findViewById(R.id.shop_owner)).setText(getString(R.string.owner)+": "+sl.owner);
        ((TextView)findViewById(R.id.amount_total)).setText(Float.toString(sl.amount_total));

        refreshTable(null);
        refreshSummary();
    }

    public void addUser(String name){
        User user = new User(name);
        sl.addUser(user);
        refreshTable(null);
        refreshSummary();
    }

    public void delUser(User user){
        //clean screen
        deleteColumn((TableLayout) findViewById(R.id.edit_table), user);
        sl.delUser(user.name);
        refreshTable(null);
        refreshSummary();
    }

    public void editUser(User user, String name){
        if (sl.editUserName(user, name))
            refreshHeader((TableLayout)findViewById(R.id.edit_table));
    }

    public void addItem(String id, float price, int qty){
        ShopListEntry item = new ShopListEntry(id,price,qty);
        if(!sl.addItem(item))
            return;

        refreshTable(item);
        refreshTable(sl.m_items.get("other"));
        refreshSummary();
        refreshPersonal((TableLayout)findViewById(R.id.edit_table));
    }

    public void editItem(ShopListEntry item, ShopListEntry new_item) {
        if(sl.editItem(item, new_item)) {
            //TODO get the right row to refresh
            refreshRow((TableLayout) findViewById(R.id.edit_table), item);
            refreshRow((TableLayout) findViewById(R.id.edit_table), sl.m_items.get("other"));
            refreshPersonal((TableLayout) findViewById(R.id.edit_table));
        }
        else{
            //error show toast
            ;
        }
    }

    public void delItem(ShopListEntry item) {
        deleteRow((TableLayout)findViewById(R.id.edit_table), item);
        sl.delItem(item);
        refreshTable(null);
        //refreshRow((TableLayout)findViewById(R.id.edit_table), sl.m_items.get("other"));
        //refreshPersonal((TableLayout)findViewById(R.id.edit_table));
        refreshSummary();
    }

    private void refreshTable(ShopListEntry item){
        View rootView = findViewById(R.id.activity_edit_id);
        //ListView listView = (ListView) rootView.findViewById(R.id.listView);
        // get data from the table by the ListAdapter
        //ListAdapter customAdapter = new ShoppingListItemAdapter(this, R.layout.shop_list_item_entry_row, sl.items);
        //listView.setAdapter(customAdapter);
        TableLayout table = (TableLayout)rootView.findViewById(R.id.edit_table_items);
        if (item == null) {
            refreshHeader((TableLayout)rootView.findViewById(R.id.edit_table));
            int c;
            for(ShopListEntry i : sl.items){
                refreshRow(table,i);
            }
        }
        else {
            refreshRow(table, item);
        }
        refreshPersonal((TableLayout)rootView.findViewById(R.id.edit_table));
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private void refreshRow(TableLayout table, ShopListEntry item) {

        TableRow rowView = null;
        RelativeLayout item_info = null;
        ShopList sl;
        sl = item.shopList;
        rowView =  (TableRow)table.findViewWithTag(item);

        if(rowView == null) {
            LayoutInflater vi;
            vi = LayoutInflater.from(table.getContext());
            rowView = (TableRow)vi.inflate(R.layout.shop_list_item_entry_row, null);
            rowView.setTag(item);
            table.addView(rowView,table.getChildCount());
            item_info = (RelativeLayout)rowView.findViewById(R.id.row_item_info);
            if(!item.name.contains("other")) {
                item_info.setTag(item);
                rowView.setOnLongClickListener(new View.OnLongClickListener() {

                    @Override
                    public boolean onLongClick(View v) {
                        row_column = true;
                        v.setSelected(true);
                        v.showContextMenu();
                        return true;
                    }
                });

                registerForContextMenu(rowView);
            }
            else{
                item_info.setOnClickListener(null);
            }
        }else{
            item_info = (RelativeLayout)rowView.findViewById(R.id.row_item_info);
        }

        // refresh background
        if(table.indexOfChild(rowView)%2 == 0){
            int sdk = android.os.Build.VERSION.SDK_INT;
            if(sdk < android.os.Build.VERSION_CODES.JELLY_BEAN) {
                rowView.setBackgroundDrawable(getResources().getDrawable(R.drawable.table_item_background_alt));
            } else {
                rowView.setBackground(getResources().getDrawable(R.drawable.table_item_background_alt));
            }
        }else{
            int sdk = android.os.Build.VERSION.SDK_INT;
            if(sdk < android.os.Build.VERSION_CODES.JELLY_BEAN) {
                rowView.setBackgroundDrawable(getResources().getDrawable(R.drawable.table_item_background));
            } else {
                rowView.setBackground(getResources().getDrawable(R.drawable.table_item_background));
            }
        }

        TextView item_name = (TextView) rowView.findViewById(R.id.row_item_name);

        if (item_name != null) {
            item_name.setText(item.name);
        }

        TextView item_price = (TextView) item_info.findViewById(R.id.row_item_price);
        if (item_price != null) {
            item_price.setText(Utils.formatToCash(item.price));
        }

        TextView item_qty = (TextView) item_info.findViewById(R.id.row_item_qty);
        if (item_qty != null) {
            item_qty.setText("x"+Integer.toString(item.qty));
        }

        int qty;
        LayoutInflater vi;
        vi = LayoutInflater.from(table.getContext());

        LinearLayout user_layout = ((LinearLayout)rowView.findViewById(R.id.row_item_users));

        for(User u : sl.users){
            RelativeLayout userView = (RelativeLayout)user_layout.findViewWithTag(u);
            if(userView == null) {
                userView = (RelativeLayout) vi.inflate(R.layout.item_user,user_layout, false);
                //userView.setLayoutParams(new TableRow.LayoutParams(sl.users.indexOf(u)+1));
                user_layout.addView(userView);
                setColBackground((ViewGroup)user_layout,(View)userView);
                userView.setTag(u);

            }
                TextView view_qty = (TextView)userView.findViewById(R.id.item_qty);
                CheckBox cb = (CheckBox)userView.findViewById(R.id.checkbox);
                view_qty.setText(Integer.toString(0));
                cb.setChecked(false);

                if (sl.m_user_item_list.get(u).containsKey(item))
                    if (sl.m_user_item_list.get(u).get(item) > 0) {
                        qty = sl.m_user_item_list.get(u).get(item);
                        view_qty.setText(Integer.toString(qty));
                        cb.setChecked(true);
                    }
                ((LinearLayout)userView.findViewById(R.id.buttonUp)).setOnClickListener(new IncItemListener(rowView.getVirtualChildCount(),u,item));
                ((LinearLayout)userView.findViewById(R.id.buttonDown)).setOnClickListener(new DecItemListener(rowView.getVirtualChildCount(),u,item));
            }

        // add the common
        TextView common_qty = (TextView)rowView.findViewById(R.id.row_item_common_qty);
        if(common_qty != null) {
            //CheckBox cb = new CheckBox(getContext());
            qty = sl.m_user_item_list.get(sl.common).get(item);
            common_qty.setText(Integer.toString(qty));
        }
    }

    private void setColBackground(ViewGroup parent, View view) {
        // refresh background
        if(parent.indexOfChild(view)%2 == 0)
            view.setBackgroundColor(getResources().getColor(R.color.row_alt));
    }

    private void refreshPersonal(TableLayout table){
        TableRow rowFooter = (TableRow)table.findViewById(R.id.edit_table_footer);

        for(User u : sl.users){
            TextView userView = (TextView)rowFooter.findViewWithTag(u);
            if(userView == null) {
                LayoutInflater vi = LayoutInflater.from(EditShopList.this);
                vi.inflate(R.layout.table_footer_textview,rowFooter,true);
                userView = (TextView)rowFooter.getChildAt(rowFooter.getChildCount()-1);

                TableRow.LayoutParams lParams = (TableRow.LayoutParams)userView.getLayoutParams();
                userView.setLayoutParams(lParams);
                lParams.column = sl.users.indexOf(u)+1;

                userView.setTag(u);

                //rowFooter.addView(userView,sl.users.indexOf(u)+1);
            }
            userView.setText(Utils.formatToCash(u.amount+sl.common.amount/sl.users.size()));
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private void refreshHeader(TableLayout table){
        TableRow rowHeader = (TableRow)table.findViewById(R.id.edit_table_header);

            for(User u : sl.users){
                TextView userView = (TextView)rowHeader.findViewWithTag(u);
                if(userView == null) {
                    LayoutInflater vi;
                    vi = LayoutInflater.from(rowHeader.getContext());
                    userView = (TextView)vi.inflate(R.layout.table_user_name, rowHeader, false);
                    TableRow.LayoutParams lParams = (TableRow.LayoutParams)userView.getLayoutParams();
                    lParams.column = sl.users.indexOf(u)+1;
                    userView.setLayoutParams(lParams);
                    rowHeader.addView(userView,sl.users.indexOf(u)+1);

                    // refresh background
                    if(table.indexOfChild(userView)%2 == 0){
                        int sdk = android.os.Build.VERSION.SDK_INT;
                        if(sdk < android.os.Build.VERSION_CODES.JELLY_BEAN) {
                            userView.setBackgroundDrawable(getResources().getDrawable(R.drawable.table_item_background_alt));
                        } else {
                            userView.setBackground(getResources().getDrawable(R.drawable.table_item_background_alt));
                        }
                    }else{
                        int sdk = android.os.Build.VERSION.SDK_INT;
                        if(sdk < android.os.Build.VERSION_CODES.JELLY_BEAN) {
                            userView.setBackgroundDrawable(getResources().getDrawable(R.drawable.table_item_background));
                        } else {
                            userView.setBackground(getResources().getDrawable(R.drawable.table_item_background));
                        }
                    }


                    userView.setOnClickListener(new EditUserListener(u));
                    userView.setOnLongClickListener(new View.OnLongClickListener() {

                        @Override
                        public boolean onLongClick(View v) {
                            row_column = false;
                            v.setSelected(true);
                            v.showContextMenu();

                            return true;
                        }
                    });
                    registerForContextMenu(userView);
                    //userView.setLayoutParams(new TableRow.LayoutParams(sl.users.indexOf(u)+1));
                    userView.setTag(u);


                }
                userView.setText(u.name);
                //TODO align with other column
            }
    }

    private void refreshSummary(){
        ((TextView)findViewById(R.id.edit_amount_common)).setText(Utils.formatToCash(sl.common.amount));
        ((TextView)findViewById(R.id.edit_amount_common_single)).setText("("+Utils.formatToCash(sl.common.amount/sl.users.size())+")");
        ((TextView)findViewById(R.id.amount_total)).setText(Utils.formatToCash(sl.amount_total));
    }

    private void deleteRow(TableLayout edit_table, ShopListEntry item){
        TableRow rowView = null;
        rowView =  (TableRow)edit_table.findViewWithTag(item);

        ((TableLayout)rowView.getParent()).removeView(rowView);
    }

    private void deleteColumn(TableLayout table, User user){
        // delete singolar colum in every part of table
        //TODO check bugs
        TableRow rowView = null;
        rowView = (TableRow)table.findViewById(R.id.edit_table_header);
        rowView.removeView(rowView.findViewWithTag(user));

        TableLayout item_table = (TableLayout)table.findViewById(R.id.edit_table_items);
        int i;
        for(i = 0; i < item_table.getChildCount(); i++) {
            rowView = (TableRow) item_table.getChildAt(i);
            //TODO check bugs
            LinearLayout user_layout = (LinearLayout)rowView.findViewById(R.id.row_item_users);
            user_layout.removeView(rowView.findViewWithTag(user));
        }

        rowView = (TableRow)table.findViewById(R.id.edit_table_footer);
        rowView.removeView(rowView.findViewWithTag(user));
    }

    public void showEditShopDialog(View v){
        FragmentManager fm = getSupportFragmentManager();
        // Create and show the dialog.
        DialogSetShopInfoFragment newFragment = DialogSetShopInfoFragment.newInstance(sl);
        newFragment.show(fm, "dialog");
    }

    public void showEditItemDialog(View v){
        FragmentManager fm = getSupportFragmentManager();
        // Create and show the dialog.
        DialogEditItemShopListFragment newFragment = DialogEditItemShopListFragment.newInstance((ShopListEntry)(v.getTag()));
        newFragment.show(fm, "dialog");
    }
/*
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Check which request we're responding to
        if (requestCode == _REQUEST) {
            // Make sure the request was successful
            if (resultCode == RESULT_OK) {
                // The user picked a contact.
                // The Intent's data Uri identifies which contact was selected.
                // Fetch the intent and fill the screen with update value
                // Do something with the contact here (bigger example below)
            }
        }
    }
*/

    private class IncItemListener implements View.OnClickListener {

        int index;
        User user;
        ShopListEntry item;

        public IncItemListener(int index, User user, ShopListEntry item) {
            this.index = index;
            this.user = user;
            this.item = item;
        }

        @Override
        public void onClick(View v) {
            sl.incUserItem(user,item);
            refreshTable(item);
            refreshPersonal((TableLayout)findViewById(R.id.edit_table));
            refreshSummary();
        }
    }

    private class DecItemListener implements View.OnClickListener {

        int index;
        User user;
        ShopListEntry item;

        public DecItemListener(int index, User user, ShopListEntry item) {
            this.index = index;
            this.user = user;
            this.item = item;
        }

        @Override
        public void onClick(View v) {
            sl.decUserItem(user, item);
            refreshTable(item);
            refreshPersonal((TableLayout)findViewById(R.id.edit_table));
            refreshSummary();
        }
    }

    private class EditUserListener implements View.OnClickListener {

        User user;

        public EditUserListener(User user) {
            this.user = user;
        }

        @Override
        public void onClick(View v) {

            FragmentManager fm = getSupportFragmentManager();
            // Create and show the dialog.
            DialogEditUserShopListFragment newFragment = DialogEditUserShopListFragment.newInstance(user);
            newFragment.show(fm, "dialog");
        }
    }

    private void showUserDialog(){
        FragmentManager fm = getSupportFragmentManager();
        // Create and show the dialog.
        DialogEditUserShopListFragment newFragment = DialogEditUserShopListFragment.newInstance(null);
        newFragment.show(fm, "dialog");
    }

    private void showItemDialog(){
        FragmentManager fm = getSupportFragmentManager();
        // Create and show the dialog.
        DialogEditItemShopListFragment newFragment = DialogEditItemShopListFragment.newInstance(null);
        newFragment.show(fm, "dialog");
    }

    private ArrayList<View> getAllChildren(View v) {

        if (!(v instanceof ViewGroup)) {
            ArrayList<View> viewArrayList = new ArrayList<View>();
            viewArrayList.add(v);
            return viewArrayList;
        }

        ArrayList<View> result = new ArrayList<View>();

        ViewGroup viewGroup = (ViewGroup) v;
        for (int i = 0; i < viewGroup.getChildCount(); i++) {

            View child = viewGroup.getChildAt(i);

            ArrayList<View> viewArrayList = new ArrayList<View>();
            viewArrayList.add(v);
            viewArrayList.addAll(getAllChildren(child));

            result.addAll(viewArrayList);
        }
        return result;
    }

    private class saveShopAsyncTask extends AsyncTask<Void, Void, Boolean> {

        ProgressDialog dialog;
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            dialog = ProgressDialog.show(EditShopList.this, null, getResources().getString(R.string.loading), true, false);
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            return DbFileImplementation.getInstance().setShoppingList(sl);
        }

        @Override
        protected void onPostExecute(Boolean result) {

            if (result) {
                Toast.makeText(EditShopList.this, getString(R.string.shoplist_save_done),
                        Toast.LENGTH_SHORT).show();

                if(!new_edit) {
                    Intent returnIntent = new Intent();
                    returnIntent.putExtra("shopList", sl);

                    setResult(RESULT_OK, returnIntent);
                    finish();
                }
                else {
                    Intent intent = new Intent(getApplicationContext(), ShowShopList.class);

                    Bundle b = new Bundle();
                    b.putSerializable("shopList", sl);
                    intent.putExtras(b);

                    startActivity(intent);
                }

            } else {
                Toast.makeText(EditShopList.this, getString(R.string.position_save_error),
                        Toast.LENGTH_SHORT).show();
            }
            dialog.dismiss();
        }
    }

    private class getShopListAsyncTask extends AsyncTask<Void, Void, Void> {

        ProgressDialog dialog;
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            dialog = ProgressDialog.show(EditShopList.this, null, getResources().getString(R.string.loading), true, false);
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
