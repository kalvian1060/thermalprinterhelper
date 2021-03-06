package com.atalay.bluetoothhelper.View.PrinterList;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import com.atalay.bluetoothhelper.Base.BasePresenter;
import com.atalay.bluetoothhelper.Adapter.BluetoothDeviceAdapter;
import com.atalay.bluetoothhelper.Common.UtilsDialog;
import com.atalay.bluetoothhelper.Common.UtilsGeneral;
import com.atalay.bluetoothhelper.Common.UtilsPermission;
import com.atalay.bluetoothhelper.Model.PermissionCallback;
import com.atalay.bluetoothhelper.R;
import com.karumi.dexter.MultiplePermissionsReport;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by baris on 10.03.2017.
 */

public class PrinterListPresenter extends BasePresenter<PrinterListView> implements PermissionCallback {
    private String TAG = this.getClass().getSimpleName();
    private Activity mActivity;
    private BluetoothAdapter mBluetoothAdapter;
    private List<BluetoothDevice> mDeviceList ;
    private SharedPreferences preferences;

    public PrinterListPresenter(Activity mActivity){
        this.mActivity = mActivity;

        preferences = PreferenceManager.getDefaultSharedPreferences(mActivity.getApplicationContext());
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    public void refreshBluetooth() {
        if(UtilsPermission.checkPermissionManuel(mActivity, Manifest.permission.ACCESS_FINE_LOCATION, 1) == -1){
            UtilsPermission.checkPermission(mActivity,this,R.string.err_permission,Manifest.permission.ACCESS_FINE_LOCATION);
            return;
        }

        if(!UtilsGeneral.bluetoothIsEnabled()){
            getView().showToast(mActivity.getText(R.string.err_bluetooth_notenabled).toString());
            return;
        }

        if(!mBluetoothAdapter.isDiscovering())
            mBluetoothAdapter.startDiscovery();

    }

    @Override
    public void successful(MultiplePermissionsReport report) {
        refreshBluetooth();
    }

    @Override
    public void denied(MultiplePermissionsReport report) {}

    public void onResume() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);

        mActivity.registerReceiver(mReceiver, filter);
    }

    public void onDestroy() {
        if(mBluetoothAdapter != null)
            if(mBluetoothAdapter.isDiscovering())
                mBluetoothAdapter.cancelDiscovery();

        mActivity.unregisterReceiver(mReceiver);
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                /*
                * Her yeni cihaz bulduğunda bu listeye girecek.
                * */
                BluetoothDevice device = (BluetoothDevice) intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

//                if(getActivePrinter().isEmpty())
//                    unpairDevice(device);

                mDeviceList.add(device);
                getView().showToast(mActivity.getString(R.string.findedbluetooth));
                Log.i(TAG, device.getName() + "\n" + device.getAddress());
            }else if(BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)){
                /*
                * Bluetooth Statu değiştiğinde buraya girecek
                * */

                int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);

                if (state == BluetoothAdapter.STATE_ON) {
                    getView().showToast(mActivity.getString(R.string.enabled));

                    getView().enabledRefreshButton();
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(action)) {
                /*
                * Bluetooth cihaz aramaya başladığında buraya girecek.
                * */
                mDeviceList = new ArrayList<BluetoothDevice>();

                getView().bluetoothSearchStarted();

                getView().showToast(mActivity.getString(R.string.searchbluetooth));
            }else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                /*
                * Bluetooth cihaz araması tamamlandığında buraya girecek.
                * */

                getView().loadDevices(new BluetoothDeviceAdapter(mActivity, mDeviceList));
                getView().bluetoothSearchEnded();
            }else if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)) {
                /*
                * Bluetooth cihaz eşleşmesi tamamlandığında buraya girecek.
                * */

                int state 		= intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR);
                int prevState	= intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.ERROR);

                if (state == BluetoothDevice.BOND_BONDED && prevState == BluetoothDevice.BOND_BONDING) {
                    getView().showToast(mActivity.getString(R.string.paired));

                    BluetoothDevice device = (BluetoothDevice) intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    saveDeviceInfo(device);
                } else if (state == BluetoothDevice.BOND_NONE && prevState == BluetoothDevice.BOND_BONDED){
                    saveDeviceInfo(null);
                }


            }
        }
    };

    private void saveDeviceInfo(BluetoothDevice device){
        SharedPreferences.Editor prefEditor = preferences.edit();
        prefEditor.putString(mActivity.getString(R.string.pref_general_printer), device==null?"":device.getName());
        prefEditor.putString(mActivity.getString(R.string.pref_general_printer_address), device==null?"":device.getAddress());
        prefEditor.commit();
        getView().refreshAdapter();
    }

    public void unpairDialog(final BluetoothDevice device) {
        UtilsDialog.createAlertDialog(mActivity)
                .setTitle(mActivity.getString(R.string.printerlist_title))
                .setMessage(mActivity.getString(R.string.unsyncbluetooth))
                .setPositiveButton(mActivity.getString(R.string.yes), new DialogInterface.OnClickListener(){
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        unpairDevice(device);
                        dialog.dismiss();
                    }
                })
                .setNegativeButton(mActivity.getString(R.string.cancel), null)
                .show();
    }

    private void unpairDevice(final BluetoothDevice device){
        try {
            Method method = device.getClass().getMethod("removeBond", (Class[]) null);
            method.invoke(device, (Object[]) null);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void printTest() {}

    public void pairDevice(final BluetoothDevice device, final Activity mActivity) {
        UtilsDialog.createAlertDialog(mActivity)
                .setTitle(mActivity.getString(R.string.printerlist_title))
                .setMessage(mActivity.getString(R.string.syncbluetooth))
                .setPositiveButton(mActivity.getString(R.string.yes), new DialogInterface.OnClickListener(){
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        try {
                            Method method = device.getClass().getMethod("createBond", (Class[]) null);
                            method.invoke(device, (Object[]) null);

                            if(device.getBondState() == BluetoothDevice.BOND_BONDED) {
                                saveDeviceInfo(device);
                                mActivity.setResult(Activity.RESULT_OK);
                                mActivity.finish();
                            }

                        } catch (Exception e) {
                            e.printStackTrace();
                        }

                        dialog.dismiss();
                    }

                })
                .setNegativeButton(mActivity.getString(R.string.cancel), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        dialogInterface.dismiss();
                    }
                })
                .show();
    }

    public String getActivePrinter() {
        String prefKey = mActivity.getString(R.string.pref_general_printer_address);
        return preferences.getString(prefKey,"");
    }
}
