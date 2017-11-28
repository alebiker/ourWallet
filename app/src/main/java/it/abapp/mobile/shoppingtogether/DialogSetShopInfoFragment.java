package it.abapp.mobile.shoppingtogether;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CalendarView;
import android.widget.EditText;
import android.widget.NumberPicker;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import java.text.BreakIterator;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

import it.abapp.mobile.shoppingtogether.model.ShopList;

/**
 * Created by Alessandro on 13/04/2015.
 */
public class DialogSetShopInfoFragment extends DialogFragment {

    View dialog_root;
    String owner;
    float amount;
    int n_user;
    private Date date;
    private boolean isNewShopList = true;
    private PopupWindow pw;
    private Date selectedDate;

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
        n_user = 1;

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
        dialog_root = inflater.inflate(R.layout.dialog_edit_shop_list, null);

        // population by passed object
        final EditText viewOwner = (EditText)dialog_root.findViewById(R.id.dialog_owner);
        final EditText viewAmount = (EditText)dialog_root.findViewById(R.id.dialog_amount);
        final NumberPicker numPicker = (NumberPicker)dialog_root.findViewById(R.id.dialog_n_picker);
        final TextView dateTV = (TextView) dialog_root.findViewById(R.id.dialog_date_value_tv);
//        final CalendarView viewCalendar = (CalendarView)dialog_root.findViewById(R.id.dialog_calendarView);

        dateTV.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                initiatePopupWindow(v);
                //TODO show up calaendar
            }
        });

        viewOwner.setText(owner);
        viewAmount.setText("");
        if(amount > 0)
            viewAmount.setText(Float.toString(amount));
        numPicker.setMinValue(1);
        numPicker.setMaxValue(99);
        numPicker.setValue(n_user);
        updateDate();


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

    private void initiatePopupWindow(View v) {
        try {
            //We need to get the instance of the LayoutInflater, use the context of this activity
            LayoutInflater inflater = (LayoutInflater) getActivity()
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            //Inflate the view from a predefined XML layout
            final View layout = inflater.inflate(R.layout.wdg_set_date,
                    (ViewGroup) v.findViewById(R.id.dialog_calendar_section));
            // create a 300px width and 470px height PopupWindow
            pw = new PopupWindow(layout, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true);
            // display the popup in the center
            pw.showAtLocation(v, Gravity.CENTER, 0, 0);

            // setup date popup
            CalendarView cv = (CalendarView) layout.findViewById(R.id.dialog_calendarView);
            cv.setDate(date.getTime());

            cv.setOnDateChangeListener(new CalendarView.OnDateChangeListener() {

                @Override
                public void onSelectedDayChange(CalendarView view, int year, int month,
                                                int dayOfMonth) {
                    Calendar calendar = new GregorianCalendar(year,month,dayOfMonth);
                    selectedDate = calendar.getTime();
                }
            });

            ((Button)layout.findViewById(R.id.wdg_set_date)).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    date = selectedDate;
                    updateDate();
                    pw.dismiss();
                }
            });
//            TextView mResultText = (TextView) layout.findViewById(R.id.server_status_text);
//            Button cancelButton = (Button) layout.findViewById(R.id.end_data_send_button);
//            cancelButton.setOnClickListener(cancel_button_click_listener);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updateDate() {
        TextView dateTV = null;
        dateTV = (TextView) dialog_root.findViewById(R.id.dialog_date_value_tv);
        if(dateTV != null)
            dateTV.setText(SimpleDateFormat.getDateInstance().format(date.getTime()));
    }
}