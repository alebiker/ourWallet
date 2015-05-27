package it.abapp.mobile.shoppingtogether;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.provider.MediaStore;

import com.googlecode.leptonica.android.Pix;
import com.googlecode.tesseract.android.TessBaseAPI;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by alex on 27/05/15.
 */
public class OCR {

    private TessBaseAPI api;
    private List<Integer> prices;
    private List<String> names;
    private int amount;

    public static OCR newInstance(){
        OCR ocr = new OCR();
        if(ocr.init())
            return ocr;
        return null;
    }

    private OCR(){

    }

    private boolean init(){
        this.api = new TessBaseAPI();
        String path = "file:///android_asset/";
        InputStream itaFile;
        BufferedReader reader = null;
        /*
        try {
            reader = new BufferedReader(
                    new InputStreamReader(Utils.getContext().getAssets().open("tessdata/ita.traineddata")));

            // do reading, usually loop until end of file reading
            String mLine = reader.readLine();
            while (mLine != null) {
                mLine = reader.readLine();
            }
        } catch (IOException e) {
            //log the exception
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    //log the exception
                }
            }
        }
        */
        try {
            path = Utils.getContext().getExternalFilesDir(null).getCanonicalPath();
        } catch (IOException e) {
            e.printStackTrace();
        }
        api.setDebug(true);
        return api.init(path, "ita");
    }

    public void process(){
        this.prices = new ArrayList<>();
        this.names = new ArrayList<>();
        //setNumber();
        String text = api.getUTF8Text();
        api.end();
        //TODO process
    }

    public void setImage(String imgPath){
        File image = new File(imgPath);
        if(image.exists()) {

            BitmapFactory.Options bmOptions = new BitmapFactory.Options();

            Bitmap bitmap = BitmapFactory.decodeFile(image.getAbsolutePath(), bmOptions);

            api.setImage(new File(imgPath));
        }
    }

    public void setMode(boolean mode){
        api.setDebug(mode);
    }


    public void setNumber(){
        //For example if we want to only detect numbers
        api.setVariable(TessBaseAPI.VAR_CHAR_WHITELIST, "1234567890");
        api.setVariable(TessBaseAPI.VAR_CHAR_BLACKLIST, "!@#$%^&*()_+=-qwertyuiop[]}{POIU" +
                "YTREWQasdASDfghFGHjklJKLl;L:'\"\\|~`xcvXCVbnmBNM,./<>?");
    }
    public void setPricesRect(){

    }

    public void setNamesRect(){

    }

    public void setTotalRect(){

    }

    public List<Integer> getPrices(){
        return null;
    }

    public List<String> getNames(){
        return null;
    }

    public int getAmount(){
        return 0;
    }

}
