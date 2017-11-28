package it.abapp.mobile.shoppingtogether;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.provider.MediaStore;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
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

import com.albori.android.utilities.Utilities;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import it.abapp.mobile.shoppingtogether.model.ShopList;
import it.abapp.mobile.shoppingtogether.model.ShopListEntry;
import it.abapp.mobile.shoppingtogether.model.User;
import it.abapp.mobile.shoppingtogether.ocr.HighliterActivity;
import it.abapp.mobile.shoppingtogether.ocr.Intents;
import it.abapp.mobile.shoppingtogether.ocr.OCRActivity;

//TODO check the availability of the camera for aquisition

public class EditShopListActivity extends AppCompatActivity {


    private static final String TAG = "EditShopListActivity";
    static final int SELECT_MAIN_INFO_REQUEST = 1;
    static final int REQUEST_TAKE_PHOTO = 1;
    static final int REQUEST_RECOGNIZE_OCR = 2;
    private static final int HIGHLITER_ITEMS = 3;

    private static final String traineddataPath = "tessdata/ita.traineddata";

    private int defaultTextViewColor;

    private ShopList sl;
    private TableLayout table;
    private TableRow selected_row;
    private View selected_col;
    private boolean row_column;
    private boolean new_edit = true;
    private ViewGroup header;
    private String mNewCameraPhotoPath;
    private boolean changed = false;
    private boolean isShopListInitialized = false;
    private String mCurrentPhotoPath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_shop_list);

        Utilities.getInstance(getApplicationContext());
        Utils.getInstance(getApplicationContext());

        defaultTextViewColor = new TextView(this).getTextColors().getDefaultColor();

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
        setListener();
    }

    private void setListener() {
        findViewById(R.id.amount_total).setOnClickListener(new EditShopListener());
        findViewById(R.id.header_info_fixed).setOnClickListener(new EditShopListener());
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
            case R.id.action_start_ocr:
                new saveShopAsyncTask().execute();
                return true;
            case R.id.action_add_user:
                showUserDialog();
                return true;
            case R.id.action_add_item:
                showItemDialog();
                return true;
            case R.id.action_take_photo:
                initImageProcessing();
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

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_TAKE_PHOTO) {
            final boolean isCamera;
            if (data == null) {
                isCamera = true;
            } else {
                final String action = data.getAction();
                if (action == null) {
                    isCamera = false;
                } else {
                    isCamera = action.equals(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
                }
            }
            if (resultCode == RESULT_OK) {

                Uri selectedImageUri;
                if (isCamera) {
                    selectedImageUri = Uri.withAppendedPath(Uri.parse("file://"), mNewCameraPhotoPath);
                    Utilities.galleryAddPic(mNewCameraPhotoPath);
                } else {
                    selectedImageUri = data == null ? null : data.getData();
                    Utilities.deleteImage(mNewCameraPhotoPath);
                }

                Intent detectIntent = new Intent(getApplicationContext(), HighliterActivity.class);
                detectIntent.putExtra(Intents.Recognize.EXTRA_INPUT, selectedImageUri);

                startActivityForResult(detectIntent, REQUEST_RECOGNIZE_OCR);
            }else{
                Utilities.deleteImage(mNewCameraPhotoPath);
            }
        }else if(requestCode == REQUEST_RECOGNIZE_OCR && resultCode == RESULT_OK){
            ArrayList<ShopListEntry> items = (ArrayList<ShopListEntry>) data.getSerializableExtra(OCRActivity.ITEMS);
            for(ShopListEntry item : items){
                addItem(item.getName(), item.getPrice(), 1);
            }
        }else if(requestCode == HIGHLITER_ITEMS && resultCode == RESULT_OK){
            /*HashMap<RectF, RectF> arrayRects = (HashMap<RectF, RectF>) data.getSerializableExtra(HighliterActivity.RECTS);
            for (Map.Entry<RectF,RectF> item : arrayRects.entrySet()) {
                Log.i("name", Float.toString(item.getKey().centerX()) + " " + Float.toString(item.getKey().centerY()));
                Log.i("price", Float.toString(item.getValue().centerX()) + " " + Float.toString(item.getValue().centerY()));
            }*/

            ArrayList<ShopListEntry> items = (ArrayList<ShopListEntry>) data.getSerializableExtra(OCRActivity.ITEMS);
            for(ShopListEntry item : items){
                addItem(item.getName(), item.getPrice(), 1);
            }
        }
    }

    @Override
    public void onBackPressed(){
        if(new_edit || changed) {
            new AlertDialog.Builder(this)
                    .setTitle("Save List")
                    .setMessage("Do you want save before exit?")
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {

                        public void onClick(DialogInterface dialog, int whichButton) {
                            new saveShopAsyncTask().execute();
                        }
                    })
                    .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {

                        public void onClick(DialogInterface dialog, int whichButton) {
                            finish();
                        }
                    }).show();
        }else
            super.onBackPressed();
    }

    private void setListChanged(){
        changed = true;
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

        setListChanged();

        sl.date = date;
        sl.setUsers(n_users);
        sl.setTotal(amount);
        sl.owner = owner;

        ((TextView)findViewById(R.id.shop_date)).setText(getString(R.string.date)+": "+new SimpleDateFormat(getString(R.string.date_pattern)).format(sl.date));
        ((TextView)findViewById(R.id.shop_owner)).setText(getString(R.string.owner)+": "+sl.owner);
        ((TextView)findViewById(R.id.amount_total)).setText(Float.toString(sl.amount_total));

        refreshTable(null);
        refreshSummary();
        this.isShopListInitialized = true;
    }

    public void addUser(String name){
        setListChanged();

        User user = new User(name);
        sl.addUser(user);
        refreshTable(null);
        refreshSummary();
    }

    public void delUser(User user){
        setListChanged();

        //clean screen
        deleteColumn((TableLayout) findViewById(R.id.edit_table), user);
        sl.delUser(user.name);
        refreshTable(null);
        refreshSummary();
    }

    public void editUser(User user, String name){
        setListChanged();

        if (sl.editUserName(user, name))
            refreshHeader((TableLayout)findViewById(R.id.edit_table));
    }

    public void addItem(String id, float price, int qty){
        setListChanged();

        ShopListEntry item = new ShopListEntry(id,price,qty);
        if(!sl.addItem(item))
            return;

        refreshTable(item);
        refreshTable(sl.m_items.get("other"));
        refreshSummary();
        refreshPersonal((TableLayout)findViewById(R.id.edit_table));
    }

    public void editItem(ShopListEntry item, ShopListEntry new_item) {
        setListChanged();

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
        setListChanged();

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
        sl = item.getShopList();
        rowView =  (TableRow)table.findViewWithTag(item);

        if(rowView == null) {
            LayoutInflater vi;
            vi = LayoutInflater.from(table.getContext());
            rowView = (TableRow)vi.inflate(R.layout.shop_list_item_entry_row, null);
            rowView.setTag(item);
            table.addView(rowView,table.getChildCount());
            item_info = (RelativeLayout)rowView.findViewById(R.id.row_item_info);
            if(!item.getName().contains("other")) {
                item_info.setTag(item);
                rowView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        showEditItemDialog(v);
                    }
                });
                
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
            item_name.setText(item.getName());
        }

        TextView item_price = (TextView) item_info.findViewById(R.id.row_item_price);
        if (item_price != null) {
            item_price.setText(Utilities.formatToCash(item.getPrice()));
        }

        TextView item_qty = (TextView) item_info.findViewById(R.id.row_item_qty);
        if (item_qty != null) {
            item_qty.setText("x"+Integer.toString(item.getQty()));
        }

        int qty;
        LayoutInflater vi;
        vi = LayoutInflater.from(table.getContext());

        LinearLayout user_layout = ((LinearLayout)rowView.findViewById(R.id.row_item_users));

        for(User u : sl.users){
            LinearLayout userView = (LinearLayout)user_layout.findViewWithTag(u);
            if(userView == null) {
                userView = (LinearLayout) vi.inflate(R.layout.item_user,user_layout, false);


                //userView.setLayoutParams(new TableRow.LayoutParams(sl.users.indexOf(u)+1));
                user_layout.addView(userView, user_layout.getChildCount() - 1);
                setColBackground((ViewGroup) user_layout, (View) userView);
                userView.setTag(u);
                userView.findViewById(R.id.buttonUp).setOnClickListener(new IncItemListener(rowView.getVirtualChildCount(), u, item));
                userView.findViewById(R.id.buttonDown).setOnClickListener(new DecItemListener(rowView.getVirtualChildCount(), u, item));

            }
            // for fill the empty space if any
            LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams) userView.getLayoutParams();
            layoutParams.weight = 1/(float)sl.users.size();

            // common space adjust
            TextView commonView = (TextView) user_layout.findViewById(R.id.row_item_common_qty);
            layoutParams = (LinearLayout.LayoutParams) commonView.getLayoutParams();
            layoutParams.weight = 1/(float)sl.users.size();

            // No Checkbox version
            /*
                CheckBox cb = (CheckBox)userView.findViewById(R.id.checkbox);

                cb.setChecked(false);

            */
            TextView view_qty = (TextView)userView.findViewById(R.id.item_qty);
            if (sl.m_user_item_list.get(u).containsKey(item)) {
                    if (sl.m_user_item_list.get(u).get(item) > 0) {
                        qty = sl.m_user_item_list.get(u).get(item);
                        view_qty.setTextColor(getResources().getColor(R.color.material_deep_teal_500));
                        view_qty.setText(Integer.toString(qty));
                        //cb.setChecked(true);
                    } else {
                        view_qty.setTextColor(defaultTextViewColor);
                        view_qty.setText(Integer.toString(0));
                    }
                }else{
                    view_qty.setTextColor(defaultTextViewColor);
                    view_qty.setText(Integer.toString(0));
                }

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
                LayoutInflater vi = LayoutInflater.from(EditShopListActivity.this);
                vi.inflate(R.layout.table_footer_textview,rowFooter,true);
                userView = (TextView)rowFooter.getChildAt(rowFooter.getChildCount()-1);

                TableRow.LayoutParams lParams = (TableRow.LayoutParams)userView.getLayoutParams();
                userView.setLayoutParams(lParams);
                lParams.column = sl.users.indexOf(u)+1;

                userView.setTag(u);

                //rowFooter.addView(userView,sl.users.indexOf(u)+1);
            }
            userView.setText(Utilities.formatToCash(u.amount+sl.common.amount/sl.users.size()));
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

                TableRow.LayoutParams lParams = (TableRow.LayoutParams)userView.getLayoutParams();
                lParams.weight = 1/(float)sl.users.size();
                userView.setText(u.name);

                TextView commView = (TextView)rowHeader.findViewById(R.id.common);
                lParams = (TableRow.LayoutParams)commView.getLayoutParams();
                lParams.weight = 1/(float)sl.users.size();
                //TODO align with other column
            }
    }

    private void refreshSummary(){
        ((TextView)findViewById(R.id.edit_amount_common)).setText(Utilities.formatToCash(sl.common.amount));
        ((TextView)findViewById(R.id.edit_amount_common_single)).setText("("+Utilities.formatToCash(sl.common.amount/sl.users.size())+")");
        ((TextView)findViewById(R.id.amount_total)).setText(Utilities.formatToCash(sl.amount_total));
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

    private boolean checkAssets() {
        if(!Utilities.openFile(traineddataPath, Utilities.RESOURCES_LOCATION.EXTERNAL).exists()){
            AsyncTask<String, Void, Boolean> at = new CopyTessdataAsyncTask();
            at.execute(traineddataPath);
            return false;
        }else {
            return true;
        }
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

    private class EditShopListener implements View.OnClickListener {


        public EditShopListener() {
        }

        @Override
        public void onClick(View v) {

            showEditShopDialog(null);
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

    private void initImageProcessing(){
        if(checkAssets()){
            dispatchTakePictureIntent();
        }
    }

    private void dispatchTakePictureIntent() {


//        //Ver.1
//        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
//        // Ensure that there's a camera activity to handle the intent
//        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
//            // Create the File where the photo should go
//            File photoFile = null;
//            try {
//                photoFile = Utils.createPrivateImageFile();
//                mNewCameraPhotoPath = photoFile.getAbsolutePath();
//            } catch (IOException ex) {
//                // Error occurred while creating the File
//
//            }
//            // Continue only if the File was successfully created
//            if (photoFile != null) {
//                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT,
//                        Uri.fromFile(photoFile));
//                startActivityForResult(takePictureIntent, REQUEST_TAKE_PHOTO);
//            }
//        }



        //Ver.2 Multi Intent

        // Create the File where the photo should go
        Uri newPhotoUri = null;
        //photoFile = Utils.createPrivateImageFile();
        String timeStamp = Long.toString(System.currentTimeMillis());
//            newPhotoUri = Utilities.getCaptureImageOutputUri("ocrCf_"+timeStamp, "jpg");
        File imgFile = null;
        try {
            imgFile = Utilities.createPublicImageFile("shopList_" + timeStamp, "jpg");
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Save a file: path for use with ACTION_VIEW intents
        mCurrentPhotoPath = imgFile.getAbsolutePath();

//        File newFile2 = Utilities.openCreateFile("Cf_" + timeStamp + ".jpg", Utilities.RESOURCES_LOCATION.EXTERNAL);
        newPhotoUri = Utilities.File2ContentUri(imgFile, BuildConfig.APPLICATION_ID + ".provider");
        mNewCameraPhotoPath = newPhotoUri.getPath();

        final List<Intent> allIntents = new ArrayList<>();

        // Camera.
        Intent i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

        i.putExtra(MediaStore.EXTRA_OUTPUT, newPhotoUri);

        if (Build.VERSION.SDK_INT>=Build.VERSION_CODES.LOLLIPOP) {
            i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        }
        else if (Build.VERSION.SDK_INT>=Build.VERSION_CODES.JELLY_BEAN) {
            ClipData clip=
                    ClipData.newUri(getContentResolver(), "A photo", newPhotoUri);

            i.setClipData(clip);
            i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        }
        else {
            List<ResolveInfo> resInfoList=
                    getPackageManager()
                            .queryIntentActivities(i, PackageManager.MATCH_DEFAULT_ONLY);

            for (ResolveInfo resolveInfo : resInfoList) {
                String packageName = resolveInfo.activityInfo.packageName;
                grantUriPermission(packageName, newPhotoUri,
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            }
        }
        allIntents.add(i);

        // Gallery.
        final Intent galleryIntent = new Intent();
        galleryIntent.setType("image/*");
        galleryIntent.setAction(Intent.ACTION_PICK);


        //intents.add(galleryIntent);

        /*
        // Filesystem.
        final Intent filesystemIntent = new Intent();
        filesystemIntent.setType("image/*");
        filesystemIntent.setAction(Intent.ACTION_GET_CONTENT);
        */

        // Chooser of filesystem options.
        final Intent chooserIntent = Intent.createChooser(galleryIntent, "Select Source");

        // Add the camera options.
        chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, allIntents.toArray(new Parcelable[allIntents.size()]));

        startActivityForResult(chooserIntent, REQUEST_TAKE_PHOTO);

    }

//    private void dispatchTakePictureIntent() {
//
//        /*
//        //Ver.1
//        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
//        // Ensure that there's a camera activity to handle the intent
//        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
//            // Create the File where the photo should go
//            File photoFile = null;
//            try {
//                photoFile = Utils.createPrivateImageFile();
//                mNewCameraPhotoPath = photoFile.getAbsolutePath();
//            } catch (IOException ex) {
//                // Error occurred while creating the File
//
//            }
//            // Continue only if the File was successfully created
//            if (photoFile != null) {
//                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT,
//                        Uri.fromFile(photoFile));
//                startActivityForResult(takePictureIntent, REQUEST_TAKE_PHOTO);
//            }
//        }
//
//*/
//
//        //Ver.2 Multi Intent
//
//        // Create the File where the photo should go
//        File photoFile = null;
//        try {
//            //photoFile = Utils.createPrivateImageFile();
//            String timeStamp = new SimpleDateFormat("MM_dd_HH_mm").format(new java.util.Date());
//            photoFile = Utilities.createPublicImageFile("shoplist_"+timeStamp, "jpg");
//            mNewCameraPhotoPath = photoFile.getAbsolutePath();
//        } catch (IOException ex) {
//            // Error occurred while creating the File
//            Log.e(TAG, "Cannot create image file!");
//            ex.printStackTrace();
//            return;
//        }
//
//        final List<Intent> intents = new ArrayList<>();
//
//        // Camera.
//        final List<Intent> cameraIntents = new ArrayList<Intent>();
//        final Intent captureIntent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
//        final PackageManager packageManager = getPackageManager();
//        final List<ResolveInfo> listCam = packageManager.queryIntentActivities(captureIntent, 0);
//        for(ResolveInfo res : listCam) {
//            final String packageName = res.activityInfo.packageName;
//            final Intent intent = new Intent(captureIntent);
//            intent.setComponent(new ComponentName(res.activityInfo.packageName, res.activityInfo.name));
//            intent.setPackage(packageName);
//            intent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(photoFile));
//            cameraIntents.add(intent);
//        }
//
//        intents.addAll(cameraIntents);
//
//        // Gallery.
//        final Intent galleryIntent = new Intent();
//        galleryIntent.setType("image/*");
//        galleryIntent.setAction(Intent.ACTION_PICK);
//        //intents.add(galleryIntent);
//
//        /*
//        // Filesystem.
//        final Intent filesystemIntent = new Intent();
//        filesystemIntent.setType("image/*");
//        filesystemIntent.setAction(Intent.ACTION_GET_CONTENT);
//        */
//
//        // Chooser of filesystem options.
//        final Intent chooserIntent = Intent.createChooser(galleryIntent, "Select Source");
//
//        // Add the camera options.
//        chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, intents.toArray(new Parcelable[intents.size()]));
//
//        startActivityForResult(chooserIntent, REQUEST_TAKE_PHOTO);
//
//    }

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
            dialog = ProgressDialog.show(EditShopListActivity.this, null, getResources().getString(R.string.loading), true, false);
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            return DbFileImplementation.getInstance().setShoppingList(sl);
        }

        @Override
        protected void onPostExecute(Boolean result) {
            dialog.dismiss();

            if (result) {
                Toast.makeText(EditShopListActivity.this, getString(R.string.shoplist_save_done),
                        Toast.LENGTH_SHORT).show();

                Intent returnIntent = new Intent();
                returnIntent.putExtra(MainActivity.SHOP_LIST, sl);
                setResult(RESULT_OK, returnIntent);
                finish();
                /*
                if(!new_edit) {



                }
                else {
                    Intent intent = new Intent(getApplicationContext(), ShowShopListActivity.class);

                    Bundle b = new Bundle();
                    b.putSerializable("shopList", sl);
                    intent.putExtras(b);

                    startActivity(intent);
                }
*/
            } else {
                Toast.makeText(EditShopListActivity.this, getString(R.string.position_save_error),
                        Toast.LENGTH_SHORT).show();

                setResult(RESULT_CANCELED, null);
                finish();
            }
            dialog.dismiss();
        }
    }

    private class getShopListAsyncTask extends AsyncTask<Void, Void, Void> {

        ProgressDialog dialog;
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            dialog = ProgressDialog.show(EditShopListActivity.this, null, getResources().getString(R.string.loading), true, false);
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

    private class CopyTessdataAsyncTask extends AsyncTask<String, Void, Boolean> {
        ProgressDialog dialog;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            Log.i(TAG, "Starting initializing assets..");
            dialog = ProgressDialog.show(EditShopListActivity.this, null, getResources().getString(R.string.text_app_initialization), true, false);
        }
        @Override
        protected Boolean doInBackground(String[] tessPaths) {
            File file = null;
            String tessPath = tessPaths[0];
            try {
                InputStream assetsPath = getAssets().open(tessPath);
                assetsPath.available();
                file = Utilities.writeFileFromInputStream(assetsPath, Utilities.openCreateFile(tessPath, Utilities.RESOURCES_LOCATION.EXTERNAL));
            } catch (IOException e) {
                e.printStackTrace();
            }
            return file == null ? false : file.exists();
        }

        @Override
        protected void onPostExecute(Boolean completed){
            String text;
            if (completed){
                text = "Initialization completed!";
                dispatchTakePictureIntent();
            }else{
                text = "Initialization error!";
            }
            Toast.makeText(getApplicationContext(), text, Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        }
    }

}
