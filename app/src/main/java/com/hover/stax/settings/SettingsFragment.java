package com.hover.stax.settings;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.amplitude.api.Amplitude;
import com.hover.stax.R;
import com.hover.stax.channels.Channel;
import com.hover.stax.languages.Lang;
import com.hover.stax.languages.LanguageViewModel;
import com.hover.stax.languages.SelectLanguageActivity;
import com.hover.stax.navigation.NavigationInterface;
import com.hover.stax.utils.Constants;
import com.hover.stax.utils.UIHelper;
import com.hover.stax.views.StaxDialog;

import java.util.List;

import static android.view.View.GONE;

public class SettingsFragment extends Fragment implements NavigationInterface {
	final public static String LANG_CHANGE = "Settings";

	private ArrayAdapter<Channel> accountAdapter;

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		Amplitude.getInstance().logEvent(getString(R.string.visit_screen, getString(R.string.visit_security)));
		PinsViewModel securityViewModel = new ViewModelProvider(this).get(PinsViewModel.class);

		View root = inflater.inflate(R.layout.fragment_settings, container, false);
		setUpAccounts(root, securityViewModel);
		setUpChooseLang(root);
		return root;
	}

	private void setUpChooseLang(View root) {
		TextView btn = root.findViewById(R.id.select_language_btn);
		LanguageViewModel languageViewModel = new ViewModelProvider(this).get(LanguageViewModel.class);
		languageViewModel.loadLanguages().observe(getViewLifecycleOwner(), languages -> {
			for (Lang lang : languages) {
				if (lang.isSelected()) btn.setText(lang.name);
			}
		});

		assert getActivity()!=null;
		btn.setOnClickListener(view -> navigateToLanguageSelectionFragment(getActivity()));
	}

	private void setUpAccounts(View root, PinsViewModel securityViewModel) {
		accountAdapter = new ArrayAdapter<>(root.getContext(), R.layout.stax_spinner_item);
		securityViewModel.getSelectedChannels().observe(getViewLifecycleOwner(), channels -> {
			showAccounts(channels, root);
			if (channels != null && channels.size() > 1)
				createDefaultSelector(channels, root, securityViewModel);
			else
				root.findViewById(R.id.defaultAccountEntry).setVisibility(GONE);
		});
	}


	private void showAccounts(List<Channel> channels, View root) {
		ListView lv = root.findViewById(R.id.accounts_list);
		accountAdapter.clear();
		accountAdapter.addAll(channels);
		lv.setAdapter(accountAdapter);
		lv.setOnItemClickListener((arg0, arg1, position, arg3) -> navigateToPinUpdateFragment(channels.get(position).id, SettingsFragment.this));
		UIHelper.fixListViewHeight(lv);
	}



	private void createDefaultSelector(List<Channel> channels, View root, PinsViewModel securityViewModel) {
		AutoCompleteTextView spinner = root.findViewById(R.id.defaultAccountSpinner);
		root.findViewById(R.id.defaultAccountEntry).setVisibility(View.VISIBLE);
		spinner.setAdapter(accountAdapter);
		spinner.setText(spinner.getAdapter().getItem(0).toString(), false);
		spinner.setOnItemClickListener((adapterView, view, pos, id) -> {
			if (pos != 0) securityViewModel.setDefaultAccount(channels.get(pos));
		});
	}
}
