package it.abapp.mobile.shoppingtogether;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CalendarView;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.NumberPicker;
import android.widget.TimePicker;
import android.widget.Toast;

import java.util.Calendar;
import java.util.Date;

/**
 * Created by Alessandro on 13/04/2015.
 */
public class DialogSetShopInfoFragment extends DialogFragment {
    Date date;
    String owner;
    float amount;
    int  n_user;

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

        // retrieve the lectures for dialog population
        Calendar c = Calendar.getInstance();
        Bundle args = getArguments();
        if(args != null){
            date = (Date)args.getSerializable("date");
            c.setTime(date);
            owner = args.getString("owner");
            amount = args.getFloat("amount");
            n_user = args.getInt("n_users");
        }

        // Inflate and set the layout for the dialog
        // Pass null as the parent view because its going in the dialog layout
        final View dialog_root = inflater.inflate(R.layout.dialog_edit_shop_list, null);

        // population by passed object
        final EditText viewOwner = (EditText)dialog_root.findViewById(R.id.dialog_owner);
        final EditText viewAmount = (EditText)dialog_root.findViewById(R.id.dialog_amount);
        final NumberPicker numPicker = (NumberPicker)dialog_root.findViewById(R.id.dialog_n_picker);
        final TimePicker timePicker = (TimePicker)dialog_root.findViewById(R.id.dialog_time_picker);
        final CalendarView viewCalendar = (CalendarView)dialog_root.findViewById(R.id.dialog_calendarView);

        viewOwner.setText(owner);
        viewAmount.setText("");
        if(amount > 0)
            viewAmount.setText(Float.toString(amount));
        numPicker.setMinValue(0);
        numPicker.setMaxValue(100);
        numPicker.setValue(n_user);
        timePicker.setCurrentHour(c.get(Calendar.HOUR_OF_DAY));
        timePicker.setCurrentMinute(c.get(Calendar.MINUTE));
        viewCalendar.setDate(c.getTimeInMillis(),true,true);


        builder.setView(dialog_root)
                // Add action buttons

                .setPositiveButton(R.string.confirm, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        // prepare return bundle
                        CalendarView cv = (CalendarView)dialog_root.findViewById(R.id.dialog_calendarView);
                        TimePicker tp = (TimePicker)dialog_root.findViewById(R.id.dialog_time_picker);

                        String owner = viewOwner.getText().toString();
                        int n_user = numPicker.getValue();
                        String innerAmount = viewAmount.getText().toString();

                        Calendar c = Calendar.getInstance();
                        c.setTime(new Date(viewCalendar.getDate()));
                        c.set(Calendar.HOUR_OF_DAY,tp.getCurrentHour());
                        c.set(Calendar.MINUTE,tp.getCurrentMinute());
                        Date selected_date = c.getTime();

                        if(innerAmount.isEmpty()){
                            String toast_info = getString(R.string.void_amount);
                            Toast.makeText(getActivity(), toast_info,
                                    Toast.LENGTH_SHORT).show();
                            ((EditShopList)getActivity()).finish();
                            return;
                        }

                        float amount = Float.valueOf(innerAmount);
                        int sel_h = tp.getCurrentHour();
                        int sel_m = tp.getCurrentMinute();


                        if(owner.isEmpty() || amount <= 0)

                        {
                            String toast_info = getString(R.string.void_shop_list);
                            Toast.makeText(getActivity(), toast_info,
                                    Toast.LENGTH_SHORT).show();
                            ((EditShopList)getActivity()).finish();
                            return;
                        }

                        // call the interface method
                        ((EditShopList)getActivity()).updateMainInfo(selected_date,n_user,amount,owner);
                        DialogSetShopInfoFragment.this.getDialog().dismiss();
                    }
                })
                .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        ((EditShopList)getActivity()).finish();
                        DialogSetShopInfoFragment.this.getDialog().cancel();
                    }
                });

        return builder.create();
    }

}