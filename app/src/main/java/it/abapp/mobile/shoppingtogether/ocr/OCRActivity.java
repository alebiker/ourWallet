package it.abapp.mobile.shoppingtogether.ocr;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.ListView;


import com.albori.android.utilities.Utilities;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import it.abapp.mobile.shoppingtogether.R;
import it.abapp.mobile.shoppingtogether.model.ShopListEntry;
import it.abapp.mobile.shoppingtogether.Utils;
import it.alborile.mobile.ocr.client.OCRWork;
import it.alborile.mobile.ocr.client.OCRWorkProcessor;
import it.alborile.mobile.ocr.client.OcrResult;
import it.alborile.mobile.ocr.client.Ocr;


public class OCRActivity extends AppCompatActivity {

    private static final String TAG = "OCRActivity";
    public static final String ITEMS = "items";

    public static final int RESULT_IMPORT = 3;
    public static final int RESULT_EDIT = 4;

    Uri mImgPath;
    private Ocr.Parameters mParamsName,mParamsPrice;

    private List<Ocr.Job> mJobs;
    private File mFile;
    private Ocr.Parameters mParams;
    private ItemListAdapter mlvAdapter;
    private ListView mlv;
    private ItemMap mItemMap;
    private boolean isTaskFinish;
    private OCRWorkProcessor mWorkProcessor;
    private OcrAsyncTask mOcrTask;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ocr);

        Utils.getInstance(this);
        Utilities.getInstance(this);


        Uri imgPath = null;
        HashMap<Rect, Rect> itemsRects = null;

        if(getIntent().getParcelableExtra(Intents.Recognize.EXTRA_INPUT) != null) {
            imgPath = getIntent().getParcelableExtra(Intents.Recognize.EXTRA_INPUT);
        }

        if (getIntent().getSerializableExtra(Intents.Recognize.EXTRA_RECTS) != null) {
            itemsRects = (HashMap<Rect,Rect>) getIntent().getSerializableExtra(Intents.Recognize.EXTRA_RECTS);
        }

        // retrive or default OCR params
        if(getIntent().getParcelableExtra(Intents.Recognize.EXTRA_PARAMETERS) != null) {
            mParams = getIntent().getParcelableExtra(Intents.Recognize.EXTRA_PARAMETERS);
            mParamsName = new Ocr.Parameters(mParams);
            mParamsPrice = new Ocr.Parameters(mParamsName);
        }
        else {
            Log.d(TAG,"No params passed, setup the default ones");
            mParamsName = new Ocr.Parameters();
            mParamsPrice = new Ocr.Parameters();
            mParams = new Ocr.Parameters();
            setDefaultParamsConfig(mParams);
            setDefaultParamsConfig(mParamsName);
            setDefaultParamsConfig(mParamsPrice);

        }

        // adjust params to type format
        setDefaultParamsConfig(mParamsName);
        setDefaultParamsConfig(mParamsPrice);
        setParamsToPriceFormat(mParamsPrice);
        setParamsToNameFormat(mParamsName);

        mlv = (ListView) findViewById(R.id.ocr_activity_list);
        mJobs = new ArrayList<>();
        mItemMap = new ItemMap();
        mlvAdapter = new ItemListAdapter(this, R.layout.ocr_entry_row);
        _setupListAdapter();
        mWorkProcessor = new OCRWorkProcessor(this);
        mOcrTask = new OcrAsyncTask();

        if(imgPath != null){
            this.mImgPath = imgPath;
        }

        if(itemsRects != null) {
            for (Map.Entry<Rect, Rect> rects : itemsRects.entrySet()) {
                mItemMap.addEntry(rects.getKey(), rects.getValue());
            }
        }

    }

    @Override
    protected void onStart() {
        super.onStart();
        // The activity is about to become visible.
        mlv.setAdapter(mlvAdapter);
    }

    @Override
    protected void onResume(){
        super.onResume();
        if (!isTaskFinish && !(mOcrTask.getStatus() == AsyncTask.Status.RUNNING)){
            _startOCR();
        }
    }

    private void _startOCR() {

        if(!Utilities.isValidImage(mImgPath) || mItemMap.isEmpty()) {
            badParametersPassed();
        }else {
            setupOCRService();

            Utilities.executeAsyncTask(mOcrTask, mWorkProcessor);
        }
    }

    @Override
    protected void onStop(){
        super.onStop();
        freeResource();
    }

    @Override
    public void onBackPressed()
    {
        if(mWorkProcessor.getStatus() == OCRWorkProcessor.Status.BUSY)
            stopOCRTask();

        // code here to show dialog
        setResult(RESULT_EDIT);
        super.onBackPressed();  // optional depending on your needs
    }

    private void badParametersPassed() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.ocr_activity_bad_input_dialog_til);
        builder.setMessage(R.string.ocr_activity_bad_input_dialog_msg);
        // Add the buttons
        builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                // User clicked OK button
            }
        });

        builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                finishAndBackToParentActivity();
            }
        });
        AlertDialog alertDialog = builder.create();
        alertDialog.show();
    }

    private void finishAndBackToParentActivity() {
        stopOCRTask();
        setResult(RESULT_EDIT);
        finish();
    }

    private void stopOCRTask() {
        if (!mJobs.isEmpty()) {
            for(Ocr.Job job : mJobs){
                job.cancel();
            }
        }

        mOcrTask.cancel(false);
        synchronized (mWorkProcessor) {
            mWorkProcessor.notify();
        }
    }

    // free temporary resoureces when are not more needed
    private void freeResource() {
        deleteOcrBitmap();
    }

    private void setDefaultParamsConfig(Ocr.Parameters params) {
        params.setLanguage("eng");
        params.setFlag(Ocr.Parameters.FLAG_DEBUG_MODE, true);
        params.setFlag(Ocr.Parameters.FLAG_SPELLCHECK, false);
        params.setPageSegMode(Ocr.Parameters.PSM_SINGLE_COLUMN);
        params.setVariable(Ocr.Parameters.VAR_CHAR_BLACKLIST, "!@#%^&*()_+=-[]}{n\'\"\\;:|~`/<>?");
    }

    private void setParamsToNameFormat(Ocr.Parameters mParamsName) {
        mParamsName.setVariable(Ocr.Parameters.VAR_CHAR_WHITELIST, ",.0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz ");
        mParamsName.setPageSegMode(Ocr.Parameters.PSM_SINGLE_LINE);
        mParamsName.setFlag(Ocr.Parameters.FLAG_DETECT_TEXT, false);
    }

    public void setParamsToPriceFormat(Ocr.Parameters mParamsPrice) {
        mParamsName.setFlag(Ocr.Parameters.FLAG_DETECT_TEXT, false);
        mParamsPrice.setVariable(Ocr.Parameters.VAR_CHAR_WHITELIST, "0123456789,.");
        mParamsPrice.setVariable(Ocr.Parameters.VAR_CHAR_BLACKLIST, mParamsPrice.getVariable(Ocr.Parameters.VAR_CHAR_BLACKLIST) + " ");
        mParamsPrice.setPageSegMode(Ocr.Parameters.PSM_SINGLE_WORD);
    }

    private void setupOCRService() {
        try {
            Bitmap bmp = null;
            if (!mItemMap.getItems().isEmpty())
                bmp = Utilities.loadImage(mImgPath);

            OCRWork ocrNameWork = new OCRWork(bmp, mParamsName, mItemMap.getNameRects()) {
                @Override
                public void onProcessResult(OcrResult result, Rect rect) {
                    final ShopListEntry item = mItemMap.getItemByName(rect);
                    final OcrResult finalResult = result;
                    Log.d("ITEM", "Found name:" + finalResult.getString());
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            setItemToList(item, finalResult.getString());
                        }
                    });
                }

                @Override
                public void onCompletedResults(List<OcrResult> results) {
                    Log.d(TAG, "Ocr name completed");
                    // Starting price recognition
                }

                //TODO enqueue and register callback as token
                @Override
                public void processOcrWorkRequest(OCRWorkProcessor processor, Bitmap mBitmap, List<Rect> rects) {

                }
            };

            ocrNameWork.setName("items");
            mWorkProcessor.enqueue(ocrNameWork);


            OCRWork ocrPriceWork = new OCRWork(bmp, mParamsPrice, mItemMap.getPriceRects()) {
                @Override
                public void onProcessResult(OcrResult result, Rect rect) {
                    final ShopListEntry item = mItemMap.getItemByPrice(rect);
                    String result_str = result.getString();
                    String priceFormat = result_str.replace(',', '.');
                    float price = 0;
                    try {
                        price = Float.parseFloat(priceFormat);
                        Log.d(TAG, "Found price:" + price);
                    } catch (NumberFormatException nfe) {
                        Log.e(TAG, "Price found is not a number! " + priceFormat);
                    }

                    final float finalPrice = price;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            setPriceToList(item, finalPrice);
                        }
                    });
                }

                @Override
                public void onCompletedResults(List<OcrResult> results) {
                    Log.d(TAG, "Ocr price completed");
                }

                @Override
                public void processOcrWorkRequest(OCRWorkProcessor mOcr, Bitmap mBitmap, List<Rect> rects) {

                }
            };

            ocrPriceWork.setName("prices");
            mWorkProcessor.enqueue(ocrPriceWork);
            if (bmp != null)
                bmp.recycle();
        }catch (Exception e){
            Log.e(TAG, "Errors occurs while sending OCR works!");
            return;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_ocr, menu);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.

        //noinspection SimplifiableIfStatement
        switch (item.getItemId()) {
            case R.id.action_import:
                confirmAndImportItems();
                // TODO confirm and do OCR;
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    private void deleteOcrBitmap() {
        Utilities.deleteImage(mImgPath.getPath());
        /*
        if(getContentResolver().delete(mImgPath,null,null) != 1)
            Log.d(TAG,"Error in deleting temp bitmap");
            */
    }

    private void _setupListAdapter() {
        mlv.setChoiceMode(GridView.CHOICE_MODE_MULTIPLE_MODAL);
        mlv.setMultiChoiceModeListener(new AbsListView.MultiChoiceModeListener() {

            int selectionCounter = 0;

            @Override
            public void onItemCheckedStateChanged(ActionMode mode, int position,
                                                  long id, boolean checked) {

                if (checked) {
                    selectionCounter++;
                    ((ItemListAdapter) mlv.getAdapter()).selectedItem(position, position);


                } else {
                    selectionCounter--;
                    ((ItemListAdapter) mlv.getAdapter()).removeSelection(position);
                }
                setCabTitle(mode);

            }

            /**
             * Override to handle the function to perform when a menu item is clicked
             */
            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                switch (item.getItemId()) {
                    case R.id.action_delete:
                        selectionCounter = 0;
                        //(ItemListAdapter)mlv.getAdapter().removeItem();
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

            private void setCabTitle(ActionMode mode) {
                mode.setTitle(getResources().getString(R.string.cab_title));
                mode.setSubtitle(Integer.toString(selectionCounter) + " "
                        + getResources().getString(R.string.cab_subtitle));
            }

        });
    }

    protected Ocr.Parameters getParameters() {

        return mParams;
    }

    protected Ocr.Parameters getParametersName() {
        return mParamsName;
    }

    protected Ocr.Parameters getParametersPrice() {
        return mParamsPrice;
    }

    // Import the recognized text into shoplist
    private void confirmAndImportItems() {
        freeResource();

        // get the selected items
        List<ShopListEntry> importingItem = ((ItemListAdapter) mlv.getAdapter()).getItemsSelected();
        Intent resultIntent = new Intent();
        resultIntent.putExtra(ITEMS, (ArrayList)importingItem);
        setResult(RESULT_OK, resultIntent);
        finish();
    }

    private void setPriceToList(ShopListEntry item, float itemPrice) {
        item.setPrice(itemPrice);
        if (!mlvAdapter.contains(item))
            mlvAdapter.add(item);
        mlvAdapter.notifyDataSetChanged();
    }

    private void setItemToList(ShopListEntry item, String itemName) {
        item.setName(itemName);
        if (!mlvAdapter.contains(item))
            mlvAdapter.add(item);
        mlvAdapter.notifyDataSetChanged();
    }

    public boolean isFiniskTask() {
        return isTaskFinish;
    }

    private class ItemListAdapter extends ArrayAdapter<ShopListEntry> {

        private Context mContext;
        HashMap<ShopListEntry,Boolean> item_priced;

        private HashMap<Integer, Integer> selectionValueMap;

        public ItemListAdapter(Context context, int resource, final List<ShopListEntry> objects) {
            super(context, resource, objects);
            mContext = context;
            selectionValueMap = new HashMap<>();
            item_priced = new HashMap<ShopListEntry,Boolean>(){
                {
                    for (ShopListEntry item : objects) {
                        put(item, item.getPrice() != 0);
                    }
                }
            };
        }

        public ItemListAdapter(Context context, int resource) {
            this(context, resource, new ArrayList<ShopListEntry>());
        }

        public void selectedItem(int postion ,int flag){
            selectionValueMap.put(postion, flag);
            //notifyDataSetChanged();
        }

        public void removeSelection(int position){
            selectionValueMap.remove(position);
            //notifyDataSetChanged();
        }

        @Override
        public void add(ShopListEntry item){
            item_priced.put(item, false);
            super.add(item);
        }

        public List<ShopListEntry> getItemsSelected(){
            List<ShopListEntry> itemsSelected = new ArrayList<>();
            Set<Integer> mapKeySet = selectionValueMap.keySet();
            Iterator keyIterator = mapKeySet.iterator();
            while(keyIterator.hasNext()){
                int key = (Integer) keyIterator.next();
                itemsSelected.add(getItem(key));
            }
            return itemsSelected;
        }

        //TODO
        /*
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
        */


        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            View v = convertView;
            final ViewHolder holder;

            if (convertView == null) {
                holder = new ViewHolder();

                LayoutInflater vi = LayoutInflater.from(mContext);
                v = vi.inflate(R.layout.ocr_entry_row, parent, false);

                holder.item_name = (EditText) v.findViewById(R.id.item_name);
                holder.item_price = (EditText) v.findViewById(R.id.item_price);
                holder.checkbox = (CheckBox) v.findViewById(R.id.item_checkbox);

                holder.checkbox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        if (isChecked)
                            selectedItem(position, 1);
                        else
                            removeSelection(position);
                    }
                });

                holder.item_name.addTextChangedListener(new TextWatcher() {

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before,
                                              int count) {
                        // TODO Auto-generated method stub
                    }

                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count,
                                                  int after) {
                        // TODO Auto-generated method stub
                    }

                    @Override
                    public void afterTextChanged(Editable s) {
                        ShopListEntry item = getItem(position);
                        if (item != null)
                            item.setName(s.toString());
                    }
                });

                holder.item_price.addTextChangedListener(new TextWatcher() {

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before,
                                              int count) {
                        // TODO Auto-generated method stub
                    }

                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count,
                                                  int after) {
                        // TODO Auto-generated method stub
                    }

                    @Override
                    public void afterTextChanged(Editable s) {
                        ShopListEntry item = getItem(position);
                        if (item != null)
                            item.setPrice(Float.valueOf(s.toString()));
                    }
                });

                v.setTag(holder);
            }else{
                holder = (ViewHolder)v.getTag();
            }


            // refresh background
            int res_bg;
            if(position%2 == 0) {
                res_bg = R.drawable.table_item_background_alt;
            }else {
                res_bg = R.drawable.table_item_background;
            }
            int sdk = android.os.Build.VERSION.SDK_INT;
            if(sdk < android.os.Build.VERSION_CODES.JELLY_BEAN) {
                v.setBackgroundDrawable(mContext.getResources().getDrawable(res_bg));
            } else {
                v.setBackground(mContext.getResources().getDrawable(res_bg));
            }

            final ShopListEntry p = getItem(position);


            if (p != null) {
                // Change only if needed to avoid call overhead to text changer
                if (holder.item_name != null &&
                        !holder.item_name.getText().toString().contentEquals(p.getName())) {
                    holder.item_name.setText(p.getName());
                }
                if (holder.item_price != null &&
                        !holder.item_price.getText().toString().contentEquals(Float.toString(p.getPrice()))) {
                    holder.item_price.setText(Float.toString(p.getPrice()));
                }
            }

            return v;

        }

        public boolean contains(ShopListEntry item) {

            return this.item_priced.containsKey(item);
        }

        private class ViewHolder {
            int position;
            public EditText item_name;
            public EditText item_price;
            public CheckBox checkbox;
            public ImageView shop_paid;
        }

    }

    private class OcrAsyncTask extends AsyncTask<OCRWorkProcessor,Void,Boolean>{

        ProgressDialog dialog;
        OCRWorkProcessor processor;


        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            dialog = ProgressDialog.show(OCRActivity.this, null, getResources().getString(R.string.ocr_processing_loading), true, false);
        }

        @Override
        protected Boolean doInBackground(OCRWorkProcessor... ocrWorkProcessors) {
            processor = ocrWorkProcessors[0];
            processor.start();

            // wait until every request has been replied
            try {
                while (!isCancelled() &&
                        mWorkProcessor.getStatus() == OCRWorkProcessor.Status.BUSY) {
                    synchronized (mWorkProcessor) {
                        mWorkProcessor.wait(1000);
                    }
                }
                mWorkProcessor.stop();
                Log.d(TAG,"OCR activities closed");
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return true;
        }


        @Override
        protected void onPostExecute(Boolean success){
            dialog.dismiss();
            synchronized (OCRActivity.this) {
                isTaskFinish = true;
                OCRActivity.this.notifyAll();
            }
        }
    }

    private class ItemMap {
        HashMap<Rect, ShopListEntry> mNameMap;
        HashMap<Rect, ShopListEntry> mPriceMap;
        List<ShopListEntry> shopList;

        public ItemMap() {
            this.mNameMap = new HashMap<>();
            this.mPriceMap = new HashMap<>();
            this.shopList = new ArrayList<>();
        }

        public ItemMap(List<ShopListEntry> shopList) {
            this();
            this.shopList = shopList;
        }

        public List<ShopListEntry> getItems(){
            return this.shopList;
        }

        synchronized public boolean addEntry(Rect name, Rect price){
            ShopListEntry item = new ShopListEntry();
            shopList.add(item);
            mNameMap.put(name, item);
            mPriceMap.put(price, item);
            return true;
        }

        synchronized public ShopListEntry getItemByPrice(Rect price){
            ShopListEntry item = null;
            if (mPriceMap.containsKey(price))
                item =  mPriceMap.get(price);
            return item;
        }

        synchronized public ShopListEntry getItemByName(Rect name){
            ShopListEntry item = null;
            if (mNameMap.containsKey(name))
                item =  mNameMap.get(name);
            return item;
        }

        synchronized public List<Rect> getNameRects() {
            List<Rect> name = new ArrayList<>();
            name.addAll(mNameMap.keySet());
            return name;
        }

        synchronized public List<Rect> getPriceRects() {
            List<Rect> price = new ArrayList<>();
            price.addAll(mPriceMap.keySet());
            return price;
        }

        public boolean isEmpty() {
            return shopList.isEmpty();
        }
    }


}
