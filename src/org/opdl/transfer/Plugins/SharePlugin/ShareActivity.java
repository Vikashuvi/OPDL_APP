/*
 * SPDX-FileCopyrightText: 2014 Albert Vaca Cintora <albertvaka@gmail.com>
 *
 * SPDX-License-Identifier: GPL-2.0-only OR GPL-3.0-only OR LicenseRef-OPDL-Accepted-GPL
 */

package org.opdl.transfer.Plugins.SharePlugin;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.webkit.URLUtil;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.core.content.IntentCompat;
import androidx.core.os.BundleCompat;
import androidx.preference.PreferenceManager;

import org.opdl.transfer.BackgroundService;
import org.opdl.transfer.Device;
import org.opdl.transfer.Helpers.FilesHelper;
import org.opdl.transfer.Helpers.WindowHelper;
import org.opdl.transfer.NetworkPacket;
import org.opdl.transfer.OpdlTransfer;
import org.opdl.transfer.UserInterface.List.DeviceItem;
import org.opdl.transfer.UserInterface.List.ListAdapter;
import org.opdl.transfer.UserInterface.List.SectionItem;
import org.opdl.transfer.UserInterface.List.UnreachableDeviceItem;
import org.opdl.transfer.base.BaseActivity;
import org.opdl.transfer.R;
import org.opdl.transfer.databinding.ActivityShareBinding;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import kotlin.Lazy;
import kotlin.LazyKt;
import kotlin.Unit;

public class ShareActivity extends BaseActivity<ActivityShareBinding> {
    private static final String KEY_UNREACHABLE_URL_LIST = "key_unreachable_url_list";

    private SharedPreferences mSharedPrefs;
    private boolean isMultiSelectMode = false;
    private final ArrayList<Device> selectedDevices = new ArrayList<>();
    private final HashMap<String, DeviceItem> deviceItemMap = new HashMap<>();

    private final Lazy<ActivityShareBinding> lazyBinding = LazyKt.lazy(() -> ActivityShareBinding.inflate(getLayoutInflater()));

    @NonNull
    @Override
    public ActivityShareBinding getBinding() {
        return lazyBinding.getValue();
    }

