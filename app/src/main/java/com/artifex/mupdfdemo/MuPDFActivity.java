package com.artifex.mupdfdemo;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.concurrent.Executor;

import com.androidquery.AQuery;
import com.astuetz.PagerSlidingTabStrip;

import com.com.zreader.database.BookmarkData;
import com.com.zreader.database.DBBookmark;
import com.example.administrator.testecliplse.R;
import com.example.myapplication.PopupView;
import com.zreader.main.BookmarkViewAdapter;

import com.zreader.main.ThumbnailViewAdapter;
import com.zreader.main.searchItemAdapter;
import com.zreader.utils.PreferencesReader;
import com.zreader.utils.ZReaderUtils;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Bitmap.CompressFormat;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.SearchView.OnCloseListener;
import android.support.v7.widget.SearchView.OnQueryTextListener;
import android.support.v7.widget.SearchView.SearchAutoComplete;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.PasswordTransformationMethod;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.Toast;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.ViewAnimator;

class ThreadPerTaskExecutor implements Executor {
	public void execute(Runnable r) {
		new Thread(r).start();
	}
}

public class MuPDFActivity extends ActionBarActivity implements
		FilePicker.FilePickerSupport {
	/* The core rendering instance */
	enum TopBarMode {
		Main, Search, Annot, Delete, More, Accept
	};

	enum AcceptMode {
		Highlight, Underline, StrikeOut, Ink, CopyText
	};

	private final int OUTLINE_REQUEST = 0;
	private final int PRINT_REQUEST = 1;
	private final int FILEPICK_REQUEST = 2;
	private MuPDFCore core;
	private String mFileName;
	private MuPDFReaderView mDocView;
	private View mButtonsView;
	private boolean mButtonsVisible = false;
	private EditText mPasswordView;
	private TextView mFilenameView;
	private SeekBar mPageSlider;
	private int mPageSliderRes;
	private TextView mPageNumberView;
	// private TextView mInfoView;
	private ImageView mOutlineAction;
	private ImageButton mSearchButton;
	private ImageButton mReflowButton;
	private ImageButton mOutlineButton;
	private ImageButton mMoreButton;
	private TextView mAnnotTypeText;
	private ImageButton mAnnotButton;
	private ViewAnimator mTopBarSwitcher;
	private ImageButton mLinkButton;
	private TopBarMode mTopBarMode = TopBarMode.Main;
	private AcceptMode mAcceptMode;
	private ImageButton mSearchBack;
	private ImageButton mSearchFwd;
	private EditText mSearchText;
	private SearchTask mSearchTask;
	private AlertDialog.Builder mAlertBuilder;
	private boolean mLinkHighlight = false;
	private final Handler mHandler = new Handler();
	private boolean mAlertsActive = false;
	private boolean mReflow = false;
	private AsyncTask<Void, Void, MuPDFAlert> mAlertTask;
	private AlertDialog mAlertDialog;
	private FilePicker mFilePicker;

	private static int whiteColor = 0xffffffff;
	private static int blackColor = 0xff000000;
	public static int backGroundPage = whiteColor;
	private String mFilePath;
	public int mCurrentPage;
	private ActionBar actionBar;
	private LinearLayout mLowerMenus;
	public static int disPlayWidth;
	public static int disPlayHeight;
	private ArrayList<OutlineItem> outlineData = new ArrayList<OutlineItem>();
	// private MuPDFPageAdapter muPdfAdapter;
	// private MuPDFReflowAdapter muPDFReflowAdapter;
	public static int dPageMode = MuPDFCore.SINGLE_PAGE_MODE;
	private boolean showCoverPage = false;
	private AsyncTask<Void, Void, Void> getCoverTask;
	private PopupView popupSearchView;
	private String cacheSearch = "";

	public void createAlertWaiter() {
		mAlertsActive = true;
		// All mupdf library calls are performed on asynchronous tasks to avoid
		// stalling
		// the UI. Some calls can lead to javascript-invoked requests to display
		// an
		// alert dialog and collect a reply from the user. The task has to be
		// blocked
		// until the user's reply is received. This method creates an
		// asynchronous task,
		// the purpose of which is to wait of these requests and produce the
		// dialog
		// in response, while leaving the core blocked. When the dialog receives
		// the
		// user's response, it is sent to the core via replyToAlert, unblocking
		// it.
		// Another alert-waiting task is then created to pick up the next alert.
		if (mAlertTask != null) {
			mAlertTask.cancel(true);
			mAlertTask = null;
		}
		if (mAlertDialog != null) {
			mAlertDialog.cancel();
			mAlertDialog = null;
		}
		mAlertTask = new AsyncTask<Void, Void, MuPDFAlert>() {

			@Override
			protected MuPDFAlert doInBackground(Void... arg0) {
				if (!mAlertsActive)
					return null;

				return core.waitForAlert();
			}

			@Override
			protected void onPostExecute(final MuPDFAlert result) {
				// core.waitForAlert may return null when shutting down
				if (result == null)
					return;
				final MuPDFAlert.ButtonPressed pressed[] = new MuPDFAlert.ButtonPressed[3];
				for (int i = 0; i < 3; i++)
					pressed[i] = MuPDFAlert.ButtonPressed.None;
				DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						mAlertDialog = null;
						if (mAlertsActive) {
							int index = 0;
							switch (which) {
								case AlertDialog.BUTTON1:
									index = 0;
									break;
								case AlertDialog.BUTTON2:
									index = 1;
									break;
								case AlertDialog.BUTTON3:
									index = 2;
									break;
							}
							result.buttonPressed = pressed[index];
							// Send the user's response to the core, so that it
							// can
							// continue processing.
							core.replyToAlert(result);
							// Create another alert-waiter to pick up the next
							// alert.
							createAlertWaiter();
						}
					}
				};
				mAlertDialog = mAlertBuilder.create();
				mAlertDialog.setTitle(result.title);
				mAlertDialog.setMessage(result.message);
				switch (result.iconType) {
					case Error:
						break;
					case Warning:
						break;
					case Question:
						break;
					case Status:
						break;
				}
				switch (result.buttonGroupType) {
					case OkCancel:
						mAlertDialog.setButton(AlertDialog.BUTTON2,
								getString(R.string.cancel), listener);
						pressed[1] = MuPDFAlert.ButtonPressed.Cancel;
					case Ok:
						mAlertDialog.setButton(AlertDialog.BUTTON1,
								getString(R.string.okay), listener);
						pressed[0] = MuPDFAlert.ButtonPressed.Ok;
						break;
					case YesNoCancel:
						mAlertDialog.setButton(AlertDialog.BUTTON3,
								getString(R.string.cancel), listener);
						pressed[2] = MuPDFAlert.ButtonPressed.Cancel;
					case YesNo:
						mAlertDialog.setButton(AlertDialog.BUTTON1,
								getString(R.string.yes), listener);
						pressed[0] = MuPDFAlert.ButtonPressed.Yes;
						mAlertDialog.setButton(AlertDialog.BUTTON2,
								getString(R.string.no), listener);
						pressed[1] = MuPDFAlert.ButtonPressed.No;
						break;
				}
				mAlertDialog
						.setOnCancelListener(new DialogInterface.OnCancelListener() {
							public void onCancel(DialogInterface dialog) {
								mAlertDialog = null;
								if (mAlertsActive) {
									result.buttonPressed = MuPDFAlert.ButtonPressed.None;
									core.replyToAlert(result);
									createAlertWaiter();
								}
							}
						});

				mAlertDialog.show();
			}
		};

		mAlertTask.executeOnExecutor(new ThreadPerTaskExecutor());
	}

	public void destroyAlertWaiter() {
		mAlertsActive = false;
		if (mAlertDialog != null) {
			mAlertDialog.cancel();
			mAlertDialog = null;
		}
		if (mAlertTask != null) {
			mAlertTask.cancel(true);
			mAlertTask = null;
		}
	}

	private MuPDFCore openFile(String path) {
		int lastSlashPos = path.lastIndexOf('/');
		mFileName = new String(lastSlashPos == -1 ? path
				: path.substring(lastSlashPos + 1));
		System.out.println("Trying to open " + path);
		try {
			core = new MuPDFCore(this, path);
			// New file: drop the old outline data
			OutlineActivityData.set(null);
		} catch (Exception e) {
			System.out.println(e);
			return null;
		}
		return core;
	}

	private MuPDFCore openBuffer(byte buffer[]) {
		System.out.println("Trying to open byte buffer");
		try {
			core = new MuPDFCore(this, buffer);
			// New file: drop the old outline data
			OutlineActivityData.set(null);
		} catch (Exception e) {
			System.out.println(e);
			return null;
		}
		return core;
	}

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_ACTION_BAR_OVERLAY);
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
				WindowManager.LayoutParams.FLAG_FULLSCREEN);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

		// Get Screen Size
		DisplayMetrics dm = new DisplayMetrics();
		getWindowManager().getDefaultDisplay().getMetrics(dm);
		double x = Math.pow(dm.widthPixels / dm.xdpi, 2);
		double y = Math.pow(dm.heightPixels / dm.ydpi, 2);
		float screenSize = (float) Math.sqrt(x + y);
		disPlayWidth = dm.widthPixels;
		disPlayHeight = dm.heightPixels;

		actionBar = getSupportActionBar();
		actionBar.setDisplayShowCustomEnabled(false);
		actionBar.setDisplayHomeAsUpEnabled(true);
		actionBar.setIcon(R.drawable.ic_launcher2);
		// actionBar.setDisplayShowHomeEnabled(false);
		// actionBar.setHomeButtonEnabled(true);

		mAlertBuilder = new AlertDialog.Builder(this);

		if (core == null) {
			core = (MuPDFCore) getLastCustomNonConfigurationInstance();

			if (savedInstanceState != null) {
				if (savedInstanceState.containsKey("FileName"))
					mFileName = savedInstanceState.getString("FileName");
				if (savedInstanceState.containsKey("FilePath"))
					mFilePath = savedInstanceState.getString("FilePath");
			}

		}
		if (core == null) {
			Intent intent = getIntent();
			byte buffer[] = null;
			if (Intent.ACTION_VIEW.equals(intent.getAction())) {
				Uri uri = intent.getData();
				if (uri.toString().startsWith("content://")) {
					// Handle view requests from the Transformer Prime's file
					// manager
					// Hopefully other file managers will use this same scheme,
					// if not
					// using explicit paths.
					Cursor cursor = getContentResolver().query(uri,
							new String[] { "_data" }, null, null, null);
					if (cursor.moveToFirst()) {
						String str = cursor.getString(0);
						String reason = null;
						if (str == null) {
							try {
								InputStream is = getContentResolver()
										.openInputStream(uri);
								int len = is.available();
								buffer = new byte[len];
								is.read(buffer, 0, len);
								is.close();
							} catch (java.lang.OutOfMemoryError e) {
								System.out
										.println("Out of memory during buffer reading");
								reason = e.toString();
							} catch (Exception e) {
								reason = e.toString();
							}
							if (reason != null) {
								buffer = null;
								Resources res = getResources();
								AlertDialog alert = mAlertBuilder.create();
								setTitle(String
										.format(res
														.getString(R.string.cannot_open_document_Reason),
												reason));
								alert.setButton(AlertDialog.BUTTON_POSITIVE,
										getString(R.string.dismiss),
										new DialogInterface.OnClickListener() {
											public void onClick(
													DialogInterface dialog,
													int which) {
												finish();
											}
										});
								alert.show();
								return;
							}
						} else {
							uri = Uri.parse(str);
						}
					}
				}

				mFilePath = Uri.decode(uri.getEncodedPath());
				if (buffer != null) {
					core = openBuffer(buffer);
				} else {
					core = openFile(mFilePath);
				}
				SearchTaskResult.set(null);
			}
			if (core != null && core.needsPassword()) {
				requestPassword(savedInstanceState);
				return;
			}
			if (core != null && core.countDisplayPage() == 0) {
				core = null;
			}
		}
		if (core == null) {
			AlertDialog alert = mAlertBuilder.create();
			alert.setTitle(R.string.cannot_open_document);
			alert.setButton(AlertDialog.BUTTON_POSITIVE,
					getString(R.string.dismiss),
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							finish();
						}
					});
			alert.show();
			return;
		}

		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
		createUI(savedInstanceState);

		PackageManager m = getPackageManager();
		String s = getPackageName();
		try {
			PackageInfo p = m.getPackageInfo(s, 0);
			s = p.applicationInfo.dataDir;
		} catch (NameNotFoundException e) {
			Log.w("yourtag", "Error Package name not found ", e);
		}
	}

	public void requestPassword(final Bundle savedInstanceState) {
		mPasswordView = new EditText(this);
		mPasswordView.setInputType(EditorInfo.TYPE_TEXT_VARIATION_PASSWORD);
		mPasswordView
				.setTransformationMethod(new PasswordTransformationMethod());

		AlertDialog alert = mAlertBuilder.create();
		alert.setTitle(R.string.enter_password);
		alert.setView(mPasswordView);
		alert.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.okay),
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						if (core.authenticatePassword(mPasswordView.getText()
								.toString())) {
							createUI(savedInstanceState);
						} else {
							requestPassword(savedInstanceState);
						}
					}
				});
		alert.setButton(AlertDialog.BUTTON_NEGATIVE,
				getString(R.string.cancel),
				new DialogInterface.OnClickListener() {

					public void onClick(DialogInterface dialog, int which) {
						finish();
					}
				});
		alert.show();
	}

	public String getPageNumberPerAll(int index) {
		String pageNum = "";
		if (core.getDoubleMode()) {
			if (core.getCoverPageMode()) {
				if (index == 0) { // first page
					pageNum = String.format("%d / %d", (index * 2) + 1,
							core.countDocumentPages());
				} else if (index * 2 == core.countDocumentPages()) { // last
					// single
					// page
					pageNum = String.format("%d / %d", index * 2,
							core.countDocumentPages());
				} else
					// double page
					pageNum = String.format("%d-%d / %d", index * 2,
							(index * 2) + 1, core.countDocumentPages());
			} else {
				if ((index * 2) + 1 == core.countDocumentPages()) { // last
					// single
					// page
					pageNum = String.format("%d / %d", (index * 2) + 1,
							core.countDocumentPages());
				} else
					pageNum = String.format("%d-%d / %d", (index * 2) + 1,
							(index * 2) + 2, core.countDocumentPages());
			}

		}
		if (!core.getDoubleMode() || mReflow) {
			pageNum = String.format("%d / %d", index + 1,
					core.countDocumentPages());
		}
		return pageNum;
	}

	public void createUI(Bundle savedInstanceState) {
		if (core == null)
			return;
		if (mFileName != null)
			actionBar.setTitle(mFileName);
		// Now create the UI.
		// First create the document view
		mDocView = new MuPDFReaderView(this) {
			@Override
			protected void onMoveToChild(int i) {
				if (core == null)
					return;
				mCurrentPage = i;

				mPageNumberView.setText(getPageNumberPerAll(i));
				// mPageNumberView.setText(String.format("%d / %d", i + 1,
				// core.countDisplayPage()));
				mPageSlider.setMax((core.countDisplayPage() - 1)
						* mPageSliderRes);
				mPageSlider.setProgress(i * mPageSliderRes);
				supportInvalidateOptionsMenu();
				super.onMoveToChild(i);
			}

			@Override
			protected void onTapMainDocArea() {
				if (!mButtonsVisible) {
					showButtons();
				} else {
					if (mTopBarMode == TopBarMode.Main)
						hideButtons();
				}
			}

			@Override
			protected void onDocMotion() {
				hideButtons();
			}

			@Override
			protected void onHit(Hit item) {
				switch (mTopBarMode) {
					case Annot:
						if (item == Hit.Annotation) {
							showButtons();
							mTopBarMode = TopBarMode.Delete;
							mTopBarSwitcher
									.setDisplayedChild(mTopBarMode.ordinal());
						}
						break;
					case Delete:
						mTopBarMode = TopBarMode.Annot;
						mTopBarSwitcher.setDisplayedChild(mTopBarMode.ordinal());
						// fall through
					default:
						// Not in annotation editing mode, but the pageview will
						// still select and highlight hit annotations, so
						// deselect just in case.
						MuPDFView pageView = (MuPDFView) mDocView
								.getDisplayedView();
						if (pageView != null)
							pageView.deselectAnnotation();
						break;
				}
			}
		};

		int theme = PreferencesReader.getThemeMode(MuPDFActivity.this);
		core.setThemeMode(theme);
		if (theme == MuPDFCore.PAPER_NORMAL) {
			backGroundPage = whiteColor;
		} else
			backGroundPage = blackColor;
		dPageMode = PreferencesReader.getPageMode(this);
		switch (dPageMode) {
			case MuPDFCore.SINGLE_PAGE_MODE:
				core.setDoubleMode(false);
				break;
			case MuPDFCore.DOUBLE_PAGE_MODE:
				core.setDoubleMode(true);
				break;
			case MuPDFCore.AUTO_PAGE_MODE:
				int currentOrientation = getResources().getConfiguration().orientation;
				if (currentOrientation == Configuration.ORIENTATION_LANDSCAPE) {
					core.setDoubleMode(true);
				} else {
					core.setDoubleMode(false);
				}
				break;

			default:
				break;
		}
		showCoverPage = PreferencesReader.isShowCoverPageMode(this);
		core.setCoverPageMode(showCoverPage);
		setLinkHighlight(true);

		// muPdfAdapter = new MuPDFPageAdapter(this, this, core);
		mDocView.setAdapter(new MuPDFPageAdapter(this, this, core));
		mSearchTask = new SearchTask(this, core) {

			@Override
			protected void onTextFound(SearchTaskResult result) {
				// TODO Auto-generated method stub

			}

			@Override
			protected void onTextFounds(final ArrayList<SearchTaskResult> result) {
				if (popupSearchView != null) {
					ViewGroup view = popupSearchView.getRootView();
					AQuery aqs = new AQuery(view);
					aqs.id(R.id.progressBarSearch).gone();
					aqs.id(R.id.searchDetail).text(result.size() + " Pages");

					// ArrayList<String> values = new ArrayList<String>();
					// for(SearchTaskResult res : result){
					// values.add("Page "+(res.pageNumber+1));
					// }
					// ArrayAdapter<String> adapter = new
					// ArrayAdapter<String>(MuPDFActivity.this,
					// android.R.layout.simple_list_item_1, android.R.id.text1,
					// values);
					searchItemAdapter adapter = new searchItemAdapter(
							MuPDFActivity.this, core, result);

					aqs.id(R.id.searchList).adapter(adapter)
							.itemClicked(new OnItemClickListener() {

								@Override
								public void onItemClick(AdapterView<?> arg0,
														View arg1, int index, long arg3) {
									SearchTaskResult.set(result.get(index));
									mDocView.setDisplayedViewIndex(getDisplayPage(result
											.get(index).pageNumber));
									mDocView.resetupChildren();
									popupSearchView.dismiss();
								}
							});
				}
			}
		};

		// Make the buttons overlay, and store all its
		// controls in variables
		makeButtonsView();

		// Set up the page slider
		int smax = Math.max(core.countDisplayPage() - 1, 1);
		mPageSliderRes = ((10 + smax - 1) / smax) * 2;

		// Set the file-name text
		mFilenameView.setText(mFileName);

		// Activate the seekbar
		mPageSlider
				.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
					public void onStopTrackingTouch(SeekBar seekBar) {
						mDocView.setDisplayedViewIndex((seekBar.getProgress() + mPageSliderRes / 2)
								/ mPageSliderRes);
					}

					public void onStartTrackingTouch(SeekBar seekBar) {
					}

					public void onProgressChanged(SeekBar seekBar,
												  int progress, boolean fromUser) {
						updatePageNumView((progress + mPageSliderRes / 2)
								/ mPageSliderRes);
					}
				});

		// Activate the search-preparing button
		mSearchButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				searchModeOn();
			}
		});

		// Activate the reflow button
		mReflowButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				toggleReflow();
			}
		});

		if (core.fileFormat().startsWith("PDF")) {
			mAnnotButton.setOnClickListener(new View.OnClickListener() {
				public void onClick(View v) {
					mTopBarMode = TopBarMode.Annot;
					mTopBarSwitcher.setDisplayedChild(mTopBarMode.ordinal());
				}
			});
		} else {
			mAnnotButton.setVisibility(View.GONE);
		}

		// Search invoking buttons are disabled while there is no text specified
		mSearchBack.setEnabled(false);
		mSearchFwd.setEnabled(false);
		mSearchBack.setColorFilter(Color.argb(255, 128, 128, 128));
		mSearchFwd.setColorFilter(Color.argb(255, 128, 128, 128));

		// React to interaction with the text widget
		mSearchText.addTextChangedListener(new TextWatcher() {

			public void afterTextChanged(Editable s) {
				boolean haveText = s.toString().length() > 0;
				setButtonEnabled(mSearchBack, haveText);
				setButtonEnabled(mSearchFwd, haveText);

				// Remove any previous search results
				if (SearchTaskResult.get() != null
						&& !mSearchText.getText().toString()
						.equals(SearchTaskResult.get().txt)) {
					SearchTaskResult.set(null);
					mDocView.resetupChildren();
				}
			}

			public void beforeTextChanged(CharSequence s, int start, int count,
										  int after) {
			}

			public void onTextChanged(CharSequence s, int start, int before,
									  int count) {
			}
		});

		// React to Done button on keyboard
		mSearchText
				.setOnEditorActionListener(new TextView.OnEditorActionListener() {
					public boolean onEditorAction(TextView v, int actionId,
												  KeyEvent event) {
						if (actionId == EditorInfo.IME_ACTION_DONE)
							search(1);
						return false;
					}
				});

		mSearchText.setOnKeyListener(new View.OnKeyListener() {
			public boolean onKey(View v, int keyCode, KeyEvent event) {
				if (event.getAction() == KeyEvent.ACTION_DOWN
						&& keyCode == KeyEvent.KEYCODE_ENTER)
					search(1);
				return false;
			}
		});

		// Activate search invoking buttons
		mSearchBack.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				search(-1);
			}
		});
		mSearchFwd.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				search(1);
			}
		});

		mLinkButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				setLinkHighlight(!mLinkHighlight);
			}
		});

		if (core.hasOutline()) {
			mOutlineButton.setOnClickListener(new View.OnClickListener() {
				public void onClick(View v) {
					OutlineItem outline[] = core.getOutline();
					if (outline != null) {
						OutlineActivityData.get().items = outline;
						Intent intent = new Intent(MuPDFActivity.this,
								OutlineActivity.class);
						startActivityForResult(intent, OUTLINE_REQUEST);
					}
				}
			});
		} else {
			mOutlineButton.setVisibility(View.GONE);
		}

		mOutlineAction.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View arg0) {
				showFullScreenDialog(0);
			}
		});

		mPageNumberView.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View arg0) {
				showFullScreenDialog(2);
			}
		});

		// Reenstate last state if it was recorded
		// SharedPreferences prefs = getPreferences(Context.MODE_PRIVATE);
		// mCurrentPage = core.getDisplayPage(prefs.getInt("page"+mFilePath,
		// 0));
		// mDocView.setDisplayedViewIndex(mCurrentPage);

		// if (savedInstanceState == null ||
		// !savedInstanceState.getBoolean("ButtonsHidden", false))
		// showButtons();

		// if (savedInstanceState != null) {
		// if (!savedInstanceState.getBoolean("ButtonsHidden", false)) {
		// mButtonsVisible = false;
		// showButtons();
		// }
		// }else {
		// mButtonsVisible = false;
		// // showButtons();
		// }
		mButtonsVisible = true;
		hideButtons();

		if (savedInstanceState != null
				&& savedInstanceState.getBoolean("SearchMode", false))
			searchModeOn();

		boolean cacheReflow;
		if (savedInstanceState != null) {
			cacheReflow = savedInstanceState.getBoolean("ReflowMode", false);
		} else
			cacheReflow = PreferencesReader.isReflow(this);

		SharedPreferences prefs = getPreferences(Context.MODE_PRIVATE);
		int cachePage = prefs.getInt("page" + mFilePath, 0);
		mCurrentPage = cacheReflow ? cachePage : getDisplayPage(cachePage);
		reflowModeSet(cacheReflow);

		// ADD CODE//
		ReadBookmark();

		// Read outline
		OutlineItem outline[] = core.getOutline();
		outlineData.clear();
		if (outline != null)
			outlineData.addAll(Arrays.asList(outline));

		// Stick the document view and the buttons overlay into a parent view
		RelativeLayout layout = new RelativeLayout(this);
		layout.setBackgroundColor(0xff222222);
		layout.addView(mDocView);
		layout.addView(mButtonsView);
		setContentView(layout);

		// getCover
		getCoverTask = new AsyncTask<Void, Void, Void>() {

			@Override
			protected Void doInBackground(Void... params) {
				getCoverThumbnail();
				return null;
			}
		};
		getCoverTask.execute();
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch (requestCode) {
			case OUTLINE_REQUEST:
				if (resultCode >= 0)
					mDocView.setDisplayedViewIndex(resultCode);
				break;
			case PRINT_REQUEST:
				if (resultCode == RESULT_CANCELED)
					showInfo(getString(R.string.print_failed));
				break;
			case FILEPICK_REQUEST:
				if (mFilePicker != null && resultCode == RESULT_OK)
					mFilePicker.onPick(data.getData());
		}
		super.onActivityResult(requestCode, resultCode, data);
	}

	public Object onRetainCustomNonConfigurationInstance() {
		MuPDFCore mycore = core;
		core = null;
		return mycore;
	}

	private void reflowModeSet(boolean reflow) {
		mReflow = reflow;
		PreferencesReader.saveReflowMode(this, mReflow);
		core.setReflow(mReflow);
		int currentSinglePage = core.getDocumentPage(mCurrentPage);

		if (mReflow) {
			core.setDoubleMode(false);
			core.setCoverPageMode(true);
		} else {
			core.setCoverPageMode(showCoverPage);
			switch (dPageMode) {
				case MuPDFCore.SINGLE_PAGE_MODE:
					core.setDoubleMode(false);
					break;
				case MuPDFCore.DOUBLE_PAGE_MODE:
					core.setDoubleMode(true);
					break;
				case MuPDFCore.AUTO_PAGE_MODE:
					int currentOrientation = getResources().getConfiguration().orientation;
					if (currentOrientation == Configuration.ORIENTATION_LANDSCAPE) {
						core.setDoubleMode(true);
					} else {
						core.setDoubleMode(false);
					}
					break;

				default:
					break;
			}
		}

		mDocView.setAdapter(mReflow ? new MuPDFReflowAdapter(this, core)
				: /* muPdfAdapter */new MuPDFPageAdapter(this, this, core));
		if (reflow)
			setLinkHighlight(false);
		mDocView.refresh(mReflow);

		if (!mReflow)
			mCurrentPage = getDisplayPage(currentSinglePage);
		mDocView.setDisplayedViewIndex(mCurrentPage);

		hideButtons();
	}

	private void toggleReflow() {
		if (!mReflow) {
			mCurrentPage = core.getDocumentPage(mCurrentPage);
		} else {

		}
		reflowModeSet(!mReflow);
		showInfo(mReflow ? getString(R.string.entering_reflow_mode)
				: getString(R.string.leaving_reflow_mode));
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);

		if (mFileName != null && mDocView != null) {
			outState.putString("FileName", mFileName);
			if (mFilePath != null)
				outState.putString("FilePath", mFilePath);
			SharedPreferences prefs = getPreferences(Context.MODE_PRIVATE);
			SharedPreferences.Editor edit = prefs.edit();

			int savePage = mReflow ? mCurrentPage : core
					.getDocumentPage(mCurrentPage);
			edit.putInt("page" + mFilePath, savePage);
			edit.commit();
		}

		// if (!mButtonsVisible)
		// outState.putBoolean("ButtonsHidden", true);

		if (mTopBarMode == TopBarMode.Search)
			outState.putBoolean("SearchMode", true);

		if (mReflow)
			outState.putBoolean("ReflowMode", true);

		// PreferencesReader.savePageMode(this, dPageMode);
		// PreferencesReader.saveShowCoverPageMode(this, showCoverPage);
	}

	@Override
	protected void onPause() {
		super.onPause();

		if (mSearchTask != null)
			mSearchTask.stop();

		if (mFileName != null && mDocView != null) {
			SharedPreferences prefs = getPreferences(Context.MODE_PRIVATE);
			SharedPreferences.Editor edit = prefs.edit();
			edit.putInt("page" + mFilePath, mDocView.getDisplayedViewIndex());
			edit.commit();
		}
	}

	public void onDestroy() {
		if (mDocView != null) {
			mDocView.applyToChildren(new ReaderView.ViewMapper() {
				void applyToView(View view) {
					((MuPDFView) view).releaseBitmaps();
				}
			});
		}
		if (core != null)
			core.onDestroy();
		if (mAlertTask != null) {
			mAlertTask.cancel(true);
			mAlertTask = null;
		}
		if (getCoverTask != null) {
			getCoverTask.cancel(true);
			getCoverTask = null;
		}
		core = null;
		super.onDestroy();
	}

	private void setButtonEnabled(ImageButton button, boolean enabled) {
		button.setEnabled(enabled);
		button.setColorFilter(enabled ? Color.argb(255, 255, 255, 255) : Color
				.argb(255, 128, 128, 128));
	}

	private void setLinkHighlight(boolean highlight) {
		mLinkHighlight = highlight;
		// LINK_COLOR tint
		// mLinkButton.setColorFilter(highlight ? Color.argb(0xFF, 172, 114, 37)
		// : Color.argb(0xFF, 255, 255, 255));
		// Inform pages of the change.
		mDocView.setLinksEnabled(highlight);
	}

	private void showButtons() {
		if (core == null)
			return;
		if (!mButtonsVisible) {
			mButtonsVisible = true;
			// Update page number text and slider
			int index = mDocView.getDisplayedViewIndex();
			updatePageNumView(index);
			mPageSlider.setMax((core.countDisplayPage() - 1) * mPageSliderRes);
			mPageSlider.setProgress(index * mPageSliderRes);
			if (mTopBarMode == TopBarMode.Search) {
				mSearchText.requestFocus();
				showKeyboard();
			}

			// Animation anim = new TranslateAnimation(0, 0,
			// -mTopBarSwitcher.getHeight(), 0);
			// anim.setDuration(200);
			// anim.setAnimationListener(new Animation.AnimationListener() {
			// public void onAnimationStart(Animation animation) {
			// mTopBarSwitcher.setVisibility(View.VISIBLE);
			// }
			// public void onAnimationRepeat(Animation animation) {}
			// public void onAnimationEnd(Animation animation) {}
			// });
			// mTopBarSwitcher.startAnimation(anim);

			Animation anim = new TranslateAnimation(0, 0,
					mLowerMenus.getHeight(), 0);
			anim.setDuration(200);
			anim.setAnimationListener(new Animation.AnimationListener() {
				public void onAnimationStart(Animation animation) {
					mLowerMenus.setVisibility(View.VISIBLE);
				}

				public void onAnimationRepeat(Animation animation) {
				}

				public void onAnimationEnd(Animation animation) {
				}
			});
			mLowerMenus.startAnimation(anim);

			actionBar.show();
			supportInvalidateOptionsMenu();
		}
	}

	private void hideButtons() {
		if (mButtonsVisible) {
			mButtonsVisible = false;
			hideKeyboard();

			// Animation anim = new TranslateAnimation(0, 0, 0,
			// -mTopBarSwitcher.getHeight());
			// anim.setDuration(200);
			// anim.setAnimationListener(new Animation.AnimationListener() {
			// public void onAnimationStart(Animation animation) {}
			// public void onAnimationRepeat(Animation animation) {}
			// public void onAnimationEnd(Animation animation) {
			// mTopBarSwitcher.setVisibility(View.INVISIBLE);
			// }
			// });
			// mTopBarSwitcher.startAnimation(anim);

			Animation anim = new TranslateAnimation(0, 0, 0,
					mLowerMenus.getHeight());
			anim.setDuration(200);
			anim.setAnimationListener(new Animation.AnimationListener() {
				public void onAnimationStart(Animation animation) {
				}

				public void onAnimationRepeat(Animation animation) {
				}

				public void onAnimationEnd(Animation animation) {
					mLowerMenus.setVisibility(View.INVISIBLE);
				}
			});
			mLowerMenus.startAnimation(anim);

			actionBar.hide();
		}
	}

	private void searchModeOn() {
		if (mTopBarMode != TopBarMode.Search) {
			mTopBarMode = TopBarMode.Search;
			// Focus on EditTextWidget
			mSearchText.requestFocus();
			showKeyboard();
			mTopBarSwitcher.setDisplayedChild(mTopBarMode.ordinal());
		}
	}

	private void searchModeOff() {
		if (mTopBarMode == TopBarMode.Search) {
			mTopBarMode = TopBarMode.Main;
			hideKeyboard();
			mTopBarSwitcher.setDisplayedChild(mTopBarMode.ordinal());
			SearchTaskResult.set(null);
			// Make the ReaderView act on the change to mSearchTaskResult
			// via overridden onChildSetup method.
			mDocView.resetupChildren();
		}
	}

	private void updatePageNumView(int index) {
		if (core == null)
			return;
		mPageNumberView.setText(getPageNumberPerAll(index));
		// mPageNumberView.setText(String.format("%d / %d", index+1,
		// core.countDisplayPage()));
	}

	private void printDoc() {
		if (!core.fileFormat().startsWith("PDF")) {
			showInfo(getString(R.string.format_currently_not_supported));
			return;
		}

		Intent myIntent = getIntent();
		Uri docUri = myIntent != null ? myIntent.getData() : null;

		if (docUri == null) {
			showInfo(getString(R.string.print_failed));
		}

		if (docUri.getScheme() == null)
			docUri = Uri.parse("file://" + docUri.toString());

		Intent printIntent = new Intent(this, PrintDialogActivity.class);
		printIntent.setDataAndType(docUri, "aplication/pdf");
		printIntent.putExtra("title", mFileName);
		startActivityForResult(printIntent, PRINT_REQUEST);
	}

	private void showInfo(String message) {
		Toast.makeText(this, message, Toast.LENGTH_SHORT).show();

		// mInfoView.setText(message);
		//
		// int currentApiVersion = android.os.Build.VERSION.SDK_INT;
		// if (currentApiVersion >= android.os.Build.VERSION_CODES.HONEYCOMB) {
		// SafeAnimatorInflater safe = new SafeAnimatorInflater((Activity)this,
		// R.animator.info, (View)mInfoView);
		// } else {
		// mInfoView.setVisibility(View.VISIBLE);
		// mHandler.postDelayed(new Runnable() {
		// public void run() {
		// mInfoView.setVisibility(View.INVISIBLE);
		// }
		// }, 500);
		// }
	}

	private void makeButtonsView() {
		mButtonsView = getLayoutInflater().inflate(R.layout.buttons, null);
		mFilenameView = (TextView) mButtonsView.findViewById(R.id.docNameText);
		mPageSlider = (SeekBar) mButtonsView.findViewById(R.id.pageSlider);
		mPageNumberView = (TextView) mButtonsView.findViewById(R.id.pageNumber);
		// mInfoView = (TextView)mButtonsView.findViewById(R.id.info);
		mSearchButton = (ImageButton) mButtonsView
				.findViewById(R.id.searchButton);
		mReflowButton = (ImageButton) mButtonsView
				.findViewById(R.id.reflowButton);
		mOutlineButton = (ImageButton) mButtonsView
				.findViewById(R.id.outlineButton);
		mAnnotButton = (ImageButton) mButtonsView
				.findViewById(R.id.editAnnotButton);
		mAnnotTypeText = (TextView) mButtonsView.findViewById(R.id.annotType);
		mTopBarSwitcher = (ViewAnimator) mButtonsView
				.findViewById(R.id.switcher);
		mSearchBack = (ImageButton) mButtonsView.findViewById(R.id.searchBack);
		mSearchFwd = (ImageButton) mButtonsView
				.findViewById(R.id.searchForward);
		mSearchText = (EditText) mButtonsView.findViewById(R.id.searchText);
		mLinkButton = (ImageButton) mButtonsView.findViewById(R.id.linkButton);
		mMoreButton = (ImageButton) mButtonsView.findViewById(R.id.moreButton);
		mLowerMenus = (LinearLayout) mButtonsView.findViewById(R.id.lowerMenus);
		// mInfoView.setVisibility(View.INVISIBLE);

		mOutlineAction = (ImageView) mButtonsView
				.findViewById(R.id.showOutlineAction);

	}

	public void OnMoreButtonClick(View v) {
		mTopBarMode = TopBarMode.More;
		mTopBarSwitcher.setDisplayedChild(mTopBarMode.ordinal());
	}

	public void OnCancelMoreButtonClick(View v) {
		mTopBarMode = TopBarMode.Main;
		mTopBarSwitcher.setDisplayedChild(mTopBarMode.ordinal());
	}

	public void OnPrintButtonClick(View v) {
		printDoc();
	}

	public void OnCopyTextButtonClick(View v) {
		mTopBarMode = TopBarMode.Accept;
		mTopBarSwitcher.setDisplayedChild(mTopBarMode.ordinal());
		mAcceptMode = AcceptMode.CopyText;
		mDocView.setMode(MuPDFReaderView.Mode.Selecting);
		mAnnotTypeText.setText(getString(R.string.copy_text));
		showInfo(getString(R.string.select_text));
	}

	public void OnEditAnnotButtonClick(View v) {
		mTopBarMode = TopBarMode.Annot;
		mTopBarSwitcher.setDisplayedChild(mTopBarMode.ordinal());
	}

	public void OnCancelAnnotButtonClick(View v) {
		mTopBarMode = TopBarMode.More;
		mTopBarSwitcher.setDisplayedChild(mTopBarMode.ordinal());
	}

	public void OnHighlightButtonClick(View v) {
		mTopBarMode = TopBarMode.Accept;
		mTopBarSwitcher.setDisplayedChild(mTopBarMode.ordinal());
		mAcceptMode = AcceptMode.Highlight;
		mDocView.setMode(MuPDFReaderView.Mode.Selecting);
		mAnnotTypeText.setText(R.string.highlight);
		showInfo(getString(R.string.select_text));
	}

	public void OnUnderlineButtonClick(View v) {
		mTopBarMode = TopBarMode.Accept;
		mTopBarSwitcher.setDisplayedChild(mTopBarMode.ordinal());
		mAcceptMode = AcceptMode.Underline;
		mDocView.setMode(MuPDFReaderView.Mode.Selecting);
		mAnnotTypeText.setText(R.string.underline);
		showInfo(getString(R.string.select_text));
	}

	public void OnStrikeOutButtonClick(View v) {
		mTopBarMode = TopBarMode.Accept;
		mTopBarSwitcher.setDisplayedChild(mTopBarMode.ordinal());
		mAcceptMode = AcceptMode.StrikeOut;
		mDocView.setMode(MuPDFReaderView.Mode.Selecting);
		mAnnotTypeText.setText(R.string.strike_out);
		showInfo(getString(R.string.select_text));
	}

	public void OnInkButtonClick(View v) {
		mTopBarMode = TopBarMode.Accept;
		mTopBarSwitcher.setDisplayedChild(mTopBarMode.ordinal());
		mAcceptMode = AcceptMode.Ink;
		mDocView.setMode(MuPDFReaderView.Mode.Drawing);
		mAnnotTypeText.setText(R.string.ink);
		showInfo(getString(R.string.draw_annotation));
	}

	public void OnCancelAcceptButtonClick(View v) {
		MuPDFView pageView = (MuPDFView) mDocView.getDisplayedView();
		if (pageView != null) {
			pageView.deselectText();
			pageView.cancelDraw();
		}
		mDocView.setMode(MuPDFReaderView.Mode.Viewing);
		switch (mAcceptMode) {
			case CopyText:
				mTopBarMode = TopBarMode.More;
				break;
			default:
				mTopBarMode = TopBarMode.Annot;
				break;
		}
		mTopBarSwitcher.setDisplayedChild(mTopBarMode.ordinal());
	}

	public void OnAcceptButtonClick(View v) {
		MuPDFView pageView = (MuPDFView) mDocView.getDisplayedView();
		boolean success = false;
		switch (mAcceptMode) {
			case CopyText:
				if (pageView != null)
					success = pageView.copySelection();
				mTopBarMode = TopBarMode.More;
				showInfo(success ? getString(R.string.copied_to_clipboard)
						: getString(R.string.no_text_selected));
				break;

			case Highlight:
				if (pageView != null)
					success = pageView.markupSelection(Annotation.Type.HIGHLIGHT);
				mTopBarMode = TopBarMode.Annot;
				if (!success)
					showInfo(getString(R.string.no_text_selected));
				break;

			case Underline:
				if (pageView != null)
					success = pageView.markupSelection(Annotation.Type.UNDERLINE);
				mTopBarMode = TopBarMode.Annot;
				if (!success)
					showInfo(getString(R.string.no_text_selected));
				break;

			case StrikeOut:
				if (pageView != null)
					success = pageView.markupSelection(Annotation.Type.STRIKEOUT);
				mTopBarMode = TopBarMode.Annot;
				if (!success)
					showInfo(getString(R.string.no_text_selected));
				break;

			case Ink:
				if (pageView != null)
					success = pageView.saveDraw();
				mTopBarMode = TopBarMode.Annot;
				if (!success)
					showInfo(getString(R.string.nothing_to_save));
				break;
		}
		mTopBarSwitcher.setDisplayedChild(mTopBarMode.ordinal());
		mDocView.setMode(MuPDFReaderView.Mode.Viewing);
	}

	public void OnCancelSearchButtonClick(View v) {
		searchModeOff();
	}

	public void OnDeleteButtonClick(View v) {
		MuPDFView pageView = (MuPDFView) mDocView.getDisplayedView();
		if (pageView != null)
			pageView.deleteSelectedAnnotation();
		mTopBarMode = TopBarMode.Annot;
		mTopBarSwitcher.setDisplayedChild(mTopBarMode.ordinal());
	}

	public void OnCancelDeleteButtonClick(View v) {
		MuPDFView pageView = (MuPDFView) mDocView.getDisplayedView();
		if (pageView != null)
			pageView.deselectAnnotation();
		mTopBarMode = TopBarMode.Annot;
		mTopBarSwitcher.setDisplayedChild(mTopBarMode.ordinal());
	}

	private void showKeyboard() {
		InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
		if (imm != null)
			imm.showSoftInput(mSearchText, 0);
	}

	private void hideKeyboard() {
		InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
		if (imm != null)
			imm.hideSoftInputFromWindow(mSearchText.getWindowToken(), 0);
	}

	private void search(int direction) {
		hideKeyboard();
		int displayPage = mDocView.getDisplayedViewIndex();
		SearchTaskResult r = SearchTaskResult.get();
		int searchPage = r != null ? r.pageNumber : -1;
		mSearchTask.go(mSearchText.getText().toString(), direction,
				displayPage, searchPage);
	}

	@Override
	public boolean onSearchRequested() {
		if (mButtonsVisible && mTopBarMode == TopBarMode.Search) {
			hideButtons();
		} else {
			showButtons();
			searchModeOn();
		}
		return super.onSearchRequested();
	}

	@Override
	protected void onStart() {
		if (core != null) {
			core.startAlerts();
			createAlertWaiter();
		}

		super.onStart();
	}

	@Override
	protected void onStop() {
		if (core != null) {
			destroyAlertWaiter();
			core.stopAlerts();
		}

		super.onStop();
	}

	@Override
	public void onBackPressed() {
		if (core.hasChanges()) {
			DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					if (which == AlertDialog.BUTTON_POSITIVE)
						core.save();

					finish();
				}
			};
			AlertDialog alert = mAlertBuilder.create();
			alert.setTitle("MuPDF");
			alert.setMessage(getString(R.string.document_has_changes_save_them_));
			alert.setButton(AlertDialog.BUTTON_POSITIVE,
					getString(R.string.yes), listener);
			alert.setButton(AlertDialog.BUTTON_NEGATIVE,
					getString(R.string.no), listener);
			alert.show();
		} else {
			super.onBackPressed();
		}
	}

	@Override
	public void performPickFor(FilePicker picker) {
		mFilePicker = picker;
		Intent intent = new Intent(this, ChoosePDFActivity.class);
		intent.setAction(ChoosePDFActivity.PICK_KEY_FILE);
		startActivityForResult(intent, FILEPICK_REQUEST);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.reader_menu, menu);
		//MenuItem searchItem = menu.findItem(R.id.mu_reader_search);

