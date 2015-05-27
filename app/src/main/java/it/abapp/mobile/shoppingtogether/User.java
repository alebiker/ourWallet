package it.abapp.mobile.shoppingtogether;

import com.googlecode.tesseract.android.TessBaseAPI;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;


/**
 * Created by Alessandro on 13/04/2015.
 */
public class User implements Serializable{
    public String name;
    public float amount = 0;  // personal amount (common not included)

    public User(String name) {
        this.name = name;
    }
}
