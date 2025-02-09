package com.hover.stax.views;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.hover.stax.R;

public class StaxCardView extends FrameLayout {

	private String title;
	private boolean showBack, useContextBackPress;

	private LinearLayout contentView;
	public ImageButton backButton;
	private int backDrawable = 0;
	private int bgColor;

	public StaxCardView(Context context, AttributeSet attrs) {
		super(context, attrs);
		getAttrs(context, attrs);
		LayoutInflater.from(context).inflate(R.layout.stax_card_view, this);
		contentView = findViewById(R.id.content);
		backButton = findViewById(R.id.backButton);
		fillFromAttrs();
	}

	private void getAttrs(Context context, AttributeSet attrs) {
		TypedArray a = context.getTheme().obtainStyledAttributes(attrs, R.styleable.StaxCardView, 0, 0);
		try {
			title = a.getString(R.styleable.StaxCardView_title);
			showBack = a.getBoolean(R.styleable.StaxCardView_showBack, false);
			useContextBackPress = a.getBoolean(R.styleable.StaxCardView_defaultBackPress, true);
			backDrawable = a.getResourceId(R.styleable.StaxCardView_backRes, 0);
			bgColor = a.getColor(R.styleable.StaxCardView_staxCardColor, context.getResources().getColor(R.color.colorPrimary));
		} finally {
			a.recycle();
		}
	}
	
	@SuppressLint("ResourceType")
	public void setBackgroundColor(int colorRes) {
		bgColor = getContext().getResources().getColor(colorRes);
		contentView.setBackgroundColor(bgColor);
	}

	public void setTitle(String t) {
		if (t != null) ((TextView) findViewById(R.id.title)).setText(t);
	}

	public void setTitle(int titleString) {
		if (titleString != 0) ((TextView) findViewById(R.id.title)).setText(getContext().getString(titleString));
	}

	public void setIcon(int icon) {
		if (icon != 0) { backButton.setImageResource(icon); }
	}

	public void setOnClickIcon(OnClickListener listener) {
		if (listener != null) { backButton.setOnClickListener(listener); }
	}

	private void fillFromAttrs() {
		if (title != null) ((TextView) findViewById(R.id.title)).setText(title);
		else findViewById(R.id.header).setVisibility(GONE);

		if (useContextBackPress) backButton.setOnClickListener(view -> triggerBack());
		if (showBack) backButton.setVisibility(VISIBLE);
		if (backDrawable != 0) backButton.setImageResource(backDrawable);
		contentView.setBackgroundColor(bgColor);
	}

	@Override
	public void addView(View child, int index, ViewGroup.LayoutParams params) {
		if (contentView == null)
			super.addView(child, index, params);
		else
			contentView.addView(child, index, params);
	}

	private void triggerBack() {
		try {
			((Activity) getContext()).onBackPressed();
		} catch (Exception ignored) {
		}
	}
}