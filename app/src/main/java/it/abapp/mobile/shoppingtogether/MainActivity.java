package it.abapp.mobile.shoppingtogether;


import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.Checkable;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.ListView;

import android.widget.AbsListView.MultiChoiceModeListener;

import java.util.List;


public class MainActivity extends ActionBarActivity {

    ListView listView;
    ShoppingListAdapter adapter;
    List<ShopList> shopList;
    private List<ResolveInfo> mApps;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        View rootView = findViewById(R.id.main_root);
        // modify the listView
        listView = (ListView) rootView.findViewById(R.id.listView);

        Utils.setContext(getApplicationContext());
        DbFileImplementation.getInstance().initialize();
        shopList = DbFileImplementation.getInstance().getSummaryShoppingLists();
        // get data from the table by the ListAdapter
        adapter = new ShoppingListAdapter(this,R.layout.shop_list_entry_row, shopList);

        listView.setAdapter(adapter);
        listView.setChoiceMode(GridView.CHOICE_MODE_MULTIPLE_MODAL);
        listView.setMultiChoiceModeListener(new AbsListView.MultiChoiceModeListener() {

            int selectionCounter = 0;

            @Override
            public void onItemCheckedStateChanged(ActionMode mode, int position,
                                                  long id, boolean checked) {

                if (checked) {
                    selectionCounter++;
                    ((ShoppingListAdapter)listView.getAdapter()).selectedItem(position, position);


                } else {
                    selectionCounter--;
                    adapter.removeSelection(position);
                }
                setCabTitle(mode);

            }

            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                // TODO Auto-generated method stub
                switch (item.getItemId()) {
                    case R.id.action_delete:
                        selectionCounter = 0;
                        adapter.removeItem();
                        mode.finish();
                        return true;
                    default:
                        return false;
                }
            }

            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                // Inflate the menu for the CAB
                MenuInflater inflater = mode.getMenuInflater();
                inflater.inflate(R.menu.main_menu_contextual, menu);
                //setCabTitle(mode);
                return true;
            }

            @Override
            public void onDestroyActionMode(ActionMode mode) {
                // Here you can make any necessary updates to the activity when
                // the CAB is removed. By default, selected items are deselected/unchecked.
                ;
                selectionCounter = 0;
            }

            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                // Here you can perform updates to the CAB due to
                // an invalidate() request
                return false;
            }

            private void setCabTitle(ActionMode mode){
                mode.setTitle(getResources().getString(R.string.cab_title));
                mode.setSubtitle(Integer.toString(selectionCounter)+" "
                        +getResources().getString(R.string.cab_subtitle));
            }

        });
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                // create and initialize the intent
                Intent intent = new Intent(getApplicationContext(), ShowShopList.class);

                Bundle b = new Bundle();
                // TODO check
                b.putSerializable("shopList", (ShopList)listView.getItemAtPosition(position));
                intent.putExtras(b);

                startActivity(intent);
            }
        });

/*
        getListView().setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {

            @Override
            public boolean onItemLongClick(AdapterView<?> arg0, View arg1,int position, long arg3) {
                getListView().setItemChecked(position, !adapter.isPositionChecked(position));
                return false;
            }
        });
*/

        addListenerOnButton(rootView);
    }

    @Override
    protected  void onStart(){
        super.onResume();
        adapter.notifyDataSetChanged();
    }

    private ListView getListView(){
        return listView;
    }

    public void addListenerOnButton(View rootView) {

        Button btnSubmit = (Button) rootView.findViewById(R.id.button_new_shop_list);

        btnSubmit.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                // create and initialize the intent
                Intent intent = new Intent(getApplicationContext(), EditShopList.class);

                Bundle b = new Bundle();
                // TODO instantiate a valid efficient intent with cmd 'NEW SHOP LIST'
                intent.putExtras(b);

                startActivity(intent);
            }

        });




    }


    public class AppsAdapter extends BaseAdapter {
        public AppsAdapter() {
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            CheckableLayout l;
            ImageView i;

            if (convertView == null) {
                i = new ImageView(MainActivity.this);
                i.setScaleType(ImageView.ScaleType.FIT_CENTER);
                i.setLayoutParams(new ViewGroup.LayoutParams(50, 50));
                l = new CheckableLayout(MainActivity.this);
                l.setLayoutParams(new GridView.LayoutParams(
                        GridView.LayoutParams.WRAP_CONTENT,
                        GridView.LayoutParams.WRAP_CONTENT));
                l.addView(i);
            } else {
                l = (CheckableLayout) convertView;
                i = (ImageView) l.getChildAt(0);
            }

            ResolveInfo info = mApps.get(position);
            i.setImageDrawable(info.activityInfo.loadIcon(getPackageManager()));

            return l;
        }

        public final int getCount() {
            return mApps.size();
        }

        public final Object getItem(int position) {
            return mApps.get(position);
        }

        public final long getItemId(int position) {
            return position;
        }
    }

    public class CheckableLayout extends FrameLayout implements Checkable {
        private boolean mChecked;

        public CheckableLayout(Context context) {
            super(context);
        }

        @SuppressWarnings("deprecation")
        public void setChecked(boolean checked) {
            mChecked = checked;
            setBackgroundDrawable(checked ? getResources().getDrawable(
                    R.color.material_deep_teal_500) : null);
        }

        public boolean isChecked() {
            return mChecked;
        }

        public void toggle() {
            setChecked(!mChecked);
        }

    }

    private void loadApps() {
        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);

        mApps = getPackageManager().queryIntentActivities(mainIntent, 0);
    }


}
