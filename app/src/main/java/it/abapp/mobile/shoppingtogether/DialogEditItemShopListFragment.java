package it.abapp.mobile.shoppingtogether;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CalendarView;
import android.widget.EditText;
import android.widget.NumberPicker;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Calendar;
import java.util.Date;

/**
 * Created by Alessandro on 13/04/2015.
 */
public class DialogEditItemShopListFragment extends DialogFragment {


    public static DialogEditItemShopListFragment newInstance(ShopListEntry item) {
        DialogEditItemShopListFragment frag = new DialogEditItemShopListFragment();
        Bundle args = new Bundle();
        args.putSerializable("item", item);

        frag.setArguments(args);
        return frag;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        // Get the layout inflater
        LayoutInflater inflater = getActivity().getLayoutInflater();

        // retrieve the lectures for dialog population
        Bundle args = getArguments();
        String name;
        float price;
        int qty;
        final ShopListEntry item = (ShopListEntry)args.getSerializable("item");

        // Inflate and set the layout for the dialog
        // Pass null as the parent view because its going in the dialog layout
        final View dialog_root = inflater.inflate(R.layout.dialog_edit_item, null);
        if(item == null)
            ((TextView)dialog_root.findViewById(R.id.dialog_title)).setText(getString(R.string.add_item));
        else
            ((TextView)dialog_root.findViewById(R.id.dialog_title)).setText(getString(R.string.edit_item));

        // population by passed object
        final EditText viewName = (EditText)dialog_root.findViewById(R.id.dialog_item_name);
        final EditText viewPrice = (EditText)dialog_root.findViewById(R.id.dialog_item_price);
        final NumberPicker pickerQty = (NumberPicker)dialog_root.findViewById(R.id.dialog_item_qty);

        if (item == null) {
            viewName.setText("");
            viewPrice.setText("");
            pickerQty.setMinValue(1);
            pickerQty.setMaxValue(999);

        }else{
            viewName.setText(item.name);
            viewPrice.setText(Float.toString(item.price));
            pickerQty.setMinValue(1);
            pickerQty.setMaxValue(999);
            pickerQty.setValue(item.qty);
        }


        builder.setView(dialog_root)
                // Add action buttons

                .setPositiveButton(R.string.confirm, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {

                    String new_name = viewName.getText().toString();
                        String innerPrice = viewPrice.getText().toString();
                        int new_qty = pickerQty.getValue();

                        if(innerPrice.isEmpty()){
                            String toast_info = getString(R.string.void_price);
                            Toast.makeText(getActivity(), toast_info,
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }

                    float new_price = Float.valueOf(viewPrice.getText().toString());


                    if(new_name.isEmpty() || new_price <= 0)

                    {
                        String toast_info = getString(R.string.void_item);
                        Toast.makeText(getActivity(), toast_info,
                                Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if(item==null)

                    {
                        // call the interface method
                        ((EditShopList) getActivity()).addItem(new_name, new_price, new_qty);
                    }

                    else if(!(new_name.equals(item.name)&&new_price==item.price&&new_qty==item.qty))

                    {
                        ShopListEntry tmp_item = new ShopListEntry(new_name, new_price, new_qty);
                        ((EditShopList) getActivity()).editItem(item, tmp_item);
                    }

                    DialogEditItemShopListFragment.this.getDialog().dismiss();
                }
    })
                .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        DialogEditItemShopListFragment.this.getDialog().cancel();
                    }
                });

        return builder.create();
    }

}