package com.hover.stax.balances;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.amplitude.api.Amplitude;
import com.hover.stax.R;
import com.hover.stax.channels.Channel;
import com.hover.stax.channels.ChannelDropdown;
import com.hover.stax.channels.ChannelDropdownViewModel;
import com.hover.stax.home.MainActivity;
import com.hover.stax.navigation.NavigationInterface;
import com.hover.stax.requests.Request;
import com.hover.stax.schedules.Schedule;
import com.hover.stax.transactions.TransactionHistoryAdapter;
import com.hover.stax.transactions.TransactionHistoryViewModel;
import com.hover.stax.utils.UIHelper;
import com.hover.stax.views.StaxCardView;

import java.util.List;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

public class BalancesFragment extends Fragment implements TransactionHistoryAdapter.SelectListener,
																  ScheduledAdapter.SelectListener,
																  RequestsAdapter.SelectListener,
																  NavigationInterface {
	final public static String TAG = "BalanceFragment";

	private BalancesViewModel balancesViewModel;
	private FutureViewModel futureViewModel;
	private TransactionHistoryViewModel transactionsViewModel;
	private BalanceAdapter balanceAdapter;
	private ChannelDropdownViewModel channelDropdownViewModel;

	private TextView addChannelLink;
	private ChannelDropdown channelDropdown;
	private boolean balancesVisible = false;

	public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		Amplitude.getInstance().logEvent(getString(R.string.visit_screen, getString(R.string.visit_balance_and_history)));
		balancesViewModel = new ViewModelProvider(requireActivity()).get(BalancesViewModel.class);
		channelDropdownViewModel = new ViewModelProvider(requireActivity()).get(ChannelDropdownViewModel.class);

		futureViewModel = new ViewModelProvider(requireActivity()).get(FutureViewModel.class);
		transactionsViewModel = new ViewModelProvider(requireActivity()).get(TransactionHistoryViewModel.class);
		return inflater.inflate(R.layout.fragment_balance, container, false);
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

		setUpBalances(view);
		setUpLinkNewAccount(view);
		setUpFuture(view);
		setUpHistory(view);
		view.findViewById(R.id.refresh_accounts_btn).setOnClickListener(this::refreshBalances);
	}

	private void setUpBalances(View view) {
		initBalanceCard(view);
		balancesViewModel.getSelectedChannels().observe(getViewLifecycleOwner(), channels -> updateServices(channels, view));
	}
	private void setUpLinkNewAccount(View view) {
		addChannelLink = view.findViewById(R.id.new_account_link);
		addChannelLink.setOnClickListener(v -> navigateToLinkAccountFragment(getActivity()));
		channelDropdown = view.findViewById(R.id.channel_dropdown);
	}

	private void initBalanceCard(View view) {
		StaxCardView balanceCard = view.findViewById(R.id.balance_card);
		balanceCard.setIcon(balancesVisible ? R.drawable.ic_visibility_on : R.drawable.ic_visibility_off);
		balanceCard.setOnClickIcon(v -> {
			balancesVisible = !balancesVisible;
			balanceCard.setIcon(balancesVisible ? R.drawable.ic_visibility_on : R.drawable.ic_visibility_off);
			balanceAdapter.showBalance(balancesVisible);
		});

		RecyclerView recyclerView = view.findViewById(R.id.balances_recyclerView);
		recyclerView.setLayoutManager(UIHelper.setMainLinearManagers(getContext()));
		recyclerView.setHasFixedSize(true);
	}

	private void updateServices(List<Channel> channels, View view) {
		RecyclerView recyclerView = view.findViewById(R.id.balances_recyclerView);
		balanceAdapter = new BalanceAdapter(channels, (MainActivity) getActivity());
		recyclerView.setAdapter(balanceAdapter);
		recyclerView.setVisibility(channels != null && channels.size() > 0 ? VISIBLE : GONE);

		((StaxCardView) view.findViewById(R.id.balance_card)).backButton.setVisibility(channels != null && channels.size() > 0  ? VISIBLE : GONE);

		toggleLink(channels != null && channels.size() > 0);
		channelDropdown.setObservers(channelDropdownViewModel, getActivity());
	}

	public void toggleLink(boolean show) {
		addChannelLink.setVisibility(show ? VISIBLE : GONE);
		channelDropdown.setVisibility(show ? GONE : VISIBLE);
	}

	private void refreshBalances(View v) {
		if (channelDropdown.getHighlighted() != null) {
			balancesViewModel.getActions().observe(getViewLifecycleOwner(), actions -> {
				balancesViewModel.setAllRunning(v.getContext());
			});
			channelDropdownViewModel.setChannelSelected(channelDropdown.getHighlighted());

		} else if (channelDropdownViewModel.getSelectedChannels().getValue() == null || channelDropdownViewModel.getSelectedChannels().getValue().size() == 0)
			channelDropdown.setError(getString(R.string.refresh_balance_error));
		else
			balancesViewModel.setAllRunning(v.getContext());
	}

	private void setUpFuture(View root) {
		futureViewModel.getScheduled().observe(getViewLifecycleOwner(), schedules -> {
			RecyclerView recyclerView = root.findViewById(R.id.scheduled_recyclerView);
			recyclerView.setLayoutManager(UIHelper.setMainLinearManagers(getContext()));
			recyclerView.setAdapter(new ScheduledAdapter(schedules, this));
			setFutureVisible(root, schedules, futureViewModel.getRequests().getValue());
		});

		futureViewModel.getRequests().observe(getViewLifecycleOwner(), requests -> {
			RecyclerView rv = root.findViewById(R.id.requests_recyclerView);
			rv.setLayoutManager(UIHelper.setMainLinearManagers(getContext()));
			rv.setAdapter(new RequestsAdapter(requests, this));
			setFutureVisible(root, futureViewModel.getScheduled().getValue(), requests);
		});
	}

	private void setFutureVisible(View root, List<Schedule> schedules, List<Request> requests) {
		boolean visible = (schedules != null && schedules.size() > 0) || (requests != null && requests.size() > 0);
		root.findViewById(R.id.scheduled_card).setVisibility(visible ? VISIBLE : GONE);
	}

	private void setUpHistory(View view) {
		RecyclerView rv = view.findViewById(R.id.transaction_history_recyclerView);
		rv.setLayoutManager(UIHelper.setMainLinearManagers(getContext()));

		transactionsViewModel.getStaxTransactions().observe(getViewLifecycleOwner(), staxTransactions -> {
			rv.setAdapter(new TransactionHistoryAdapter(staxTransactions, BalancesFragment.this));
			view.findViewById(R.id.no_history).setVisibility(staxTransactions.size() > 0 ? GONE : VISIBLE);
		});
	}

	@Override
	public void viewTransactionDetail(String uuid) { navigateToTransactionDetailsFragment(uuid, this); }

	@Override
	public void viewScheduledDetail(int id) { navigateToScheduleDetailsFragment(id, this); }

	@Override
	public void viewRequestDetail(int id) { navigateToRequestDetailsFragment(id, this); }
}