    @Override
    public boolean isScrollable() {
        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.refresh, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        if (item.getItemId() == R.id.menu_refresh) {
            refreshDevicesAction();
            return true;
        } else if (item.getItemId() == R.id.menu_multi_select) {
            toggleMultiSelectMode();
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    private void refreshDevicesAction() {
        BackgroundService.ForceRefreshConnections(this);

        getBinding().devicesListLayout.refreshListLayout.setRefreshing(true);
        getBinding().devicesListLayout.refreshListLayout.postDelayed(() -> {
            getBinding().devicesListLayout.refreshListLayout.setRefreshing(false);
        }, 1500);
    }

    private void toggleMultiSelectMode() {
        if (isMultiSelectMode && !selectedDevices.isEmpty()) {
            // User clicked "Share to Selected" - execute the share
            shareToSelectedDevices();
            return;
        }

        // Toggle multi-select mode
        isMultiSelectMode = !isMultiSelectMode;
        selectedDevices.clear();
        deviceItemMap.clear();

        MenuItem menuItem = getBinding().toolbarLayout.toolbar.getMenu().findItem(R.id.menu_multi_select);
        if (menuItem != null) {
            menuItem.setTitle(R.string.select_multiple);
            menuItem.setIcon(R.drawable.ic_action_refresh_24dp);
        }

        updateDeviceList();
    }

    private void shareToSelectedDevices() {
        final Intent intent = getIntent();
        Bundle extras = intent.getExtras();
        ArrayList<Uri> streams = streamsFromIntent(intent, extras);

        // Pre-read all URIs into NetworkPackets before activity finishes
        // This ensures we have permission to read the data
        ArrayList<NetworkPacket> preReadPackets = new ArrayList<>();
        if (streams != null && !streams.isEmpty()) {
            for (Uri uri : streams) {
                NetworkPacket np = FilesHelper.uriToNetworkPacket(this, uri, "opdltransfer.share.request");
                if (np != null) {
                    preReadPackets.add(np);
                }
            }
        }

        // Share pre-read packets to each device
        for (Device device : selectedDevices) {
            SharePlugin plugin = OpdlTransfer.getInstance().getDevicePlugin(device.getDeviceId(), SharePlugin.class);
            if (plugin != null) {
                if (!preReadPackets.isEmpty()) {
                    plugin.sendNetworkPackets(preReadPackets);
                } else if (extras != null) {
                    // Handle text/URL sharing
                    String text = extras.getString(Intent.EXTRA_TEXT);
                    if (text != null && !text.isEmpty()) {
                        plugin.share(intent);
                    }
                }
            }
        }
        finish();
    }

    private ArrayList<Uri> streamsFromIntent(Intent intent, Bundle extras) {
        if (extras == null || !extras.containsKey(Intent.EXTRA_STREAM)) {
            return null;
        }
        ArrayList<Uri> uriList;
        if (Intent.ACTION_SEND_MULTIPLE.equals(intent.getAction())) {
            uriList = IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri.class);
        } else {
            uriList = new ArrayList<>();
            uriList.add(BundleCompat.getParcelable(extras, Intent.EXTRA_STREAM, Uri.class));
        }
        uriList.removeAll(Collections.singleton(null));
        if (uriList.isEmpty()) {
            return null;
        }
        return uriList;
    }

    private void updateDeviceList() {
        final Intent intent = getIntent();

        String action = intent.getAction();
        if (!Intent.ACTION_SEND.equals(action) && !Intent.ACTION_SEND_MULTIPLE.equals(action)) {
            finish();
            return;
        }

        Collection<Device> devices = OpdlTransfer.getInstance().getDevices().values();
        final ArrayList<Device> devicesList = new ArrayList<>();
        final ArrayList<ListAdapter.Item> items = new ArrayList<>();

        boolean intentHasUrl = doesIntentContainUrl(intent);

        String sectionString = getString(R.string.share_to);
        if (intentHasUrl) {
            sectionString = getString(R.string.unreachable_device_url_share_text) + getString(R.string.share_to);
        }
        SectionItem section = new SectionItem(sectionString);
        items.add(section);

        for (Device d : devices) {
            // Show the paired devices only if they are unreachable and the shared intent has a URL
            if (d.isPaired() && (intentHasUrl || d.isReachable())) {
                devicesList.add(d);
                if (!d.isReachable()) {
                    UnreachableDeviceItem item = new UnreachableDeviceItem(d, device -> deviceClicked(device, intentHasUrl, intent));
                    items.add(item);
                } else {
                    DeviceItem item = new DeviceItem(d, device -> deviceClicked(device, intentHasUrl, intent),
                            (device, selected) -> onDeviceSelectionChanged(device, selected), isMultiSelectMode);
                    items.add(item);
                    deviceItemMap.put(d.getDeviceId(), item);
                }
                section.isEmpty = false;
            }
        }

        getBinding().devicesListLayout.devicesList.setAdapter(new ListAdapter(ShareActivity.this, items));

        // Configure focus order for Accessibility, for touchpads, and for TV remotes
        // (allow focus of items in the device list)
        getBinding().devicesListLayout.devicesList.setItemsCanFocus(true);
    }

    private Unit deviceClicked(Device device, boolean intentHasUrl, Intent intent) {
        if (isMultiSelectMode) {
            return Unit.INSTANCE;
        }

        SharePlugin plugin = OpdlTransfer.getInstance().getDevicePlugin(device.getDeviceId(), SharePlugin.class);
        if (intentHasUrl && !device.isReachable()) {
            // Store the URL to be delivered once device becomes online
            storeUrlForFutureDelivery(device, intent.getStringExtra(Intent.EXTRA_TEXT));
        } else if (plugin != null) {
            plugin.share(intent);
        }
        finish();
        return Unit.INSTANCE;
    }

    private Unit onDeviceSelectionChanged(Device device, boolean selected) {
        if (selected) {
            if (!selectedDevices.contains(device)) {
                selectedDevices.add(device);
            }
        } else {
            selectedDevices.remove(device);
        }

        MenuItem menuItem = getBinding().toolbarLayout.toolbar.getMenu().findItem(R.id.menu_multi_select);
        if (menuItem != null) {
            if (selectedDevices.isEmpty()) {
                menuItem.setTitle(R.string.select_multiple);
                menuItem.setIcon(R.drawable.ic_action_refresh_24dp);
                menuItem.setEnabled(true);
            } else {
                menuItem.setTitle(getString(R.string.share_to_selected) + " (" + selectedDevices.size() + ")");
                menuItem.setIcon(R.drawable.ic_action_refresh_24dp);
                menuItem.setEnabled(true);
            }
        }

        return Unit.INSTANCE;
    }

    private boolean doesIntentContainUrl(Intent intent) {
        if (intent != null) {
            Bundle extras = intent.getExtras();
            if (extras != null) {
                String url = extras.getString(Intent.EXTRA_TEXT);
                return URLUtil.isHttpUrl(url) || URLUtil.isHttpsUrl(url);
            }
        }
        return false;
    }

    private void storeUrlForFutureDelivery(Device device, String url) {
        Set<String> oldUrlSet = mSharedPrefs.getStringSet(KEY_UNREACHABLE_URL_LIST + device.getDeviceId(), null);
        // According to the API docs, we should not directly modify the set returned above
        Set<String> newUrlSet = new HashSet<>();
        newUrlSet.add(url);
        if (oldUrlSet != null) {
            newUrlSet.addAll(oldUrlSet);
        }
        mSharedPrefs.edit().putStringSet(KEY_UNREACHABLE_URL_LIST + device.getDeviceId(), newUrlSet).apply();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mSharedPrefs = PreferenceManager.getDefaultSharedPreferences (this);

        setSupportActionBar(getBinding().toolbarLayout.toolbar);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);

        ActionBar actionBar = getSupportActionBar();
        getBinding().devicesListLayout.refreshListLayout.setOnRefreshListener(this::refreshDevicesAction);
        if (actionBar != null) {
            actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_HOME | ActionBar.DISPLAY_SHOW_TITLE | ActionBar.DISPLAY_SHOW_CUSTOM);
        }

