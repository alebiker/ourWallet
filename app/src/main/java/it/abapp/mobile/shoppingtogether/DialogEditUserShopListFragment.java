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

import java.util.Date;

/**
 * Created by Alessandro on 13/04/2015.
 */
public class DialogEditUserShopListFragment extends DialogFragment {


    public static DialogEditUserShopListFragment newInstance(User user) {
        DialogEditUserShopListFragment frag = new DialogEditUserShopListFragment();
        Bundle args = new Bundle();
        args.putSerializable("user", user);

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
        //TODO check if the getSerializable pass reference or copy
        final User user = (User)args.getSerializable("user");

        StringBuilder name = new StringBuilder();
        // Inflate and set the layout for the dialog
        // Pass null as the parent view because its going in the dialog layout
        final View dialog_root = inflater.inflate(R.layout.dialog_edit_user, null);

        if (user == null) {
            ((TextView) dialog_root.findViewById(R.id.dialog_title)).setText(getString(R.string.add_user));
            ((EditText) dialog_root.findViewById(R.id.dialog_user_name)).setText(null);
        }
        else {
            ((TextView) dialog_root.findViewById(R.id.dialog_title)).setText(getString(R.string.edit_user));
            name.append(user.name);
            // population by passed object
            ((EditText)dialog_root.findViewById(R.id.dialog_user_name)).setText(name);
        }

        builder.setView(dialog_root)
                // Add action buttons

                .setPositiveButton(R.string.confirm, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        // call the interface method
                        String selected_name = ((EditText) dialog_root.findViewById(R.id.dialog_user_name)).getText().toString();
                        if (selected_name.isEmpty()) {
                            String toast_info = getString(R.string.void_user);
                            Toast.makeText(getActivity(), toast_info,
                                    Toast.LENGTH_SHORT).show();
                        } else {
                            if (user == null) {
                                ((EditShopList) getActivity()).addUser(selected_name);
                            } else if (!selected_name.equals(user.name)) {
                                ((EditShopList) getActivity()).editUser(user, selected_name);
                            }
                            DialogEditUserShopListFragment.this.getDialog().dismiss();
                        }
                    }
                })
                .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        DialogEditUserShopListFragment.this.getDialog().cancel();
                    }
                });

        return builder.create();
    }

}