//		final SearchView searchView = (SearchView) MenuItemCompat
//				.getActionView(searchItem);
//		searchView.setQueryHint("Search text");

//		SearchAutoComplete searchAutoComplete = (SearchAutoComplete) searchView
//				.findViewById(android.support.v7.appcompat.R.id.search_src_text);
//		searchAutoComplete.setTextColor(getResources().getColor(
//				R.color.search_text_color));
//		searchAutoComplete.setHintTextColor(getResources().getColor(
//				R.color.search_text_hint_color));
//		ImageView closeSearch = (ImageView) searchView
//				.findViewById(android.support.v7.appcompat.R.id.search_close_btn);
//		closeSearch.setImageResource(R.drawable.ic_action_content_remove);
		// ImageView sSearch = (ImageView)
		// searchView.findViewById(android.support.v7.appcompat.R.id.search_mag_icon);
		// sSearch.setAdjustViewBounds(true);
		// sSearch.setMaxWidth(0);
		// sSearch.setLayoutParams(new
		// LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT,
		// LayoutParams.WRAP_CONTENT));
		// sSearch.setImageDrawable(null);

//		if (!cacheSearch.equals(""))
//			searchView.setQuery(cacheSearch, false);
//		searchView.setOnQueryTextListener(new OnQueryTextListener() {
//
//			@Override
//			public boolean onQueryTextSubmit(String query) {
//				if (query.length() >= 3) {
//					mSearchTask.searchAll(query);
//					LayoutInflater layoutInflater = (LayoutInflater) getBaseContext()
//							.getSystemService(LAYOUT_INFLATER_SERVICE);
//					View popupView = layoutInflater.inflate(
//							R.layout.popup_search, null);
//					if (popupSearchView != null) {
//						popupSearchView.dismiss();
//						popupSearchView = null;
//					}
//					popupSearchView = new PopupView(getApplicationContext(),
//							popupView);
//					int[] location = new int[2];
//					searchView.getLocationOnScreen(location);
//					popupSearchView.show(searchView, location[0],
//							getActionBar().getHeight(), searchView.getWidth());
//					popupSearchView
//							.setOnDismissListener(new PopupView.OnDismissListener() {
//
//								@Override
//								public void onDismiss() {
//									mSearchTask.stop();
//									popupSearchView = null;
//								}
//							});
//					searchView.clearFocus();
//					cacheSearch = query;
//
//				} else {
//					Toast.makeText(getApplicationContext(),
//							"The text is too short", Toast.LENGTH_SHORT).show();
//				}
//				return false;
//			}
//
//			@Override
//			public boolean onQueryTextChange(String newText) {
//				// TODO Auto-generated method stub
//				return false;
//			}
//		});
//
//		searchView.setOnCloseListener(new OnCloseListener() {
//
//			@Override
//			public boolean onClose() {
//				cacheSearch = "";
//				return false;
//			}
//		});

		return true;
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		if (mButtonsVisible && mTopBarMode != TopBarMode.Search) {
			// hideButtons();
		} else {
			// showButtons();
			searchModeOff();
		}

		//MenuItem seaechItem = menu.findItem(R.id.mu_reader_search);
