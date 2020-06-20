/*
 * Copyright 2018 Nikita Shakarun
 * Copyright 2020 Yury Kharchenko
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ru.playsoftware.j2meloader.config;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.util.TypedValue;
import android.view.Display;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Checkable;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import javax.microedition.shell.MicroActivity;
import javax.microedition.util.ContextHolder;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.PopupMenu;
import androidx.fragment.app.FragmentManager;
import androidx.preference.PreferenceManager;
import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.base.BaseActivity;
import ru.playsoftware.j2meloader.settings.KeyMapperActivity;
import ru.playsoftware.j2meloader.util.FileUtils;
import yuku.ambilwarna.AmbilWarnaDialog;

public class ConfigActivity extends BaseActivity implements View.OnClickListener, ShaderTuneAlert.Callback {
	private static final String TAG = ConfigActivity.class.getSimpleName();

	public static final String ACTION_EDIT = "config.edit";
	public static final String ACTION_EDIT_PROFILE = "config.edit.profile";
	public static final String CONFIG_PATH_KEY = "configPath";
	public static final String MIDLET_NAME_KEY = "midletName";
	private static final int[] SCREEN_SIZES = {128, 176, 220, 320};
	private static final int[] FONT_SIZES = {
			9, 13, 15, // 128
			13, 15, 20, // 176
			15, 18, 22, // 220
			18, 22, 26, // 320
	};

	protected ScrollView rootContainer;
	protected EditText tfScreenWidth;
	protected EditText tfScreenHeight;
	protected EditText tfScreenBack;
	protected SeekBar sbScaleRatio;
	protected EditText tfScaleRatioValue;
	protected Spinner spOrientation;
	protected Checkable cxScaleToFit;
	protected Checkable cxKeepAspectRatio;
	protected Checkable cxFilter;
	protected Checkable cxImmediate;
	protected Spinner spGraphicsMode;
	protected Spinner spShader;
	protected CompoundButton cxParallel;
	protected Checkable cxForceFullscreen;
	protected Checkable cxShowFps;
	protected EditText tfFpsLimit;

	protected EditText tfFontSizeSmall;
	protected EditText tfFontSizeMedium;
	protected EditText tfFontSizeLarge;
	protected Checkable cxFontSizeInSP;
	protected CompoundButton cxShowKeyboard;

	private View rootInputConfig;
	private View groupVkConfig;
	protected Checkable cxVKFeedback;
	protected Checkable cxTouchInput;

	protected Spinner spVKType;
	protected Spinner spLayout;
	private Spinner spButtonsShape;
	protected SeekBar sbVKAlpha;
	protected Checkable cxVKForceOpacity;
	protected EditText tfVKHideDelay;
	protected EditText tfVKFore;
	protected EditText tfVKBack;
	protected EditText tfVKSelFore;
	protected EditText tfVKSelBack;
	protected EditText tfVKOutline;

	protected EditText tfSystemProperties;

	protected ArrayList<String> screenPresets = new ArrayList<>();

	protected ArrayList<Integer> fontSmall;
	protected ArrayList<Integer> fontMedium;
	protected ArrayList<Integer> fontLarge;
	protected ArrayList<String> fontAdapter;

	private File keylayoutFile;
	private File dataDir;
	private ProfileModel params;
	private FragmentManager fragmentManager;
	private boolean isProfile;
	private Display display;
	private File configDir;
	private String defProfile;
	private ArrayAdapter<ShaderInfo> spShaderAdapter;
	private View shaderContainer;
	private ImageButton btShaderTune;

	@SuppressLint({"StringFormatMatches", "StringFormatInvalid"})
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Intent intent = getIntent();
		String action = intent.getAction();
		isProfile = ACTION_EDIT_PROFILE.equals(action);
		boolean showSettings = isProfile || ACTION_EDIT.equals(action);
		String dirName = intent.getDataString();
		if (dirName == null) {
			finish();
			return;
		}
		if (isProfile) {
			setResult(RESULT_OK, new Intent().setData(intent.getData()));
			configDir = new File(Config.getProfilesDir(), dirName);
			setTitle(dirName);
		} else {
			setTitle(intent.getStringExtra(MIDLET_NAME_KEY));
			dataDir = new File(Config.getDataDir(), dirName);
			dataDir.mkdirs();
			configDir = new File(Config.getConfigsDir(), dirName);
		}
		configDir.mkdirs();

		defProfile = PreferenceManager.getDefaultSharedPreferences(getApplicationContext())
				.getString(Config.PREF_DEFAULT_PROFILE, null);
		loadConfig();
		if (!params.isNew && !showSettings) {
			startMIDlet();
			return;
		}
		loadKeyLayout();
		setContentView(R.layout.activity_config);
		//noinspection ConstantConditions
		getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		display = getWindowManager().getDefaultDisplay();
		fragmentManager = getSupportFragmentManager();

		rootContainer = findViewById(R.id.configRoot);
		tfScreenWidth = findViewById(R.id.tfScreenWidth);
		tfScreenHeight = findViewById(R.id.tfScreenHeight);
		tfScreenBack = findViewById(R.id.tfScreenBack);
		cxScaleToFit = findViewById(R.id.cxScaleToFit);
		sbScaleRatio = findViewById(R.id.sbScaleRatio);
		tfScaleRatioValue = findViewById(R.id.tfScaleRatioValue);
		spOrientation = findViewById(R.id.spOrientation);
		cxKeepAspectRatio = findViewById(R.id.cxKeepAspectRatio);
		cxFilter = findViewById(R.id.cxFilter);
		cxImmediate = findViewById(R.id.cxImmediate);
		spGraphicsMode = findViewById(R.id.spGraphicsMode);
		spShader = findViewById(R.id.spShader);
		btShaderTune = findViewById(R.id.btShaderTune);
		shaderContainer = findViewById(R.id.shaderContainer);
		cxParallel = findViewById(R.id.cxParallel);
		cxForceFullscreen = findViewById(R.id.cxForceFullscreen);
		cxShowFps = findViewById(R.id.cxShowFps);
		tfFpsLimit = findViewById(R.id.tfFpsLimit);

		tfFontSizeSmall = findViewById(R.id.tfFontSizeSmall);
		tfFontSizeMedium = findViewById(R.id.tfFontSizeMedium);
		tfFontSizeLarge = findViewById(R.id.tfFontSizeLarge);
		cxFontSizeInSP = findViewById(R.id.cxFontSizeInSP);
		tfSystemProperties = findViewById(R.id.tfSystemProperties);

		rootInputConfig = findViewById(R.id.rootInputConfig);
		cxTouchInput = findViewById(R.id.cxTouchInput);
		cxShowKeyboard = findViewById(R.id.cxIsShowKeyboard);
		groupVkConfig = findViewById(R.id.groupVkConfig);
		cxVKFeedback = findViewById(R.id.cxVKFeedback);
		cxVKForceOpacity = findViewById(R.id.cxVKForceOpacity);

		spVKType = findViewById(R.id.spVKType);
		spLayout = findViewById(R.id.spLayout);
		spButtonsShape = findViewById(R.id.spButtonsShape);
		sbVKAlpha = findViewById(R.id.sbVKAlpha);
		tfVKHideDelay = findViewById(R.id.tfVKHideDelay);
		tfVKFore = findViewById(R.id.tfVKFore);
		tfVKBack = findViewById(R.id.tfVKBack);
		tfVKSelFore = findViewById(R.id.tfVKSelFore);
		tfVKSelBack = findViewById(R.id.tfVKSelBack);
		tfVKOutline = findViewById(R.id.tfVKOutline);

		fillScreenSizePresets(display.getWidth(), display.getHeight());

		fontSmall = new ArrayList<>();
		fontMedium = new ArrayList<>();
		fontLarge = new ArrayList<>();
		fontAdapter = new ArrayList<>();

		addFontSizePreset("128 x 128", 9, 13, 15);
		addFontSizePreset("128 x 160", 13, 15, 20);
		addFontSizePreset("176 x 220", 15, 18, 22);
		addFontSizePreset("240 x 320", 18, 22, 26);

		findViewById(R.id.cmdScreenSizePresets).setOnClickListener(this::showScreenPresets);
		findViewById(R.id.cmdSwapSizes).setOnClickListener(this);
		findViewById(R.id.cmdAddToPreset).setOnClickListener(v -> addResolutionToPresets());
		findViewById(R.id.cmdFontSizePresets).setOnClickListener(this);
		findViewById(R.id.cmdScreenBack).setOnClickListener(this);
		findViewById(R.id.cmdKeyMappings).setOnClickListener(this);
		findViewById(R.id.cmdVKBack).setOnClickListener(this);
		findViewById(R.id.cmdVKFore).setOnClickListener(this);
		findViewById(R.id.cmdVKSelBack).setOnClickListener(this);
		findViewById(R.id.cmdVKSelFore).setOnClickListener(this);
		findViewById(R.id.cmdVKOutline).setOnClickListener(this);
		findViewById(R.id.btEncoding).setOnClickListener(this::showCharsetPicker);
		btShaderTune.setOnClickListener(this::showShaderSettings);
		sbScaleRatio.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
			@Override
			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
				if (fromUser) tfScaleRatioValue.setText(String.valueOf(progress));
			}

			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {
			}

			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {
			}
		});
		tfScaleRatioValue.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				int length = s.length();
				if (length > 3) {
					if (start >= 3) {
						tfScaleRatioValue.getText().delete(3, length);
					} else {
						int st = start + count;
						int end = st + (before == 0 ? count : before);
						tfScaleRatioValue.getText().delete(st, Math.min(end, length));
					}
				}
			}

			@Override
			public void afterTextChanged(Editable s) {
				if (s.length() == 0) return;
				try {
					int progress = Integer.parseInt(s.toString());
					if (progress <= 100) {
						sbScaleRatio.setProgress(progress);
					} else {
						s.replace(0, s.length(), "100");
					}
				} catch (NumberFormatException e) {
					s.clear();
				}
			}
		});
		spGraphicsMode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
				switch (position) {
					case 0:
					case 3:
						cxParallel.setVisibility(View.VISIBLE);
						shaderContainer.setVisibility(View.GONE);
						break;
					case 1:
						cxParallel.setVisibility(View.GONE);
						initShaderSpinner();
						break;
					case 2:
						cxParallel.setVisibility(View.GONE);
						shaderContainer.setVisibility(View.GONE);
				}
			}

			@Override
			public void onNothingSelected(AdapterView<?> parent) {
			}
		});
		cxShowKeyboard.setOnClickListener((b) -> {
			View.OnLayoutChangeListener onLayoutChangeListener = new View.OnLayoutChangeListener() {
				@Override
				public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
					View focus = rootContainer.findFocus();
					if (focus != null) focus.clearFocus();
					v.scrollTo(0, rootInputConfig.getTop());
					v.removeOnLayoutChangeListener(this);
				}
			};
			rootContainer.addOnLayoutChangeListener(onLayoutChangeListener);
			groupVkConfig.setVisibility(cxShowKeyboard.isChecked() ? View.VISIBLE : View.GONE);
		});
		tfScreenBack.addTextChangedListener(new ColorTextWatcher(tfScreenBack));
		tfVKFore.addTextChangedListener(new ColorTextWatcher(tfVKFore));
		tfVKBack.addTextChangedListener(new ColorTextWatcher(tfVKBack));
		tfVKSelFore.addTextChangedListener(new ColorTextWatcher(tfVKSelFore));
		tfVKSelBack.addTextChangedListener(new ColorTextWatcher(tfVKSelBack));
		tfVKOutline.addTextChangedListener(new ColorTextWatcher(tfVKOutline));
	}

	void loadConfig() {
		params = ProfilesManager.loadConfig(configDir);
		if (params == null && defProfile != null) {
			FileUtils.copyFiles(new File(Config.getProfilesDir(), defProfile), configDir, null);
			params = ProfilesManager.loadConfig(configDir);
		}
		if (params == null) {
			params = new ProfileModel(configDir);
			ProfilesManager.saveConfig(params);
		}
		if (params.version < 1) {
			ProfilesManager.updateSystemProperties(params);
			params.version = 1;
			ProfilesManager.saveConfig(params);
		}
	}

	private void showShaderSettings(View v) {
		ShaderInfo shader = (ShaderInfo) spShader.getSelectedItem();
		params.shader = shader;
		ShaderTuneAlert.newInstance(shader).show(getSupportFragmentManager(), "ShaderTuning");
	}

	private void initShaderSpinner() {
		if (spShaderAdapter != null) {
			shaderContainer.setVisibility(View.VISIBLE);
			return;
		}
		File dir = new File(Config.getShadersDir());
		if (!dir.exists()) {
			//noinspection ResultOfMethodCallIgnored
			dir.mkdirs();
		}
		ArrayList<ShaderInfo> infos = new ArrayList<>();
		spShaderAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, infos);
		spShaderAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spShader.setAdapter(spShaderAdapter);
		File[] files = dir.listFiles((f) -> f.isFile() && f.getName().toLowerCase().endsWith(".ini"));
		if (files != null) {
			for (File file : files) {
				String text = FileUtils.getText(file.getAbsolutePath());
				String[] split = text.split("[\\n\\r]+");
				ShaderInfo info = null;
				for (String line : split) {
					if (line.startsWith("[")) {
						if (info != null) {
							infos.add(info);
						}
						info = new ShaderInfo();
					} else if (info != null) {
						try {
							info.set(line);
						} catch (Exception e) {
							Log.e(TAG, "initShaderSpinner: ", e);
						}
					}
				}
				if (info != null) {
					infos.add(info);
				}
			}
			Collections.sort(infos);
		}
		infos.add(0, new ShaderInfo(getString(R.string.identity_filter), "woesss"));
		spShaderAdapter.notifyDataSetChanged();
		ShaderInfo selected = params.shader;
		if (selected != null) {
			int position = infos.indexOf(selected);
			if (position > 0) {
				infos.get(position).values = selected.values;
				spShader.setSelection(position);
			}
		}
		shaderContainer.setVisibility(View.VISIBLE);
		spShader.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
				ShaderInfo item = (ShaderInfo) parent.getItemAtPosition(position);
				ShaderInfo.Setting[] settings = item.settings;
				for (int i = 0; i < 4; i++) {
					if (settings[i] != null) {
						btShaderTune.setVisibility(View.VISIBLE);
						return;
					}
				}
				btShaderTune.setVisibility(View.GONE);
			}

			@Override
			public void onNothingSelected(AdapterView<?> parent) {

			}
		});

	}

	private void showCharsetPicker(View v) {
		String[] charsets = Charset.availableCharsets().keySet().toArray(new String[0]);
		new AlertDialog.Builder(this).setItems(charsets, (d, w) -> {
			String enc = "microedition.encoding: " + charsets[w];
			String[] props = tfSystemProperties.getText().toString().split("[\\n\\r]+");
			int propsLength = props.length;
			if (propsLength == 0) {
				tfSystemProperties.setText(enc);
				return;
			}
			int i = propsLength - 1;
			while (i >= 0) {
				if (props[i].startsWith("microedition.encoding")) {
					props[i] = enc;
					break;
				}
				i--;
			}
			if (i < 0) {
				tfSystemProperties.append(enc);
				return;
			}
			tfSystemProperties.setText(TextUtils.join("\n", props));
		}).setTitle(R.string.pref_encoding_title).show();
	}

	private void loadKeyLayout() {
		File file = new File(configDir, Config.MIDLET_KEY_LAYOUT_FILE);
		keylayoutFile = file;
		if (isProfile || file.exists()) {
			return;
		}
		if (defProfile == null) {
			return;
		}
		File defaultKeyLayoutFile = new File(Config.getProfilesDir() + defProfile, Config.MIDLET_KEY_LAYOUT_FILE);
		if (!defaultKeyLayoutFile.exists()) {
			return;
		}
		try {
			FileUtils.copyFileUsingChannel(defaultKeyLayoutFile, file);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void onPause() {
		saveParams();
		super.onPause();
	}

	@Override
	protected void onResume() {
		super.onResume();
		loadParams(true);
	}

	@Override
	public void onConfigurationChanged(@NonNull Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		fillScreenSizePresets(display.getWidth(), display.getHeight());
	}

	private void fillScreenSizePresets(int w, int h) {
		ArrayList<String> screenPresets = this.screenPresets;
		screenPresets.clear();

		screenPresets.add("128 x 128");
		screenPresets.add("128 x 160");
		screenPresets.add("132 x 176");
		screenPresets.add("176 x 220");
		screenPresets.add("240 x 320");
		screenPresets.add("352 x 416");
		screenPresets.add("640 x 360");
		screenPresets.add("800 x 480");

		if (w > h) {
			screenPresets.add(h * 3 / 4 + " x " + h);
			screenPresets.add(h * 4 / 3 + " x " + h);
		} else {
			screenPresets.add(w + " x " + w * 4 / 3);
			screenPresets.add(w + " x " + w * 3 / 4);
		}

		screenPresets.add(w + " x " + h);
		Set<String> preset = PreferenceManager.getDefaultSharedPreferences(this)
				.getStringSet("ResolutionsPreset", null);
		if (preset != null) {
			screenPresets.addAll(preset);
		}
		Collections.sort(screenPresets, (o1, o2) -> {
			int sep1 = o1.indexOf(" x ");
			int sep2 = o2.indexOf(" x ");
			if (sep1 == -1) {
				if (sep2 != -1) return -1;
				else return 0;
			} else if (sep2 == -1) return 1;
			int r = Integer.decode(o1.substring(0, sep1)).compareTo(Integer.decode(o2.substring(0, sep2)));
			if (r != 0) return r;
			return Integer.decode(o1.substring(sep1 + 3)).compareTo(Integer.decode(o2.substring(sep2 + 3)));
		});
		String prev = null;
		for (Iterator<String> iterator = screenPresets.iterator(); iterator.hasNext(); ) {
			String next = iterator.next();
			if (next.equals(prev)) iterator.remove();
			else prev = next;
		}
	}

	private void addFontSizePreset(String title, int small, int medium, int large) {
		fontSmall.add(small);
		fontMedium.add(medium);
		fontLarge.add(large);
		fontAdapter.add(title);
	}

	private int parseInt(String s) {
		return parseInt(s, 10);
	}

	private int parseInt(String s, int radix) {
		int result;
		try {
			result = Integer.parseInt(s, radix);
		} catch (NumberFormatException e) {
			result = 0;
		}
		return result;
	}

	@SuppressLint("SetTextI18n")
	public void loadParams(boolean reloadFromFile) {
		if (reloadFromFile) {
			loadConfig();
		}
		tfScreenWidth.setText(Integer.toString(params.screenWidth));
		tfScreenHeight.setText(Integer.toString(params.screenHeight));
		tfScreenBack.setText(String.format("%06X", params.screenBackgroundColor));
		sbScaleRatio.setProgress(params.screenScaleRatio);
		tfScaleRatioValue.setText(Integer.toString(params.screenScaleRatio));
		spOrientation.setSelection(params.orientation);
		cxScaleToFit.setChecked(params.screenScaleToFit);
		cxKeepAspectRatio.setChecked(params.screenKeepAspectRatio);
		cxFilter.setChecked(params.screenFilter);
		cxImmediate.setChecked(params.immediateMode);
		cxParallel.setChecked(params.parallelRedrawScreen);
		cxForceFullscreen.setChecked(params.forceFullscreen);
		spGraphicsMode.setSelection(params.getGraphicsMode());
		cxShowFps.setChecked(params.showFps);

		tfFontSizeSmall.setText(Integer.toString(params.fontSizeSmall));
		tfFontSizeMedium.setText(Integer.toString(params.fontSizeMedium));
		tfFontSizeLarge.setText(Integer.toString(params.fontSizeLarge));
		cxFontSizeInSP.setChecked(params.fontApplyDimensions);
		boolean showVk = params.showKeyboard;
		cxShowKeyboard.setChecked(showVk);
		groupVkConfig.setVisibility(showVk ? View.VISIBLE : View.GONE);
		cxVKFeedback.setChecked(params.vkFeedback);
		cxVKForceOpacity.setChecked(params.vkForceOpacity);
		cxTouchInput.setChecked(params.touchInput);
		int fpsLimit = params.fpsLimit;
		if (fpsLimit > 0) {
			tfFpsLimit.setText(Integer.toString(fpsLimit));
		}

		spVKType.setSelection(params.vkType);
		spLayout.setSelection(params.keyCodesLayout);
		spButtonsShape.setSelection(params.vkButtonShape);
		sbVKAlpha.setProgress(params.vkAlpha);
		int vkHideDelay = params.vkHideDelay;
		if (vkHideDelay > 0) {
			tfVKHideDelay.setText(Integer.toString(vkHideDelay));
		}

		tfVKBack.setText(String.format("%06X", params.vkBgColor));
		tfVKFore.setText(String.format("%06X", params.vkFgColor));
		tfVKSelBack.setText(String.format("%06X", params.vkBgColorSelected));
		tfVKSelFore.setText(String.format("%06X", params.vkFgColorSelected));
		tfVKOutline.setText(String.format("%06X", params.vkOutlineColor));

		String systemProperties = params.systemProperties;
		if (systemProperties == null) {
			systemProperties = ContextHolder.getAssetAsString("defaults/system.props");
		}
		tfSystemProperties.setText(systemProperties);
	}

	private void saveParams() {
		try {
			int width = parseInt(tfScreenWidth.getText().toString());
			params.screenWidth = width;
			int height = parseInt(tfScreenHeight.getText().toString());
			params.screenHeight = height;
			try {
				params.screenBackgroundColor = Integer.parseInt(tfScreenBack.getText().toString(), 16);
			} catch (NumberFormatException ignored) {
			}
			params.screenScaleRatio = sbScaleRatio.getProgress();
			params.orientation = spOrientation.getSelectedItemPosition();
			params.screenScaleToFit = cxScaleToFit.isChecked();
			params.screenKeepAspectRatio = cxKeepAspectRatio.isChecked();
			params.screenFilter = cxFilter.isChecked();
			params.immediateMode = cxImmediate.isChecked();
			int mode = spGraphicsMode.getSelectedItemPosition();
			params.graphicsMode = mode;
			if (mode == 1) {
				params.shader = (ShaderInfo) spShader.getSelectedItem();
			}
			params.parallelRedrawScreen = cxParallel.isChecked();
			params.forceFullscreen = cxForceFullscreen.isChecked();
			params.showFps = cxShowFps.isChecked();
			params.fpsLimit = parseInt(tfFpsLimit.getText().toString());

			try {
				params.fontSizeSmall = Integer.parseInt(tfFontSizeSmall.getText().toString());
			} catch (NumberFormatException e) {
				params.fontSizeSmall = getFontSizeForResolution(0, width, height);
			}
			try {
				params.fontSizeMedium = Integer.parseInt(tfFontSizeMedium.getText().toString());
			} catch (NumberFormatException e) {
				params.fontSizeMedium = getFontSizeForResolution(1, width, height);
			}
			try {
				params.fontSizeLarge = Integer.parseInt(tfFontSizeLarge.getText().toString());
			} catch (NumberFormatException e) {
				params.fontSizeLarge = getFontSizeForResolution(2, width, height);
			}
			params.fontApplyDimensions = cxFontSizeInSP.isChecked();
			params.showKeyboard = cxShowKeyboard.isChecked();
			params.vkFeedback = cxVKFeedback.isChecked();
			params.vkForceOpacity = cxVKForceOpacity.isChecked();
			params.touchInput = cxTouchInput.isChecked();

			params.vkType = spVKType.getSelectedItemPosition();
			params.keyCodesLayout = spLayout.getSelectedItemPosition();
			params.vkButtonShape = spButtonsShape.getSelectedItemPosition();
			params.vkAlpha = sbVKAlpha.getProgress();
			params.vkHideDelay = parseInt(tfVKHideDelay.getText().toString());
			try {
				params.vkBgColor = Integer.parseInt(tfVKBack.getText().toString(), 16);
			} catch (Exception ignored) {
			}
			try {
				params.vkFgColor = Integer.parseInt(tfVKFore.getText().toString(), 16);
			} catch (Exception ignored) {
			}
			try {
				params.vkBgColorSelected = Integer.parseInt(tfVKSelBack.getText().toString(), 16);
			} catch (Exception ignored) {
			}
			try {
				params.vkFgColorSelected = Integer.parseInt(tfVKSelFore.getText().toString(), 16);
			} catch (Exception ignored) {
			}
			try {
				params.vkOutlineColor = Integer.parseInt(tfVKOutline.getText().toString(), 16);
			} catch (Exception ignored) {
			}
			params.systemProperties = getSystemProperties();

			ProfilesManager.saveConfig(params);
		} catch (Throwable t) {
			t.printStackTrace();
		}
	}

	@NonNull
	private String getSystemProperties() {
		String s = tfSystemProperties.getText().toString();
		String[] lines = s.split("\\n");
		StringBuilder sb = new StringBuilder(s.length());
		boolean validCharset = false;
		for (int i = lines.length - 1; i >= 0; i--) {
			String line = lines[i];
			if (line.trim().isEmpty()) continue;
			if (line.startsWith("microedition.encoding:")) {
				if (validCharset) continue;
				try {
					Charset.forName(line.substring(22).trim());
					validCharset = true;
				} catch (Exception ignored) {
					continue;
				}
			}
			sb.append(line).append('\n');
		}
		return sb.toString();
	}

	private int getFontSizeForResolution(int sizeType, int width, int height) {
		int size = Math.max(width, height);
		if (size > 0) {
			for (int i = 0; i < SCREEN_SIZES.length; i++) {
				if (SCREEN_SIZES[i] >= size) {
					return FONT_SIZES[i * 3 + sizeType];
				}
			}
		}
		return FONT_SIZES[FONT_SIZES.length - (3 - sizeType)];
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.config, menu);
		if (isProfile) {
			menu.findItem(R.id.action_start).setVisible(false);
			menu.findItem(R.id.action_clear_data).setVisible(false);
		}
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.action_start:
				startMIDlet();
				break;
			case R.id.action_clear_data:
				showClearDataDialog();
				break;
			case R.id.action_reset_settings:
				params = new ProfileModel(configDir);
				loadParams(false);
				break;
			case R.id.action_reset_layout:
				//noinspection ResultOfMethodCallIgnored
				keylayoutFile.delete();
				loadKeyLayout();
				break;
			case R.id.action_load_profile:
				LoadProfileAlert.newInstance(keylayoutFile.getParent())
						.show(fragmentManager, "load_profile");
				break;
			case R.id.action_save_profile:
				saveParams();
				SaveProfileAlert.getInstance(keylayoutFile.getParent())
						.show(fragmentManager, "save_profile");
				break;
			case android.R.id.home:
				finish();
				break;
		}
		return super.onOptionsItemSelected(item);
	}

	private void showClearDataDialog() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this)
				.setTitle(android.R.string.dialog_alert_title)
				.setMessage(R.string.message_clear_data)
				.setPositiveButton(android.R.string.ok, (d, w) -> FileUtils.clearDirectory(dataDir))
				.setNegativeButton(android.R.string.cancel, null);
		builder.show();
	}

	private void startMIDlet() {
		Intent i = new Intent(getIntent());
		i.setClass(getApplicationContext(), MicroActivity.class);
		startActivity(i);
		finish();
	}

	@SuppressLint("SetTextI18n")
	@Override
	public void onClick(View v) {
		switch (v.getId()) {
			case R.id.cmdSwapSizes:
				String tmp = tfScreenWidth.getText().toString();
				tfScreenWidth.setText(tfScreenHeight.getText().toString());
				tfScreenHeight.setText(tmp);
				break;
			case R.id.cmdFontSizePresets:
				new AlertDialog.Builder(this)
						.setTitle(getString(R.string.SIZE_PRESETS))
						.setItems(fontAdapter.toArray(new String[0]),
								(dialog, which) -> {
									tfFontSizeSmall.setText(Integer.toString(fontSmall.get(which)));
									tfFontSizeMedium.setText(Integer.toString(fontMedium.get(which)));
									tfFontSizeLarge.setText(Integer.toString(fontLarge.get(which)));
								})
						.show();
				break;
			case R.id.cmdScreenBack:
				showColorPicker(tfScreenBack);
				break;
			case R.id.cmdVKBack:
				showColorPicker(tfVKBack);
				break;
			case R.id.cmdVKFore:
				showColorPicker(tfVKFore);
				break;
			case R.id.cmdVKSelFore:
				showColorPicker(tfVKSelFore);
				break;
			case R.id.cmdVKSelBack:
				showColorPicker(tfVKSelBack);
				break;
			case R.id.cmdVKOutline:
				showColorPicker(tfVKOutline);
				break;
			case R.id.cmdKeyMappings:
				Intent i = new Intent(getIntent());
				i.setClass(getApplicationContext(), KeyMapperActivity.class);
				startActivity(i);
				break;
			default:
		}
	}

	private void showScreenPresets(View v) {
		PopupMenu popup = new PopupMenu(this, v);
		Menu menu = popup.getMenu();
		for (String preset : screenPresets) {
			menu.add(preset);
		}
		popup.setOnMenuItemClickListener(item -> {
			String string = item.getTitle().toString();
			int separator = string.indexOf(" x ");
			tfScreenWidth.setText(string.substring(0, separator));
			tfScreenHeight.setText(string.substring(separator + 3));
			return true;
		});
		popup.show();
	}

	private void showColorPicker(EditText et) {
		AmbilWarnaDialog.OnAmbilWarnaListener colorListener = new AmbilWarnaDialog.OnAmbilWarnaListener() {
			@Override
			public void onOk(AmbilWarnaDialog dialog, int color) {
				et.setText(String.format("%06X", color & 0xFFFFFF));
				ColorDrawable drawable = (ColorDrawable) et.getCompoundDrawables()[2];
				drawable.setColor(color);
			}

			@Override
			public void onCancel(AmbilWarnaDialog dialog) {
			}
		};
		int color = parseInt(et.getText().toString().trim(), 16);
		new AmbilWarnaDialog(this, color | 0xFF000000, colorListener).show();
	}

	private void addResolutionToPresets() {
		String width = tfScreenWidth.getText().toString();
		String height = tfScreenHeight.getText().toString();
		if (width.isEmpty()) width = "-1";
		if (height.isEmpty()) height = "-1";
		int w = parseInt(width);
		int h = parseInt(height);
		if (w <= 0 || h <= 0) {
			Toast.makeText(this, R.string.error, Toast.LENGTH_SHORT).show();
			return;
		}
		String preset = width + " x " + height;
		if (screenPresets.contains(preset)) {
			Toast.makeText(this, R.string.not_saved_exists, Toast.LENGTH_SHORT).show();
			return;
		}

		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
		Set<String> set = preferences.getStringSet("ResolutionsPreset", null);
		if (set == null) {
			set = new HashSet<>(1);
		}
		if (set.add(preset)) {
			preferences.edit().putStringSet("ResolutionsPreset", set).apply();
			screenPresets.add(preset);
			Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show();
		} else {
			Toast.makeText(this, R.string.not_saved_exists, Toast.LENGTH_SHORT).show();
		}
	}

	@Override
	public void onTuneComplete(float[] values) {
		params.shader.values = values;
	}

	private static class ColorTextWatcher implements TextWatcher {
		private final EditText editText;
		private final ColorDrawable drawable;

		ColorTextWatcher(EditText editText) {
			this.editText = editText;
			int size = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 32,
					editText.getResources().getDisplayMetrics());
			ColorDrawable colorDrawable = new ColorDrawable();
			colorDrawable.setBounds(0, 0, size, size);
			editText.setCompoundDrawablesRelative(null, null, colorDrawable, null);
			drawable = colorDrawable;
			editText.setFilters(new InputFilter[]{this::filter});
		}

		private CharSequence filter(CharSequence src, int ss, int se, Spanned dst, int ds, int de) {
			StringBuilder sb = new StringBuilder(se - ss);
			for (int i = ss; i < se; i++) {
				char c = src.charAt(i);
				if ((c >= '0' && c <= '9') || (c >= 'A' && c <= 'F')) {
					sb.append(c);
				} else if (c >= 'a' && c <= 'f') {
					sb.append((char) (c - 32));
				}
			}
			return sb;
		}

		@Override
		public void beforeTextChanged(CharSequence s, int start, int count, int after) {
		}

		@Override
		public void onTextChanged(CharSequence s, int start, int before, int count) {
			if (s.length() > 6) {
				if (start >= 6) editText.getText().delete(6, s.length());
				else {
					int st = start + count;
					int end = st + (before == 0 ? count : before);
					editText.getText().delete(st, Math.min(end, s.length()));
				}
			}
		}

		@Override
		public void afterTextChanged(Editable s) {
			if (s.length() == 0) return;
			try {
				int color = Integer.parseInt(s.toString(), 16);
				drawable.setColor(color | Color.BLACK);
			} catch (NumberFormatException e) {
				drawable.setColor(Color.BLACK);
				s.clear();
			}
		}
	}
}
