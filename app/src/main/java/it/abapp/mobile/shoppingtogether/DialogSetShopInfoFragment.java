package it.abapp.mobile.shoppingtogether;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.NumberPicker;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import it.abapp.mobile.shoppingtogether.model.ShopList;

/**
 * Created by Alessandro on 13/04/2015.
 */
public class DialogSetShopInfoFragment extends DialogFragment {
    String owner;
    float amount;
    int n_user;
    private Date date;
    private boolean isNewShopList = true;

    public static DialogSetShopInfoFragment newInstance(ShopList shopList) {
        DialogSetShopInfoFragment frag = new DialogSetShopInfoFragment();
        if(shopList != null) {
            Bundle args = new Bundle();
            args.putSerializable("date", shopList.date);
            args.putString("owner", shopList.owner);
            args.putFloat("amount", shopList.amount_total);
            args.putInt("n_users", shopList.users.size());

            frag.setArguments(args);
        }
        return frag;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        // Get the layout inflater
        LayoutInflater inflater = getActivity().getLayoutInflater();

        date = new Date();

        // retrieve the lectures for dialog population
        Bundle args = getArguments();
        if(args != null){
            isNewShopList = false;
            date = (Date)args.getSerializable("date");
            owner = args.getString("owner");
            amount = args.getFloat("amount");
            n_user = args.getInt("n_users");
        }

        Calendar c = Calendar.getInstance();
        c.setTime(date);

        // Inflate and set the layout for the dialog
        // Pass null as the parent view because its going in the dialog layout
        final View dialog_root = inflater.inflate(R.layout.dialog_edit_shop_list, null);

        // population by passed object
        final EditText viewOwner = (EditText)dialog_root.findViewById(R.id.dialog_owner);
        final EditText viewAmount = (EditText)dialog_root.findViewById(R.id.dialog_amount);
        final NumberPicker numPicker = (NumberPicker)dialog_root.findViewById(R.id.dialog_n_picker);
        final TextView dateTV = (TextView) dialog_root.findViewById(R.id.dialog_date_value_tv);
//        final CalendarView viewCalendar = (CalendarView)dialog_root.findViewById(R.id.dialog_calendarView);

        dateTV.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //TODO show up calaendar
            }
        });

        viewOwner.setText(owner);
        viewAmount.setText("");
        if(amount > 0)
            viewAmount.setText(Float.toString(amount));
        numPicker.setMinValue(0);
        numPicker.setMaxValue(100);
        numPicker.setValue(n_user);
        dateTV.setText(SimpleDateFormat.getDateInstance().format(c.getTime()));


        builder.setView(dialog_root)
                // Add action buttons

                .setPositiveButton(R.string.confirm, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        // prepare return bundle

                        String owner = viewOwner.getText().toString();
                        int n_user = numPicker.getValue();
                        String innerAmount = viewAmount.getText().toString();

                        Calendar c = Calendar.getInstance();
                        c.setTime(date);
//                        c.set(Calendar.HOUR_OF_DAY,tp.getCurrentHour());
//                        c.set(Calendar.MINUTE,tp.getCurrentMinute());
                        Date selected_date = date;

                        if(innerAmount.isEmpty()){
                            String toast_info = getString(R.string.void_amount);
                            Toast.makeText(getActivity(), toast_info,
                                    Toast.LENGTH_SHORT).show();
                            ((EditShopListActivity)getActivity()).finish();
                            return;
                        }

                        float amount = Float.valueOf(innerAmount);


                        if(owner.isEmpty() || amount <= 0) {
                            String toast_info = getString(R.string.void_shop_list);
                            Toast.makeText(getActivity(), toast_info,
                                    Toast.LENGTH_SHORT).show();
                            ((EditShopListActivity)getActivity()).finish();
                            return;
                        }

                        // call the interface method
                        ((EditShopListActivity)getActivity()).updateMainInfo(selected_date, n_user, amount, owner);

                        DialogSetShopInfoFragment.this.getDialog().dismiss();
                    }
                })
                .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        DialogSetShopInfoFragment.this.getDialog().cancel();
                    }
                });

        return builder.create();
    }

    @Override
    public void onCancel(DialogInterface dialog) {
        // kill activity if new
        if(isNewShopList)
            ((EditShopListActivity)getActivity()).finish();
        DialogSetShopInfoFragment.this.getDialog().cancel();
    }
}