package it.abapp.mobile.shoppingtogether;

import java.util.Date;

/**
 * Created by Alessandro on 13/04/2015.
 */
public interface EditDialogListener {

    public void updateMainInfo(Date date, int n_users, float amount, String owner);
}
