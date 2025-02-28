package com.hover.stax;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.res.ResourcesCompat;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.WorkManager;

import com.amplitude.api.Amplitude;
import com.google.firebase.messaging.FirebaseMessaging;
import com.hover.sdk.actions.HoverAction;
import com.hover.sdk.api.Hover;
import com.hover.stax.channels.UpdateChannelsWorker;
import com.hover.stax.destruct.SelfDestructActivity;
import com.hover.stax.onboarding.OnboardingActivity;
import com.hover.stax.utils.Constants;
import com.hover.stax.home.MainActivity;
import com.hover.stax.schedules.ScheduleWorker;
import com.hover.stax.settings.BiometricChecker;
import com.hover.stax.utils.blur.StaxBlur;
import com.hover.stax.utils.UIHelper;
import com.hover.stax.utils.Utils;

import java.util.Objects;

import static com.hover.stax.utils.Constants.AUTH_CHECK;
import static com.hover.stax.utils.Constants.FRAGMENT_DIRECT;

public class SplashScreenActivity extends AppCompatActivity implements BiometricChecker.AuthListener {
	private final static String TAG = "SplashScreenActivity";

	private final static int BLUR_DELAY = 1000, LOGO_DELAY = 1200, NAV_DELAY = 1800,
		SPLASH_ICON_WIDTH = 177, SPLASH_ICON_HEIGHT = 57;

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		startSplashForegroundSequence();
		startBackgroundProcesses();
		continueOn();
	}

	private void startSplashForegroundSequence() {
		setContentView(R.layout.splash_screen_layout);
		blurBackground();
		fadeInLogo();
	}

	private void startBackgroundProcesses() {
		initAmplitude();
		initHover();
		createNotificationChannel();
		startWorkers();
		Utils.setFirebaseMessagingTopic(getString(R.string.firebase_topic_everyone));
		FirebaseMessaging.getInstance().getToken().addOnSuccessListener(this, s -> Log.d(TAG, "Firebase ID is: "+s));
	}
	
	private void blurBackground() {
		new Handler(Looper.getMainLooper()).postDelayed(() -> {
			Bitmap bg = BitmapFactory.decodeResource(getResources(), R.drawable.splash_background);
			Bitmap bitmap = new StaxBlur(this, 16, 1).transform(bg);
			ImageView bgView = findViewById(R.id.splash_image_blur);
			if (bgView != null) {
				bgView.setImageBitmap(bitmap);
				bgView.setVisibility(View.VISIBLE);
				bgView.setAnimation(loadFadeIn(this));
			}
		}, BLUR_DELAY);
	}

	private void fadeInLogo() {
		TextView tv = findViewById(R.id.splash_content);
		setSplashContentTopDrawable(tv);

		new Handler(Looper.getMainLooper()).postDelayed(() -> {
			tv.setVisibility(View.VISIBLE);
			tv.setAnimation(loadFadeIn(this));
		}, LOGO_DELAY);
	}

	private Animation loadFadeIn(Context context) {
		return AnimationUtils.loadAnimation(context, android.R.anim.fade_in);
	}

	private void setSplashContentTopDrawable(TextView tv) {
		Drawable dr = ResourcesCompat.getDrawable (getResources(), R.mipmap.stax, null);
		assert dr != null;
		Bitmap bitmap = ((BitmapDrawable) dr).getBitmap();
		Drawable d = new BitmapDrawable(getResources(), Bitmap.createScaledBitmap(bitmap, SPLASH_ICON_WIDTH, SPLASH_ICON_HEIGHT, true));
		tv.setCompoundDrawablesWithIntrinsicBounds(null, d,null,null);
	}

	private void continueOn() {
		new Handler().postDelayed(() -> {
			if (Utils.getSharedPrefs(this).getInt(AUTH_CHECK, 0) == 1) new BiometricChecker(this, this).startAuthentication(null);
			else chooseNavigation(getIntent());
		}, NAV_DELAY);
	}

	private void initAmplitude() {
		Amplitude.getInstance().initialize(this, getString(R.string.amp)).enableForegroundTracking(getApplication());

		if(getIntent().getExtras() !=null) {
			String fcmTitle = getIntent().getExtras().getString(Constants.FROM_FCM);
			if(fcmTitle !=null) {
				Amplitude.getInstance().logEvent(getString(R.string.cliucked_push_notification, fcmTitle));
			}
		}
	}

	private void initHover() {
		Hover.initialize(this);
		Hover.setBranding(getString(R.string.app_name), R.mipmap.stax, this);
		Hover.setPermissionActivity(Constants.PERM_ACTIVITY, this);
	}

	private Boolean shouldSelfDestructWhenAppVersionExpires(Boolean value) {
		if(value && SelfDestructActivity.isExpired(this)){
			startActivity(new Intent(this, SelfDestructActivity.class));
			finish();
			return true;
		} else return false;
	}

	private void createNotificationChannel() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			int importance = NotificationManager.IMPORTANCE_DEFAULT;
			NotificationChannel channel = new NotificationChannel("DEFAULT", getString(R.string.notify_default_title), importance);
			channel.setDescription(getString(R.string.notify_default_channel_descrip));
			NotificationManager notificationManager = getSystemService(NotificationManager.class);
			notificationManager.createNotificationChannel(channel);
		}
	}

	private void startWorkers() {
		WorkManager wm = WorkManager.getInstance(this);
		startChannelWorker(wm);
		startScheduleWorker(wm);
	}
	private void startChannelWorker(WorkManager wm) {
		wm.beginUniqueWork(UpdateChannelsWorker.CHANNELS_WORK_ID, ExistingWorkPolicy.KEEP, UpdateChannelsWorker.makeWork()).enqueue();
		wm.enqueueUniquePeriodicWork(UpdateChannelsWorker.TAG, ExistingPeriodicWorkPolicy.KEEP, UpdateChannelsWorker.makeToil());
	}
	private void startScheduleWorker(WorkManager wm) {
		wm.enqueueUniquePeriodicWork(ScheduleWorker.TAG, ExistingPeriodicWorkPolicy.KEEP, ScheduleWorker.makeToil());
	}

	private void chooseNavigation(Intent intent) {
		 if(!OnboardingActivity.hasPassedThrough(this)) goToOnboardingActivity();
		 else if(isToRedirectFromMainActivity(intent)) {
			assert intent.getExtras() !=null;
			assert intent.getExtras().getString(FRAGMENT_DIRECT) !=null;
			String redirectLink = Objects.requireNonNull(intent.getExtras().getString(FRAGMENT_DIRECT));

			if(redirectionIsExternal(redirectLink)) openUrl(redirectLink, this);
			else goToMainActivity(redirectLink);
		}
		else if(isForFulfilRequest(intent)) goToFulfillRequestActivity(intent);
		else goToMainActivity(null);

		finish();
	}

	public static void openUrl(String url, Context ctx) {
		Intent i = new Intent(Intent.ACTION_VIEW);
		i.setData(Uri.parse(url));
		ctx.startActivity(i);
	}

	private void goToOnboardingActivity() {
		startActivity(new Intent(this, OnboardingActivity.class));
	}
	private void goToFulfillRequestActivity(Intent intent) {
		Intent i = new Intent(this, MainActivity.class);
		i.putExtra(Constants.REQUEST_LINK, intent.getData().toString());
		startActivity(i);
	}
	private void goToMainActivity(String redirectLink) {
		Intent intent = new Intent(this, MainActivity.class);
		try{ if(redirectLink !=null) intent.putExtra(FRAGMENT_DIRECT, Integer.parseInt(redirectLink));
		}catch (NumberFormatException e){Utils.logErrorAndReportToFirebase(TAG, getString(R.string.firebase_fcm_redirect_format_err), e);}

		startActivity(intent);
	}

	@Override
	public void onAuthError(String error) {
		UIHelper.flashMessage(this, getString(R.string.toast_error_auth));
	}

	@Override
	public void onAuthSuccess(HoverAction act) {
		chooseNavigation(getIntent());
	}

	private boolean redirectionIsExternal(String redirectTo) {
		return redirectTo.contains("https");
	}
	private boolean isToRedirectFromMainActivity(Intent intent) {
		return intent.getExtras() !=null && intent.getExtras().getString(FRAGMENT_DIRECT) !=null;
	}
	private boolean isForFulfilRequest(Intent intent) {
		return intent.getAction() != null && intent.getAction().equals(Intent.ACTION_VIEW) && intent.getData() != null;
	}
}