        WindowHelper.setupBottomPadding(getBinding().devicesListLayout.devicesList);
    }

    @Override
    protected void onStart() {
        super.onStart();

        final Intent intent = getIntent();
        String deviceId = intent.getStringExtra("deviceId");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && deviceId == null) {
            deviceId = intent.getStringExtra(Intent.EXTRA_SHORTCUT_ID);
        }

        if (deviceId != null) {
            SharePlugin plugin = OpdlTransfer.getInstance().getDevicePlugin(deviceId, SharePlugin.class);
            if (plugin != null) {
                plugin.share(intent);
            } else {
                Bundle extras = intent.getExtras();
                if (extras != null && extras.containsKey(Intent.EXTRA_TEXT)) {
                    final Device device = OpdlTransfer.getInstance().getDevice(deviceId);
                    if (doesIntentContainUrl(intent) && device != null && !device.isReachable()) {
                        final String text = extras.getString(Intent.EXTRA_TEXT);
                        storeUrlForFutureDelivery(device, text);
                    }
                }
            }
            finish();
        } else {
            OpdlTransfer.getInstance().addDeviceListChangedCallback("ShareActivity", () -> runOnUiThread(this::updateDeviceList));
            BackgroundService.ForceRefreshConnections(this); // force a network re-discover
            updateDeviceList();
        }
    }

    @Override
    protected void onStop() {
        OpdlTransfer.getInstance().removeDeviceListChangedCallback("ShareActivity");
        super.onStop();
    }
}
