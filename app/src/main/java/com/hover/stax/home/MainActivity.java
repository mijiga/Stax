package com.hover.stax.home;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import com.amplitude.api.Amplitude;
import com.hover.sdk.actions.HoverAction;
import com.hover.stax.R;
import com.hover.stax.balances.BalanceAdapter;
import com.hover.stax.balances.BalancesViewModel;
import com.hover.stax.channels.Channel;
import com.hover.stax.utils.Constants;
import com.hover.stax.hover.HoverSession;
import com.hover.stax.navigation.AbstractNavigationActivity;
import com.hover.stax.schedules.Schedule;
import com.hover.stax.settings.BiometricChecker;
import com.hover.stax.transactions.TransactionHistoryViewModel;
import com.hover.stax.utils.DateUtils;
import com.hover.stax.utils.UIHelper;

import java.util.List;

public class MainActivity extends AbstractNavigationActivity implements
	BalancesViewModel.RunBalanceListener, BalanceAdapter.BalanceListener, BiometricChecker.AuthListener {

	final public static String TAG = "MainActivity";
	private BalancesViewModel balancesViewModel;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		balancesViewModel = new ViewModelProvider(this).get(BalancesViewModel.class);
		balancesViewModel.setListener(this);
		balancesViewModel.getSelectedChannels().observe(this, channels -> Log.i(TAG, "Channels observer is neccessary to make updates fire, but all logic is in viewmodel. " + channels.size()));
		balancesViewModel.getToRun().observe(this, actions -> Log.i(TAG, "RunActions observer is neccessary to make updates fire, but all logic is in viewmodel. " + actions.size()));
		balancesViewModel.getRunFlag().observe(this, flag -> Log.i(TAG, "Flag observer is neccessary to make updates fire, but all logic is in viewmodel. " + flag));
		balancesViewModel.getActions().observe(this, actions -> Log.i(TAG, "Actions observer is neccessary to make updates fire, but all logic is in viewmodel. " + actions.size()));

		setUpNav();
		checkForRequest(getIntent());
		checkForFragmentDirection(getIntent());
	}

	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);
		checkForRequest(intent);
	}

	private void checkForRequest(Intent intent) {
		if (intent.hasExtra(Constants.REQUEST_LINK)) navigateToTransferActivity(HoverAction.P2P, true, intent, this);
	}
	private void checkForFragmentDirection(Intent intent) {
		if (intent.hasExtra(Constants.FRAGMENT_DIRECT)) {
			int toWhere = intent.getExtras().getInt(Constants.FRAGMENT_DIRECT, 0);
			checkPermissionsAndNavigate(toWhere);
		}
	}

	@Override
	public void onTapDetail(int channel_id) { navigateToChannelDetailsFragment(channel_id, this); }

	@Override
	public void onTapRefresh(int channel_id) {
		Amplitude.getInstance().logEvent(getString(R.string.refresh_balance_single));
		balancesViewModel.setRunning(channel_id);
	}

	@Override
	public void startRun(HoverAction a, int i) {
			run(a, i);
	}

	private void run(HoverAction action, int index) {
		Log.e(TAG, "running index: " + index);
		if (balancesViewModel.getChannel(action.channel_id) != null) {
			new HoverSession.Builder(action, balancesViewModel.getChannel(action.channel_id), MainActivity.this, index)
				.finalScreenTime(0).run();
		} else { // Fix for auth issue on OnePlus 6
			new Handler(Looper.getMainLooper()).post(() -> {
				balancesViewModel.getSelectedChannels().observe(MainActivity.this, new Observer<List<Channel>>() {
					@Override
					public void onChanged(List<Channel> channels) {
						if (channels != null && balancesViewModel.getChannel(channels, action.channel_id) != null) {
							run(action, 0);
							balancesViewModel.getSelectedChannels().removeObserver(this);
						}
					}
				});
			});
		}
	}

	@Override
	public void onAuthError(String error) {
		Log.e(TAG, "error: " + error);
	}

	@Override
	public void onAuthSuccess(HoverAction act) {
			run(act, 0);
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		switch (requestCode) {
			case Constants.TRANSFER_REQUEST:
				if (data != null) { onProbableHoverCall(data); }
				break;
			case Constants.REQUEST_REQUEST:
				if (resultCode == RESULT_OK) { onRequest(data); }
				break;
			default: // requestCode < Constants.BALANCE_MAX // Balance call
				balancesViewModel.setRan(requestCode);
				if (resultCode == RESULT_OK && data != null && data.getAction() != null) onProbableHoverCall(data);
		}
	}

	private void onProbableHoverCall(Intent data) {
		if (data.getAction() != null && data.getAction().equals(Constants.SCHEDULED)) {
			showMessage(getString(R.string.toast_confirm_schedule, DateUtils.humanFriendlyDate(data.getLongExtra(Schedule.DATE_KEY, 0))));
		} else {
			Amplitude.getInstance().logEvent(getString(R.string.finish_load_screen));
			new ViewModelProvider(this).get(TransactionHistoryViewModel.class).saveTransaction(data, this);
		}
	}


	private void onRequest(Intent data) {
		if (data.getAction().equals(Constants.SCHEDULED))
			showMessage(getString(R.string.toast_request_scheduled, DateUtils.humanFriendlyDate(data.getLongExtra(Schedule.DATE_KEY, 0))));
		else
			showMessage(getString(R.string.toast_confirm_request));
	}

	private void showMessage(String str) {
		UIHelper.flashMessage(this, findViewById(R.id.fab), str);
	}
}