//		SearchView searchView = (SearchView) MenuItemCompat
//				.getActionView(seaechItem);
//		searchView.clearFocus();
//		if (!cacheSearch.equals(""))
//			searchView.setQuery(cacheSearch, false);

		MenuItem bookmarkItem = menu.findItem(R.id.mu_reader_bookmark);
		BookmarkData mMark = null;
		if (core != null)
			mMark = mapBookmark.get(core.getDocumentPage(mCurrentPage));
		if (mMark != null) {
			bookmarkItem.setTitle(getString(R.string.delete_bookmark));
			bookmarkItem.setIcon(R.drawable.ic_action_bookmark_after);
		}
		if (core.getFilePath() == null) {
			bookmarkItem.setVisible(false);
		}

		MenuItem rotateItem = menu.findItem(R.id.mu_reader_autorotate);
		int currentRequestedOrientation = getRequestedOrientation();
		if (currentRequestedOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR) {
			rotateItem
					.setIcon(R.drawable.ic_action_device_access_screen_rotation);
			rotateItem.setChecked(true);
		} else {
			int currentOrientation = getResources().getConfiguration().orientation;
			if (currentOrientation == Configuration.ORIENTATION_LANDSCAPE) {
				rotateItem
						.setIcon(R.drawable.ic_action_device_access_screen_locked_to_landscape);
			} else {
				rotateItem
						.setIcon(R.drawable.ic_action_device_access_screen_locked_to_portrait);
			}
			rotateItem.setChecked(false);
		}

		MenuItem flowingText = menu.findItem(R.id.mu_reader_flowingtext);
		if (mReflow) {
			flowingText.setTitle(getString(R.string.original_pages));
		}

		return super.onPrepareOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item) {
		if (item.getItemId() == android.R.id.home) {
			finish();
			return true;
		} else if (item.getItemId() == R.id.mu_reader_bookmark) {
			bookmarkAction();
			return true;
		} else if (item.getItemId() == R.id.mu_reader_print) {
			printDoc();
			return true;
		} else if (item.getItemId() == R.id.mu_reader_flowingtext) {
			toggleReflow();
			return true;
		} else if (item.getItemId() == R.id.mu_reader_displayoption) {
			showDisplayOptions();
			return true;
		} else if (item.getItemId() == R.id.mu_reader_autorotate) {
			int currentRequestedOrientation = getRequestedOrientation();
			if (currentRequestedOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR) {
				int currentOrientation = getResources().getConfiguration().orientation;
				if (currentOrientation == Configuration.ORIENTATION_LANDSCAPE) {
					setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
					item.setIcon(R.drawable.ic_action_device_access_screen_locked_to_landscape);
				} else {
					setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
					item.setIcon(R.drawable.ic_action_device_access_screen_locked_to_portrait);
				}
				item.setChecked(false);
			} else {
				setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
				item.setIcon(R.drawable.ic_action_device_access_screen_rotation);
				item.setChecked(true);
			}

			item.setEnabled(false);
			new Handler().postDelayed(new Runnable() {
				@Override
				public void run() {
					item.setEnabled(true);
				}
			}, 200);
			return true;
		}

		return super.onOptionsItemSelected(item);
	}

	private void showDisplayOptions() {
		LayoutInflater layoutInflater = (LayoutInflater) getBaseContext()
				.getSystemService(LAYOUT_INFLATER_SERVICE);
		View popupView = layoutInflater.inflate(R.layout.option_layout, null,
				false);
		final AQuery aq = new AQuery(popupView);

		ArrayAdapter<CharSequence> dayNightTheme = ArrayAdapter
				.createFromResource(getApplicationContext(),
						R.array.daynight_mode, R.layout.z_spinner_item);
		dayNightTheme.setDropDownViewResource(R.layout.z_spinner_dropdown_item);

		int themeIndex = 0;
		int theme = PreferencesReader.getThemeMode(MuPDFActivity.this);
		if (theme == MuPDFCore.PAPER_NORMAL) {
			themeIndex = 0;
		} else if (theme == MuPDFCore.PAPER_GRAY_INVERT) {
			themeIndex = 1;
		} else
			themeIndex = 0;

		aq.id(R.id.spinnerDayNight).adapter(dayNightTheme)
				.setSelection(themeIndex).getSpinner()
				.setOnItemSelectedListener(new OnItemSelectedListener() {

					@Override
					public void onItemSelected(AdapterView<?> arg0, View arg1,
											   int index, long arg3) {
						int theme = PreferencesReader
								.getThemeMode(MuPDFActivity.this);
						switch (index) {
							case 0:
								if (theme != MuPDFCore.PAPER_NORMAL) {
									core.setThemeMode(MuPDFCore.PAPER_NORMAL);
									PreferencesReader.saveThemeMode(
											MuPDFActivity.this,
											MuPDFCore.PAPER_NORMAL);
									backGroundPage = whiteColor;
									if (mReflow) {
										mDocView.setAdapter(new MuPDFReflowAdapter(
												MuPDFActivity.this, core));
									} else
										mDocView.setAdapter(new MuPDFPageAdapter(
												MuPDFActivity.this,
												MuPDFActivity.this, core));
								}
								break;
							case 1:
								if (theme != MuPDFCore.PAPER_GRAY_INVERT) {
									core.setThemeMode(MuPDFCore.PAPER_GRAY_INVERT);
									PreferencesReader.saveThemeMode(
											MuPDFActivity.this,
											MuPDFCore.PAPER_GRAY_INVERT);
									backGroundPage = blackColor;
									if (mReflow) {
										mDocView.setAdapter(new MuPDFReflowAdapter(
												MuPDFActivity.this, core));
									} else
										mDocView.setAdapter(new MuPDFPageAdapter(
												MuPDFActivity.this,
												MuPDFActivity.this, core));
								}
								break;
							default:
								if (theme != MuPDFCore.PAPER_NORMAL) {
									core.setThemeMode(MuPDFCore.PAPER_NORMAL);
									PreferencesReader.saveThemeMode(
											MuPDFActivity.this,
											MuPDFCore.PAPER_NORMAL);
									backGroundPage = whiteColor;
									if (mReflow) {
										mDocView.setAdapter(new MuPDFReflowAdapter(
												MuPDFActivity.this, core));
									} else
										mDocView.setAdapter(new MuPDFPageAdapter(
												MuPDFActivity.this,
												MuPDFActivity.this, core));
								}
								break;
						}
					}

					@Override
					public void onNothingSelected(AdapterView<?> arg0) {
						// TODO Auto-generated method stub

					}
				});

		ArrayAdapter<CharSequence> doublePageMode = ArrayAdapter
				.createFromResource(getApplicationContext(), R.array.page_mode,
						R.layout.z_spinner_item);
		doublePageMode
				.setDropDownViewResource(R.layout.z_spinner_dropdown_item);
		aq.id(R.id.spinnerDoublePageMode).enabled(mReflow ? false : true)
				.adapter(doublePageMode).setSelection(dPageMode).getSpinner()
				.setOnItemSelectedListener(new OnItemSelectedListener() {

					@Override
					public void onItemSelected(AdapterView<?> arg0, View arg1,
											   int index, long arg3) {
						switch (index) {
							case MuPDFCore.SINGLE_PAGE_MODE:
								if (dPageMode != index) {
									int documentPage = core
											.getDocumentPage(mCurrentPage);
									dPageMode = index;
									core.setDoubleMode(false);
									mDocView.setAdapter(new MuPDFPageAdapter(
											MuPDFActivity.this, MuPDFActivity.this,
											core));
									mCurrentPage = getDisplayPage(documentPage);
									mDocView.setDisplayedViewIndex(mCurrentPage);
									PreferencesReader.savePageMode(
											MuPDFActivity.this, dPageMode);
									aq.id(R.id.checkBoxShowCoverPage)
											.enabled(false);
								}
								break;
							case MuPDFCore.DOUBLE_PAGE_MODE:
								if (dPageMode != index) {
									int documentPage = core
											.getDocumentPage(mCurrentPage);
									dPageMode = index;
									core.setDoubleMode(true);
									// muPdfAdapter.notifyDataSetInvalidated();
									mDocView.setAdapter(new MuPDFPageAdapter(
											MuPDFActivity.this, MuPDFActivity.this,
											core));
									mCurrentPage = getDisplayPage(documentPage);
									mDocView.setDisplayedViewIndex(mCurrentPage);
									PreferencesReader.savePageMode(
											MuPDFActivity.this, dPageMode);
									aq.id(R.id.checkBoxShowCoverPage).enabled(true);
								}
								break;
							case MuPDFCore.AUTO_PAGE_MODE:
								if (dPageMode != index) {
									int documentPage = core
											.getDocumentPage(mCurrentPage);
									dPageMode = index;
									int currentOrientation = getResources()
											.getConfiguration().orientation;
									if (currentOrientation == Configuration.ORIENTATION_LANDSCAPE) {
										core.setDoubleMode(true);
									} else {
										core.setDoubleMode(false);
									}
									// muPdfAdapter.notifyDataSetInvalidated();
									mDocView.setAdapter(new MuPDFPageAdapter(
											MuPDFActivity.this, MuPDFActivity.this,
											core));
									mCurrentPage = getDisplayPage(documentPage);
									mDocView.setDisplayedViewIndex(mCurrentPage);
									PreferencesReader.savePageMode(
											MuPDFActivity.this, dPageMode);
									aq.id(R.id.checkBoxShowCoverPage).enabled(true);
								}
								break;

							default:
								break;
						}

					}

					@Override
					public void onNothingSelected(AdapterView<?> arg0) {
						// TODO Auto-generated method stub

					}
				});

		aq.id(R.id.checkBoxShowCoverPage)
				.checked(showCoverPage)
				.enabled(
						dPageMode == MuPDFCore.SINGLE_PAGE_MODE || mReflow ? false
								: true).getCheckBox()
				.setOnCheckedChangeListener(new OnCheckedChangeListener() {

					@Override
					public void onCheckedChanged(CompoundButton buttonView,
												 boolean isChecked) {
						int documentPage = core.getDocumentPage(mCurrentPage);
						showCoverPage = isChecked;
						core.setCoverPageMode(isChecked);
						mDocView.setAdapter(new MuPDFPageAdapter(
								MuPDFActivity.this, MuPDFActivity.this, core));
						mCurrentPage = getDisplayPage(documentPage);
						mDocView.setDisplayedViewIndex(mCurrentPage);
						PreferencesReader.saveShowCoverPageMode(
								MuPDFActivity.this, showCoverPage);
					}
				});

		AlertDialog.Builder builder = new AlertDialog.Builder(
				MuPDFActivity.this);
		// builder.setTitle("Display Options");
		builder.setView(popupView);
		builder.create().show();
	}

	private HashMap<Integer, BookmarkData> mapBookmark = new HashMap<Integer, BookmarkData>();

	public void ReadBookmark() {
		DBBookmark db = new DBBookmark(this).open();
		ArrayList<BookmarkData> bookmarks = db.getAllFormPath(mFilePath);
		db.close();
		if (mapBookmark == null)
			mapBookmark = new HashMap<Integer, BookmarkData>();
		mapBookmark.clear();
		for (BookmarkData mark : bookmarks) {
			mapBookmark.put(mark.page, mark);
		}
	}

	private void bookmarkAction() {
		final DBBookmark db = new DBBookmark(MuPDFActivity.this).open();
		if (!db.isMarkFormPath(mFilePath, core.getDocumentPage(mCurrentPage))) {
			String currentTime = ZReaderUtils.getCurrentTiem() + "";
			db.addBookmark(mFilePath, mFileName,
					core.getDocumentPage(mCurrentPage), "-", currentTime);
			db.close();
			BookmarkData bmData = new BookmarkData();
			bmData.filePath = mFilePath;
			bmData.bookName = mFileName;
			bmData.page = core.getDocumentPage(mCurrentPage);
			bmData.markName = "-";
			bmData.addTime = currentTime;
			mapBookmark.put(core.getDocumentPage(mCurrentPage), bmData);
			supportInvalidateOptionsMenu();
			Toast.makeText(
					MuPDFActivity.this,
					"Page " + (core.getDocumentPage(mCurrentPage) + 1)
							+ " bookmark added", Toast.LENGTH_LONG).show();
		} else {
			db.deleteBookmarkFromPath(mFilePath,
					core.getDocumentPage(mCurrentPage));
			db.close();
			mapBookmark.remove(core.getDocumentPage(mCurrentPage));
			supportInvalidateOptionsMenu();
		}
	}

	public int getDisplayPage(int documentPage) {
		if (core != null && core.getDoubleMode()) {
			if (core.getCoverPageMode()) {
				return (documentPage + 1) / 2;
			} else
				return documentPage / 2;
		} else
			return documentPage;
	}

	PopupView popView;

	public void showFullScreenDialog(int showPage) {
		int showDialog = getResources().getInteger(R.integer.showdialog);
		if (showDialog == 0) {
			popView = new PopupView(getApplicationContext(),
					createOutlineView(showPage), true);
			popView.show(mOutlineAction, 0, 0, disPlayWidth);
		} else {
			popView = new PopupView(getApplicationContext(),
					createOutlineView(showPage), false);
			popView.show(mOutlineAction);
		}
	}

	private View createOutlineView(final int showPage) {
		LayoutInflater inflater = (LayoutInflater) getApplicationContext()
				.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		View rootView = inflater.inflate(R.layout.activity_current, null);
		final ViewPager mViewPager = (ViewPager) rootView
				.findViewById(R.id.pager);

		OutlineAdapter adapter = new OutlineAdapter();
		mViewPager.setAdapter(adapter);

		PagerSlidingTabStrip tabsStrip = (PagerSlidingTabStrip) rootView
				.findViewById(R.id.tabs_strip);
		tabsStrip.setViewPager(mViewPager);
		tabsStrip.setIndicatorColorResource(R.color.accent_color);
		tabsStrip.setTextColorResource(R.drawable.actionbar_tab_textselector);

		new Handler().postDelayed(new Runnable() {
			@Override
			public void run() {
				if (popView != null && mViewPager != null)
					mViewPager.setCurrentItem(showPage);
			}
		}, 300);

		return rootView;
	}

	class OutlineAdapter extends PagerAdapter {

		@Override
		public Object instantiateItem(ViewGroup container, int position) {

			LayoutInflater inflater = (LayoutInflater) getApplicationContext()
					.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			View rootView = null;
			if (position == 0) {
				rootView = inflater.inflate(R.layout.layout_listview, null);
				ListView listOutline = (ListView) rootView
						.findViewById(R.id.mListView);
				final OutlineItemsAdapter adapter = new OutlineItemsAdapter();
				listOutline.setAdapter(adapter);
				if (adapter.getCount() == 0) {
					TextView noChapter = (TextView) rootView
							.findViewById(R.id.textViewNoChapter);
					noChapter.setVisibility(View.VISIBLE);
				}
				listOutline.setOnItemClickListener(new OnItemClickListener() {

					@Override
					public void onItemClick(AdapterView<?> arg0, View arg1,
											int position, long arg3) {
						OutlineItem item = (OutlineItem) adapter
								.getItem(position);
						if (item != null) {
							mDocView.setDisplayedViewIndex(getDisplayPage(item.page));
							if (popView != null)
								popView.dismiss();
						}
					}
				});
			} else if (position == 1) {
				rootView = inflater.inflate(R.layout.layout_gridview, null);
				GridView grid = (GridView) rootView
						.findViewById(R.id.ThumbnailGridView);
				DBBookmark db = new DBBookmark(MuPDFActivity.this).open();
				ArrayList<BookmarkData> bmData = db.getAllFormPath(mFilePath);
				db.close();
				final BookmarkViewAdapter adapter = new BookmarkViewAdapter(
						MuPDFActivity.this, core, bmData);
				grid.setAdapter(adapter);
				if (adapter.getCount() == 0) {
					TextView noBookmarks = (TextView) rootView
							.findViewById(R.id.textViewNoBookmarks);
					noBookmarks.setVisibility(View.VISIBLE);
				}
				grid.setOnItemClickListener(new OnItemClickListener() {

					@Override
					public void onItemClick(AdapterView<?> arg0, View arg1,
											int position, long arg3) {
						BookmarkData bookmark = (BookmarkData) adapter
								.getItem(position);
						if (bookmark != null) {
							mDocView.setDisplayedViewIndex(getDisplayPage(bookmark.page));
							if (popView != null)
								popView.dismiss();
						}
					}
				});
			} else if (position == 2) {
				rootView = inflater.inflate(R.layout.layout_gridview, null);
				GridView grid = (GridView) rootView
						.findViewById(R.id.ThumbnailGridView);
				ThumbnailViewAdapter mThumbnailAdapter = new ThumbnailViewAdapter(
						MuPDFActivity.this, core);
				grid.setAdapter(mThumbnailAdapter);

				int index = core.getDocumentPage(mCurrentPage);
				grid.setSelection(index);
				grid.setOnItemClickListener(new OnItemClickListener() {

					@Override
					public void onItemClick(AdapterView<?> arg0, View arg1,
											int position, long arg3) {
						mDocView.setDisplayedViewIndex(getDisplayPage(position));
						if (popView != null)
							popView.dismiss();
					}
				});
			}
			((ViewPager) container).addView(rootView, position);
			return rootView;

		}

		@Override
		public void destroyItem(ViewGroup container, int position, Object object) {
			((ViewPager) container).removeView((View) object);
		}

		@Override
		public int getCount() {
			return 3;
		}

		@Override
		public CharSequence getPageTitle(int position) {
			Locale l = Locale.getDefault();
			switch (position) {
				case 0:
					return "chapter";
				case 1:
					return "bookmark";
				case 2:
					return "thumbnail";
				default:
					return "-";
			}
		}

		@Override
		public boolean isViewFromObject(View arg0, Object arg1) {
			return arg0 == ((View) arg1);
		}

	}

	class OutlineItemsAdapter extends BaseAdapter {

		@Override
		public int getCount() {
			return outlineData.size();
		}

		@Override
		public Object getItem(int position) {
			return outlineData.get(position);
		}

		@Override
		public long getItemId(int position) {
			return position;
		}

		@Override
		public View getView(final int position, View convertView,
							ViewGroup parent) {
			AQuery aq;
			if (convertView == null) {
				convertView = LayoutInflater.from(getApplicationContext())
						.inflate(R.layout.outline_item, parent, false);
				aq = new AQuery(MuPDFActivity.this, convertView);
			} else {
				aq = new AQuery(MuPDFActivity.this, convertView);
			}

			int level = outlineData.get(position).level;
			int margin = level * 16;
			aq.id(R.id.outlineTitle).text(outlineData.get(position).title)
					.margin(margin, 0, 0, 0);
			aq.id(R.id.outlinePage).text(outlineData.get(position).page + "");

			return convertView;
		}

	}

	public void getCoverThumbnail() {
		if (core != null && core.getFilePath() != null) {
			int position = 0;
			String dirPath = PreferencesReader.getDataDir(this) + "/Thumbnail/"
					+ PreferencesReader.rePlaceString(core.getFilePath());
			File dirFile = new File(dirPath);
			if (!dirFile.exists() || !dirFile.isDirectory()) {
				dirFile.mkdirs();
			}

			String mCachedBitmapFilePath = dirPath + "/"
					+ core.getFilePathReplace() + "_" + position;
			File mCachedBitmapFile = new File(mCachedBitmapFilePath);
			Bitmap bmp = null;

			try {
				if (mCachedBitmapFile.exists() && mCachedBitmapFile.canRead()) {
					return;
				}
			} catch (Exception e) {
				e.printStackTrace();
				mCachedBitmapFile.delete();
				bmp = null;
			}

			PointF pageSize = core.getSinglePageSize(position);
			int sizeY = (int) ZReaderUtils.convertDpToPixel(130,
					MuPDFActivity.this);
			if (sizeY == 0)
				sizeY = 120;
			int sizeX = (int) (pageSize.x / pageSize.y * sizeY);
			Point newSize = new Point(sizeX, sizeY);
			bmp = Bitmap.createBitmap(newSize.x, newSize.y,
					Bitmap.Config.ARGB_8888);
			core.drawThumbnailPage(bmp, position, newSize.x, newSize.y, 0, 0,
					newSize.x, newSize.y);
			try {
				bmp.compress(CompressFormat.JPEG, 75, new FileOutputStream(
						mCachedBitmapFile));
			} catch (FileNotFoundException e) {
				mCachedBitmapFile.delete();
				e.printStackTrace();
			}
			return;
		}
	}

}
