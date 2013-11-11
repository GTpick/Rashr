package de.mkrtchyan.recoverytools;

/*
 * Copyright (c) 2013 Ashot Mkrtchyan
 * Permission is hereby granted, free of charge, to any person obtaining a copy 
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights 
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell 
 * copies of the Software, and to permit persons to whom the Software is 
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in 
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR 
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, 
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER 
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.ActionBarDrawerToggle;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.PopupMenu;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;

import de.mkrtchyan.utils.Common;
import de.mkrtchyan.utils.Downloader;
import de.mkrtchyan.utils.FileChooser;
import de.mkrtchyan.utils.Notifyer;

public class RecoveryTools extends ActionBarActivity {

    private static final String TAG = "Recovery-Tools";
    //  Declaring setting names
    public static final String PREF_NAME = "recovery_tools";
    private static final String PREF_KEY_ADS = "show_ads";
    private static final String PREF_KEY_CUR_VER = "current_version";
    private static final String PREF_KEY_CUSTOM_DEVICE = "custom_device_name";
    private static final String PREF_KEY_USE_CUSTOM = "use_custom_device_name";
    private static final String PREF_KEY_FIRST_RUN = "first_run";
    public static final String PREF_KEY_HISTORY = "last_history_";

    private final Context mContext = this;
    //  Used paths and files
    private static final File PathToSd = Environment.getExternalStorageDirectory();
    private static final File PathToRecoveryTools = new File(PathToSd, "Recovery-Tools");
    private static final File PathToRecoveries = new File(PathToRecoveryTools, "recoveries");
    public static final File PathToCWM = new File(PathToRecoveries, "clockworkmod");
    public static final File PathToTWRP = new File(PathToRecoveries, "twrp");
    public static final File PathToBackups = new File(PathToRecoveryTools, "backups");
    public static final File PathToUtils = new File(PathToRecoveryTools, "utils");
    private static final File Sums = new File(PathToRecoveries, "IMG_SUMS");
    private File fRECOVERY;

    //	Declaring needed objects
    private final Notifyer mNotifyer = new Notifyer(mContext);
    private DeviceHandler mDeviceHandler = new DeviceHandler();
    private DrawerLayout mDrawerLayout = null;
    private FileChooser fcFlashOther = null;
    private FileChooser fcAllIMGS = null;
    private boolean keepAppOpen = true;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        System.gc();

        if (getIntent().getData() != null) {
            handleBrowserChoice();
        } else {
            if (BuildConfig.DEBUG) {
                showFakeDialog();
            }
//      	If device is not supported, you can report it now or close the App
            if (!mDeviceHandler.isOtherSupported()) {
                showDeviceNotSupportedDialog();
            } else {
                if (!Common.getBooleanPref(mContext, PREF_NAME, PREF_KEY_FIRST_RUN)) {
                    showFlashWarning();
                    Common.setBooleanPref(mContext, PREF_NAME, PREF_KEY_ADS, true);
                } else {
                    Notifyer.showAppRateDialog(mContext);
                }
//			    Create needed Folder
                checkFolder();
            }

            try {
                optimizeLayout();
            } catch (NullPointerException e) {
                Notifyer.showExceptionToast(mContext, TAG, e);
            }
            mDeviceHandler.extractFiles(mContext);
            mDeviceHandler.downloadUtils(mContext);
            showChangelog();
            if (Sums.exists()) {
                Sums.delete();
            }
            try {
                Common.pushFileFromRAW(mContext, Sums, R.raw.img_sums);
            } catch (IOException e) {
                Notifyer.showExceptionToast(mContext, TAG, e);
            }
        }
    }
//	View Methods (onClick)
    public void Go(View view) {
        boolean updateable = true;
        fRECOVERY = null;
        if (!mDeviceHandler.downloadUtils(mContext)) {
    //			Get device specified recovery file for example recovery-clockwork-touch-6.0.3.1-grouper.img
            String SYSTEM = view.getTag().toString();
            String CWM_SYSTEM = "clockwork";
            String TWRP_SYSTEM = "twrp";
            if (SYSTEM.equals(CWM_SYSTEM)) {
                fRECOVERY = mDeviceHandler.getCWM_IMG();
            } else if (SYSTEM.equals(TWRP_SYSTEM)) {
                fRECOVERY = mDeviceHandler.getTWRP_IMG();
            } else {
                return;
            }
            if (fRECOVERY.getParentFile().listFiles() != null) {
                File[] recoveryList = fRECOVERY.getParentFile().listFiles();
                if (recoveryList != null) {
                    for (File i : recoveryList) {
                        if (i.compareTo(fRECOVERY) == 0) {
                            updateable = false;
                        }
                    }
                    if (recoveryList.length == 1 && !updateable) {
                        rFlasher.run();
                    } else if (updateable && recoveryList.length > 0
                            || recoveryList.length > 1) {
                        fcAllIMGS = new FileChooser(mContext, fRECOVERY.getParentFile(), new Runnable() {
                            @Override
                            public void run() {
                                fRECOVERY = fcAllIMGS.getSelectedFile();
                                rFlasher.run();
                            }
                        });
                        fcAllIMGS.setEXT(mDeviceHandler.getEXT());
                        fcAllIMGS.setWarn(false);
                        if (updateable) {
                            LinearLayout chooserLayout = fcAllIMGS.getLayout();
                            Button Update = new Button(mContext);
                            Update.setText(R.string.update);
                            Update.setOnClickListener(new View.OnClickListener() {

                                @Override
                                public void onClick(View v) {
                                    fcAllIMGS.dismiss();
                                    rFlasher.run();
                                }
                            });
                            chooserLayout.addView(Update);
                        }
                        fcAllIMGS.setTitle(SYSTEM.toUpperCase());
                        fcAllIMGS.show();
                    } else if (!fRECOVERY.exists()) {
                        Downloader RecoveryDownloader = new Downloader(mContext, DeviceHandler.HOST_URL, fRECOVERY.getName(), fRECOVERY, rFlasher);
                        RecoveryDownloader.setRetry(true);
                        RecoveryDownloader.setAskBeforeDownload(true);
                        RecoveryDownloader.setChecksumFile(Sums);
                        RecoveryDownloader.ask();
                    } else {
                        rFlasher.run();
                    }
                }
            }
        }
    }

    public void bDonate(View view) {
        startActivity(new Intent(mContext, DonationsActivity.class));
    }

    public void bXDA(View view) {
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("http://forum.xda-developers.com/showthread.php?t=2334554")));
    }

    public void bExit(View view) {
        finish();
        System.exit(0);
    }

    public void cbLog(View view) {
        CheckBox cbLog = (CheckBox) view;
        Common.setBooleanPref(mContext, Common.PREF_NAME, Common.PREF_LOG, !Common.getBooleanPref(mContext, Common.PREF_NAME, Common.PREF_LOG));
        cbLog.setChecked(Common.getBooleanPref(mContext, Common.PREF_NAME, Common.PREF_LOG));
        if (cbLog.isChecked()) {
            findViewById(R.id.bShowLogs).setVisibility(View.VISIBLE);
        } else {
            findViewById(R.id.bShowLogs).setVisibility(View.INVISIBLE);
        }
    }

    public void bReport(View view) {
        report(true);
    }

    public void bShowLogs(View view) {
        Common.showLogs(mContext);
    }

    public void cbShowAds(View view) {
        Common.setBooleanPref(mContext, PREF_NAME, PREF_KEY_ADS, !Common.getBooleanPref(mContext, PREF_NAME, PREF_KEY_ADS));
        ((CheckBox) view).setChecked(Common.getBooleanPref(mContext, PREF_NAME, PREF_KEY_ADS));
        Toast.makeText(mContext, R.string.please_restart, Toast.LENGTH_SHORT).show();
    }

    public void bFlashOther(View view) {
        fRECOVERY = null;
        try {
            fcFlashOther = new FileChooser(mContext, PathToSd, new Runnable() {
                @Override
                public void run() {
                    fRECOVERY = fcFlashOther.getSelectedFile();
                    rFlasher.run();
                }
            });
            fcFlashOther.setTitle(R.string.search_recovery);
            fcFlashOther.setEXT(mDeviceHandler.getEXT());
            fcFlashOther.show();
        } catch (NullPointerException e) {
            Notifyer.showExceptionToast(mContext, TAG, e);
        }
    }

    public void bShowHistory(View view) {
        File HistoryFiles[] = {null, null, null, null, null};
        String HistoryFileNames[] = {"", "", "", "", ""};
        final Dialog HistoryDialog = new Dialog(mContext);
        HistoryDialog.setTitle(R.string.sHistory);
        ListView list = new ListView(mContext);
        for (int i = 0; i < 5; i++) {
            HistoryFiles[i] = new File(Common.getStringPref(mContext, PREF_NAME, PREF_KEY_HISTORY + String.valueOf(i)));
            if (!HistoryFiles[i].exists())
                Common.setStringPref(mContext, PREF_NAME, PREF_KEY_HISTORY + String.valueOf(i), "");
            else {
                HistoryFileNames[i] = HistoryFiles[i].getName();
            }
        }
        final File TMPList[] = HistoryFiles;
        list.setAdapter(new ArrayAdapter<String>(mContext, android.R.layout.simple_list_item_1, HistoryFileNames));
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> arg0, View arg1, int arg2,
                                    long arg3) {
                if (TMPList[arg2].exists()) {
                    fRECOVERY = TMPList[arg2];
                    rFlasher.run();
                    HistoryDialog.dismiss();
                } else {
                    Toast.makeText(mContext, R.string.no_choosed, Toast.LENGTH_SHORT).show();
                }
            }
        });
        HistoryDialog.setContentView(list);
        HistoryDialog.show();
    }

    public void bBackupMgr(View view) {
        showPopup(R.menu.bakmgr_menu, view);
    }

    public void bClearCache(View view) {
        Common.deleteFolder(PathToCWM, false);
        Common.deleteFolder(PathToTWRP, false);
    }

    public void bRebooter(View view) {
        showPopup(R.menu.rebooter_menu, view);
    }

    public void report(final boolean isCancelable) {
        final Dialog reportDialog = mNotifyer.createDialog(R.string.commentar, R.layout.dialog_comment, false, true);
        new Thread(new Runnable() {
            @Override
            public void run() {
//		Creates a report Email including a Comment and important device infos
                if (!isCancelable)
                    reportDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                        @Override
                        public void onCancel(DialogInterface dialogInterface) {
                            System.exit(0);
                            finish();
                        }
                    });
                final Button bGo = (Button) reportDialog.findViewById(R.id.bGo);
                bGo.setOnClickListener(new View.OnClickListener() {

                    @Override
                    public void onClick(View v) {

                        if (!Common.getBooleanPref(mContext, PREF_NAME, PREF_KEY_ADS))
                            Toast.makeText(mContext, R.string.please_ads, Toast.LENGTH_SHORT).show();
                        Toast.makeText(mContext, R.string.donate_to_support, Toast.LENGTH_SHORT).show();
                        try {
                            ArrayList<File> files = new ArrayList<File>();
                            File TestResults = new File(mContext.getFilesDir(), "results.txt");
                            try {
                                if (TestResults.exists())
                                    TestResults.delete();
                                FileOutputStream fos = openFileOutput(TestResults.getName(), Context.MODE_PRIVATE);
                                fos.write(("\nRecovery-Tools:\n\n" + Common.executeSuShell("ls -lR " + PathToRecoveryTools.getAbsolutePath()) +
                                        "\nMTD result:\n" + Common.executeSuShell("cat /proc/mtd") + "\n" +
                                        "\nDevice Tree:\n" + "\n" + Common.executeSuShell("ls -lR /dev/block")).getBytes());
                                files.add(TestResults);
                            } catch (FileNotFoundException e) {
                                e.printStackTrace();
                            }
                            if (getPackageManager() != null) {
                                PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
                                EditText text = (EditText) reportDialog.findViewById(R.id.etCommentar);
                                String comment = "";
                                if (text.getText() != null)
                                    comment = text.getText().toString();
                                Intent intent = new Intent(Intent.ACTION_SEND_MULTIPLE);
                                intent.setType("text/plain");
                                intent.putExtra(Intent.EXTRA_EMAIL, new String[]{"ashotmkrtchyan1995@gmail.com"});
                                intent.putExtra(Intent.EXTRA_SUBJECT, "Recovery-Tools report");
                                intent.putExtra(Intent.EXTRA_TEXT, "Package Infos:" +
                                        "\n\nName: " + pInfo.packageName +
                                        "\nVersionName: " + pInfo.versionName +
                                        "\nVersionCode: " + pInfo.versionCode +
                                        "\n\n\nProduct Info: " +
                                        "\n\nManufacture: " + android.os.Build.MANUFACTURER +
                                        "\nDevice: " + Build.DEVICE + " (" + mDeviceHandler.DEV_NAME + ")" +
                                        "\nBoard: " + Build.BOARD +
                                        "\nBrand: " + Build.BRAND +
                                        "\nModel: " + Build.MODEL +
                                        "\nFingerprint: " + Build.FINGERPRINT +
                                        "\nAndroid SDK Level: " + Build.VERSION.CODENAME + " (" + Build.VERSION.SDK_INT + ")" +
                                        "\nRecovery Path: " + mDeviceHandler.getRecoveryPath() +
                                        "\nIs MTD: " + mDeviceHandler.isMTD() +
                                        "\nIs DD: " + mDeviceHandler.isDD() +
                                        "\n\n\n===========Comment==========\n" + comment +
                                        "\n===========Comment==========\n");
                                File CommandLogs = new File(mContext.getFilesDir(), Common.Logs);
                                if (CommandLogs.exists())
                                    files.add(CommandLogs);
                                files.add(new File(getFilesDir(), "last_log.txt"));
                                ArrayList<Uri> uris = new ArrayList<Uri>();
                                for (File file : files) {
                                    Common.executeSuShell("cp " + file.getAbsolutePath() + " " + new File(mContext.getFilesDir(), file.getName()).getAbsolutePath());
                                    file = new File(mContext.getFilesDir(), file.getName());
                                    Common.chmod(file, "644");
                                    uris.add(Uri.fromFile(file));
                                }
                                intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
                                startActivity(Intent.createChooser(intent, "Send over Gmail"));
                                reportDialog.dismiss();
                            }
                        } catch (Exception e) {
                            Notifyer.showExceptionToast(mContext, TAG, e);
                        }
                    }
                });
            }
        }).start();
        reportDialog.show();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home)
            if (mDrawerLayout.isDrawerOpen(Gravity.LEFT))
                mDrawerLayout.closeDrawer(Gravity.LEFT);
            else
                mDrawerLayout.openDrawer(Gravity.LEFT);
        return super.onOptionsItemSelected(item);
    }

    public void showPopup(int Menu, View v) {
        PopupMenu popup = new PopupMenu(mContext, v);
        popup.getMenuInflater().inflate(Menu, popup.getMenu());
        popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem menuItem) {
                try {
                    switch (menuItem.getItemId()) {
                        case R.id.iReboot:
                            Common.executeSuShell(mContext, "reboot");
                            return true;
                        case R.id.iRebootRecovery:
                            Common.executeSuShell(mContext, "reboot recovery");
                            return true;
                        case R.id.iRebootBootloader:
                            Common.executeSuShell(mContext, "reboot bootloader");
                            return true;
                        case R.id.iCreateBackup:
                            new BackupHandler(mContext).backup();
                            return true;
                        case R.id.iRestoreBackup:
                            new BackupHandler(mContext).restore();
                            return true;
                        case R.id.iDeleteBackup:
                            new BackupHandler(mContext).deleteBackup();
                            return true;
                        default:
                            return false;
                    }
                } catch (Common.ShellException e) {
                    Notifyer.showExceptionToast(mContext, TAG, e);
                    return false;
                }
            }
        });
        popup.show();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            if (!mDrawerLayout.isDrawerOpen(Gravity.LEFT)) {
                mDrawerLayout.openDrawer(Gravity.LEFT);
            } else {
                mDrawerLayout.closeDrawer(Gravity.LEFT);
            }
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void showFakeDialog() {
//      Fake other devices to check the functionality of the App
        final Dialog FakerDialog = new Dialog(mContext);
        FakerDialog.setTitle("Set your preferred device name");
        final LinearLayout FakerLayout = new LinearLayout(mContext);
        FakerLayout.setOrientation(LinearLayout.VERTICAL);
        final EditText etFakeDevName = new EditText(mContext);
        if (!Common.getStringPref(mContext, PREF_NAME, PREF_KEY_CUSTOM_DEVICE).equals("")) {
            etFakeDevName.setHint(Common.getStringPref(mContext, PREF_NAME, PREF_KEY_CUSTOM_DEVICE));
            mDeviceHandler = new DeviceHandler(Common.getStringPref(mContext, PREF_NAME, PREF_KEY_CUSTOM_DEVICE));
        } else {
            etFakeDevName.setHint(Build.DEVICE);
        }
        final Button reset = new Button(mContext);
        reset.setText(R.string.go);
        reset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String DEV_NAME = "";
                if (etFakeDevName.getText() != null)
                    DEV_NAME = etFakeDevName.getText().toString();
                Common.setStringPref(mContext, PREF_NAME, PREF_KEY_CUSTOM_DEVICE, DEV_NAME);
                FakerDialog.dismiss();
                Toast.makeText(mContext, R.string.please_restart, Toast.LENGTH_SHORT).show();
            }
        });
        final Button setDefault = new Button(mContext);
        setDefault.setText("Reset to Default");
        setDefault.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Common.setStringPref(mContext, PREF_NAME, PREF_KEY_CUSTOM_DEVICE, Build.DEVICE);
                Common.setBooleanPref(mContext, PREF_NAME, PREF_KEY_USE_CUSTOM, false);
                etFakeDevName.setHint(Build.DEVICE);
                FakerDialog.dismiss();
                Toast.makeText(mContext, R.string.please_restart, Toast.LENGTH_SHORT).show();
            }
        });
        FakerLayout.addView(etFakeDevName);
        FakerLayout.addView(reset);
        FakerLayout.addView(setDefault);
        FakerDialog.setContentView(FakerLayout);
        FakerDialog.show();
    }

    public void optimizeLayout() throws NullPointerException {
        setContentView(R.layout.recovery_tools);
        setupDrawerList();
        final LinearLayout mDrawerLinear = (LinearLayout) findViewById(R.id.left_drawer);
        CheckBox cbShowAds = (CheckBox) mDrawerLinear.findViewById(R.id.cbShowAds);
        CheckBox cbLog = (CheckBox) mDrawerLinear.findViewById(R.id.cbLog);
        cbShowAds.setChecked(Common.getBooleanPref(mContext, PREF_NAME, PREF_KEY_ADS));
        cbLog.setChecked(Common.getBooleanPref(mContext, Common.PREF_NAME, Common.PREF_LOG));
        if (cbLog.isChecked()) {
            findViewById(R.id.bShowLogs).setVisibility(View.VISIBLE);
        } else {
            findViewById(R.id.bShowLogs).setVisibility(View.INVISIBLE);
        }
        cbShowAds.setChecked(Common.getBooleanPref(mContext, PREF_NAME, PREF_KEY_ADS));
        ViewGroup layout = (ViewGroup) findViewById(R.id.MainLayout);
        try {
            if (!Common.getBooleanPref(mContext, PREF_NAME, PREF_KEY_ADS)) {
                layout.removeView(findViewById(R.id.adView));
            }
            if (!mDeviceHandler.isCwmSupported()) {
                layout.removeView(findViewById(R.id.bCWM));
            }
            if (!mDeviceHandler.isTwrpSupported()) {
                layout.removeView(findViewById(R.id.bTWRP));
            }
            if (mDeviceHandler.isOverRecovery()) {
                layout.removeView(findViewById(R.id.bBAK_MGR));
                layout.removeView(findViewById(R.id.bHistory));
            }
            if (!mDeviceHandler.isOtherSupported())
                layout.removeAllViews();
            readLastLog();
        } catch (NullPointerException e) {
            throw new NullPointerException("Error while setting up Layout");
        }
    }

    private void checkFolder() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Common.checkFolder(PathToRecoveryTools);
                Common.checkFolder(PathToRecoveries);
                Common.checkFolder(PathToCWM);
                Common.checkFolder(PathToTWRP);
                Common.checkFolder(PathToBackups);
                Common.checkFolder(PathToUtils);
                Common.checkFolder(new File(PathToUtils, mDeviceHandler.DEV_NAME));
            }
        }).start();
    }

    public void showOverRecoveryInstructions() {
        final AlertDialog.Builder Instructions = new AlertDialog.Builder(mContext);
        Instructions
                .setTitle(R.string.info)
                .setMessage(R.string.flash_over_recovery)
                .setPositiveButton(R.string.positive, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        bRebooter(findViewById(R.id.bRebooter));
                    }
                })
                .setNeutralButton(R.string.instructions, new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Dialog d = new Dialog(mContext);
                        d.setTitle(R.string.instructions);
                        TextView tv = new TextView(mContext);
                        tv.setTextSize(20);
                        tv.setText(R.string.instruction);
                        d.setContentView(tv);
                        d.setOnCancelListener(new DialogInterface.OnCancelListener() {
                            @Override
                            public void onCancel(DialogInterface dialog) {
                                Instructions.show();
                            }
                        });
                        d.show();
                    }
                })
                .show();
    }

    public void showChangelog() {
        try {
            if (getPackageManager() != null) {
                PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
                final int previous_version = Common.getIntegerPref(mContext, PREF_NAME, PREF_KEY_CUR_VER);
                final int current_version = pInfo.versionCode;
                if (current_version > previous_version) {
                    mNotifyer.createDialog(R.string.version, R.string.changes, true, true).show();
                    Common.setIntegerPref(mContext, PREF_NAME, PREF_KEY_CUR_VER, current_version);
                }
            }
        } catch (PackageManager.NameNotFoundException e) {
            Notifyer.showExceptionToast(mContext, TAG, e);
        } catch (NullPointerException e) {
            Notifyer.showExceptionToast(mContext, TAG, e);
        }
    }

    public void showFlashAlertDialog() {
        new AlertDialog.Builder(mContext)
                .setTitle(R.string.warning)
                .setMessage(String.format(mContext.getString(R.string.choose_message), fRECOVERY.getName()))
                .setPositiveButton(R.string.positive, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        rFlasher.run();
                    }
                })
                .setNegativeButton(R.string.negative, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        finish();
                        System.exit(0);
                    }
                })
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialogInterface) {

                    }
                })
                .show();
    }

    private void showDeviceNotSupportedDialog() {
        AlertDialog.Builder DeviceNotSupported = mNotifyer.createAlertDialog(R.string.warning, R.string.notsupportded,
                new Runnable() {
                    @Override
                    public void run() {
                        report(false);
                    }
                }, null,
                new Runnable() {
                    @Override
                    public void run() {
                        finish();
                        System.exit(0);
                    }
                }
        );
        DeviceNotSupported.setCancelable(BuildConfig.DEBUG);
        DeviceNotSupported.show();
    }

    public void setupDrawerList() {
        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        mDrawerLayout.setDrawerShadow(R.drawable.drawer_shadow, GravityCompat.START);
        ActionBarDrawerToggle mDrawerToggle = new ActionBarDrawerToggle(this, mDrawerLayout, R.drawable.ic_drawer, R.string.settings, R.string.app_name);
        mDrawerLayout.setDrawerListener(mDrawerToggle);
        mDrawerToggle.syncState();
        mDrawerToggle.setDrawerIndicatorEnabled(true);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);
    }

    public void showFlashWarning() {
        if (mDeviceHandler.getDevType() != DeviceHandler.DEV_TYPE_RECOVERY && Common.suRecognition()) {
            final AlertDialog.Builder WarningDialog = new AlertDialog.Builder(mContext);
            WarningDialog.setTitle(R.string.warning);
            WarningDialog.setMessage(String.format(getString(R.string.bak_warning), PathToBackups.getAbsolutePath()));
            WarningDialog.setPositiveButton(R.string.sBackup, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    new BackupHandler(mContext).backup();
                    Common.setBooleanPref(mContext, PREF_NAME, PREF_KEY_FIRST_RUN, true);
                }
            });
            WarningDialog.setNegativeButton(R.string.risk, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    Common.setBooleanPref(mContext, PREF_NAME, PREF_KEY_FIRST_RUN, true);
                }
            });
            WarningDialog.setCancelable(false);
            WarningDialog.show();
        }
    }

    public void handleBrowserChoice() {
//      Handle with Files chosen by FileBrowsers
        if (getIntent().getData() != null) {
            keepAppOpen = false;
            fRECOVERY = new File(getIntent().getData().getPath());
            getIntent().setData(null);
            showFlashAlertDialog();
        }
    }

    public void readLastLog() {
        try {
            File LastLog = new File("/cache/recovery/last_log");
            File LogCopy = new File(mContext.getFilesDir(), LastLog.getName() + ".txt");
            if (LogCopy.exists())
                LogCopy.delete();
            Common.executeSuShell("cp " + LastLog.getAbsolutePath() + " " + LogCopy.getAbsolutePath() );
            if (LogCopy.exists()) {
                final TextView RecoveryInfo = (TextView) findViewById(R.id.tvRecoveryVersion);
                Common.chmod(LogCopy, "644");
                String line;
                boolean version_found = false;
                BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(LogCopy)));
                while ((line = br.readLine()) != null && !version_found) {
                    if (line.contains("ClockworkMod Recovery")) {
                        version_found = true;
                        RecoveryInfo.setText(line);
                    }
                    if (line.contains("Starting TWRP")) {
                        version_found = true;
                        line = line.replace("Starting ", "");
                        line = line.substring(0, 12);
                        RecoveryInfo.setText(line);
                    }
                }
                br.close();
            }
        } catch (Exception e) {
            Notifyer.showExceptionToast(mContext, "TAG", e);
        }
    }

//	"Methods" need a input from user (AlertDialog) or at the end of AsyncTasks
    private final Runnable rFlash = new Runnable() {
        @Override
        public void run() {
            FlashUtil flashUtil = new FlashUtil(mContext, fRECOVERY, FlashUtil.JOB_FLASH);
            flashUtil.setKeepAppOpen(keepAppOpen);
            flashUtil.execute();
        }
    };
    private final Runnable rFlasher = new Runnable() {
        @Override
        public void run() {
            if (!Common.suRecognition()) {
                mNotifyer.showRootDeniedDialog();
            } else {
                if (fRECOVERY != null) {
                    if (fRECOVERY.exists()) {
                        if (fRECOVERY.getName().endsWith(mDeviceHandler.getEXT())) {
//				            If the flashing don't be handle specially flash it
                            if (!mDeviceHandler.isKernelFlashed() && !mDeviceHandler.isOverRecovery()) {
                                rFlash.run();
                            } else {
//		        	            Get user input if Kernel will be modified
                                if (mDeviceHandler.isKernelFlashed())
                                    mNotifyer.createAlertDialog(R.string.warning, R.string.kernel_to, rFlash).show();
//					            Get user input if user want to install over recovery now
                                if (mDeviceHandler.isOverRecovery()) {
                                    showOverRecoveryInstructions();
                                }
                            }
                        }
                    }
                }
            }
            fcFlashOther = null;
            fcAllIMGS = null;
        }
    };
}