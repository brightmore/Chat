package eu.siacs.conversations.ui;

import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.FragmentTransaction;
import android.app.PendingIntent;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentSender.SendIntentException;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.support.v4.widget.SlidingPaneLayout;
import android.support.v4.widget.SlidingPaneLayout.PanelSlideListener;
import android.util.Log;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.PopupMenu.OnMenuItemClickListener;
import android.widget.Toast;

import net.java.otr4j.session.SessionStatus;

import java.util.ArrayList;
import java.util.List;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Message;
import eu.siacs.conversations.services.XmppConnectionService.OnAccountUpdate;
import eu.siacs.conversations.services.XmppConnectionService.OnConversationUpdate;
import eu.siacs.conversations.services.XmppConnectionService.OnRosterUpdate;
import eu.siacs.conversations.ui.adapter.ConversationAdapter;
import eu.siacs.conversations.utils.ExceptionHelper;

public class ConversationActivity extends XmppActivity implements
		OnAccountUpdate, OnConversationUpdate, OnRosterUpdate {

	public static final String VIEW_CONVERSATION = "viewConversation";
    public static final String VIEW_CONVERSATIONS = "viewConversations";
    public static final String CONVERSATION = "conversationUuid";
	public static final String TEXT = "text";
	public static final String NICK = "nick";
	public static final String PRESENCE = "eu.siacs.conversations.presence";

	public static final int REQUEST_SEND_MESSAGE = 0x0201;
	public static final int REQUEST_DECRYPT_PGP = 0x0202;
	public static final int REQUEST_ENCRYPT_MESSAGE = 0x0207;
	private static final int REQUEST_ATTACH_IMAGE_DIALOG = 0x0203;
	private static final int REQUEST_IMAGE_CAPTURE = 0x0204;
	private static final int REQUEST_RECORD_AUDIO = 0x0205;
	private static final int REQUEST_SEND_PGP_IMAGE = 0x0206;
	private static final int REQUEST_ATTACH_FILE_DIALOG = 0x0208;
	private static final int ATTACHMENT_CHOICE_CHOOSE_IMAGE = 0x0301;
	private static final int ATTACHMENT_CHOICE_TAKE_PHOTO = 0x0302;
	private static final int ATTACHMENT_CHOICE_CHOOSE_FILE = 0x0303;

	private static final int REFRESH_UI_INTERVAL = 10000;
	private static final String STATE_OPEN_CONVERSATION = "state_open_conversation";
	private static final String STATE_PANEL_OPEN = "state_panel_open";
	private static final String STATE_PENDING_URI = "state_pending_uri";

	private String mOpenConverstaion = null;
	private boolean mPanelOpen = true;
	private Uri mPendingImageUri = null;
	private Uri mPendingFileUri = null;

	private View mContentView;

	private List<Conversation> conversationList = new ArrayList<>();
	private Conversation mSelectedConversation = null;
    private Conversation mSelectedConversationForContext = null;
	private ListView listView;
	private ConversationFragment mConversationFragment;

	private ArrayAdapter<Conversation> listAdapter;

	private Toast prepareFileToast;

	private Handler updateHandler = new Handler();
	private Runnable refreshUi = new Runnable() {
		@Override
		public void run() {
			onConversationUpdate();
			if (isRunning) {
				updateHandler.postDelayed(this, REFRESH_UI_INTERVAL);
			}
		}
	};
	private boolean isRunning = false;


	public List<Conversation> getConversationList() {
		return this.conversationList;
	}

	public Conversation getSelectedConversation() {
		return this.mSelectedConversation;
	}

	public void setSelectedConversation(Conversation conversation) {
		this.mSelectedConversation = conversation;
	}

	public ListView getConversationListView() {
		return this.listView;
	}

	public void showConversationsOverview() {
		if (mContentView instanceof SlidingPaneLayout) {
			SlidingPaneLayout mSlidingPaneLayout = (SlidingPaneLayout) mContentView;
			mSlidingPaneLayout.openPane();
		}
	}

	@Override
	protected String getShareableUri() {
		Conversation conversation = getSelectedConversation();
		if (conversation != null) {
			return conversation.getAccount().getShareableUri();
		} else {
			return "";
		}
	}

	public void hideConversationsOverview() {
		if (mContentView instanceof SlidingPaneLayout) {
			SlidingPaneLayout mSlidingPaneLayout = (SlidingPaneLayout) mContentView;
			mSlidingPaneLayout.closePane();
		}
	}

	public boolean isConversationsOverviewHideable() {
		if (mContentView instanceof SlidingPaneLayout) {
			SlidingPaneLayout mSlidingPaneLayout = (SlidingPaneLayout) mContentView;
			return mSlidingPaneLayout.isSlideable();
		} else {
			return false;
		}
	}

	public boolean isConversationsOverviewVisable() {
		if (mContentView instanceof SlidingPaneLayout) {
			SlidingPaneLayout mSlidingPaneLayout = (SlidingPaneLayout) mContentView;
			return mSlidingPaneLayout.isOpen();
		} else {
			return true;
		}
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (savedInstanceState != null) {mOpenConverstaion = savedInstanceState.getString(
					STATE_OPEN_CONVERSATION, null);
			mPanelOpen = savedInstanceState.getBoolean(STATE_PANEL_OPEN, true);
			String pending = savedInstanceState.getString(STATE_PENDING_URI, null);
			if (pending != null) {
				mPendingImageUri = Uri.parse(pending);
			}
		}

		setContentView(R.layout.fragment_conversations_overview);

		this.mConversationFragment = new ConversationFragment();
		FragmentTransaction transaction = getFragmentManager().beginTransaction();
		transaction.replace(R.id.selected_conversation, this.mConversationFragment, "conversation");
		transaction.commit();

		listView = (ListView) findViewById(R.id.list);
		this.listAdapter = new ConversationAdapter(this, conversationList);
		listView.setAdapter(this.listAdapter);

		if (getActionBar() != null) {
			getActionBar().setDisplayHomeAsUpEnabled(false);
			getActionBar().setHomeButtonEnabled(false);
		}

		listView.setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> arg0, View clickedView,
									int position, long arg3) {
				if (getSelectedConversation() != conversationList.get(position)) {
					setSelectedConversation(conversationList.get(position));
					ConversationActivity.this.mConversationFragment.reInit(getSelectedConversation());
				}
				hideConversationsOverview();
			}
		});
		registerForContextMenu(listView);
		mContentView = findViewById(R.id.content_view_spl);
		if (mContentView == null) {
			mContentView = findViewById(R.id.content_view_ll);
		}
		if (mContentView instanceof SlidingPaneLayout) {
			SlidingPaneLayout mSlidingPaneLayout = (SlidingPaneLayout) mContentView;
			mSlidingPaneLayout.setParallaxDistance(150);
			mSlidingPaneLayout
					.setShadowResource(R.drawable.es_slidingpane_shadow);
			mSlidingPaneLayout.setSliderFadeColor(0);
			mSlidingPaneLayout.setPanelSlideListener(new PanelSlideListener() {

				@Override
				public void onPanelOpened(View arg0) {
					ActionBar ab = getActionBar();
					if (ab != null) {
						ab.setDisplayHomeAsUpEnabled(false);
						ab.setHomeButtonEnabled(false);
						ab.setTitle(R.string.app_name);
					}
					invalidateOptionsMenu();
					hideKeyboard();
					if (xmppConnectionServiceBound) {
						xmppConnectionService.getNotificationService()
								.setOpenConversation(null);
					}
					closeContextMenu();
				}

				@Override
				public void onPanelClosed(View arg0) {
					openConversation();
				}

				@Override
				public void onPanelSlide(View arg0, float arg1) {
					// TODO Auto-generated method stub

				}
			});
		}
	}

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        getMenuInflater().inflate(R.menu.conversations_context, menu);
        AdapterView.AdapterContextMenuInfo acmi = (AdapterView.AdapterContextMenuInfo) menuInfo;
        if(acmi == null) return;
        this.mSelectedConversationForContext = this.conversationList.get(acmi.position);
        menu.setHeaderTitle(this.mSelectedConversationForContext.getName());
        MenuItem enableNotifications = menu.findItem(R.id.action_unmute);
        MenuItem disableNotifications = menu.findItem(R.id.action_mute);
        if (this.mSelectedConversationForContext.isMuted()) {
            disableNotifications.setVisible(false);
        } else {
            enableNotifications.setVisible(false);
        }

        MenuItem details = menu.findItem(R.id.action_contact_details);
        MenuItem delete = menu.findItem(R.id.action_delete_contact);
        if(mSelectedConversationForContext.getMode() == Conversation.MODE_MULTI) {
            details.setVisible(false);
            delete.setVisible(false);
        } else {
            details.setVisible(true);
            delete.setVisible(true);
        }

        super.onCreateContextMenu(menu,v,menuInfo);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_archive:
                xmppConnectionService.clearConversationHistory(mSelectedConversationForContext);
                endConversation(mSelectedConversationForContext);
                return true;
            case R.id.action_mute:
                muteConversationDialog(mSelectedConversationForContext);
                return true;
            case R.id.action_unmute:
                mSelectedConversationForContext.setMutedTill(0);
                xmppConnectionService.updateConversation(mSelectedConversationForContext);
                updateConversationList();
                ConversationActivity.this.mConversationFragment.updateMessages();
                return true;
            case R.id.action_contact_details:
                switchToContactDetails(mSelectedConversationForContext.getContact());
                return true;
            case R.id.action_delete_contact:
                deleteContact(mSelectedConversationForContext.getContact());
                return true;
            default:
                return super.onContextItemSelected(item);
        }
    }

    protected void deleteContact(Contact contact) {
        final Contact contactfinal = contact;
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setNegativeButton(R.string.cancel, null);
        builder.setTitle(R.string.action_delete_contact);
        builder.setMessage(getString(R.string.remove_contact_text, contact.getJid()));
        builder.setPositiveButton(R.string.delete, new OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                xmppConnectionService.clearConversationHistory(mSelectedConversationForContext);
                endConversation(mSelectedConversationForContext);
                xmppConnectionService.deleteContactOnServer(contactfinal);
            }
        });
        builder.create().show();
    }

    public void openConversation() {
		ActionBar ab = getActionBar();
		if (ab != null) {
			ab.setDisplayHomeAsUpEnabled(true);
			ab.setHomeButtonEnabled(true);
			if (getSelectedConversation().getMode() == Conversation.MODE_SINGLE
					|| ConversationActivity.this
					.useSubjectToIdentifyConference()) {
				ab.setTitle(getSelectedConversation().getName());
			} else {
				ab.setTitle(getSelectedConversation().getContactJid().toBareJid().toString());
			}
		}
		invalidateOptionsMenu();
		if (xmppConnectionServiceBound) {
			xmppConnectionService.getNotificationService().setOpenConversation(getSelectedConversation());
			if (!getSelectedConversation().isRead()) {
				xmppConnectionService.markRead(getSelectedConversation(), true);
				listView.invalidateViews();
			}
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.conversations, menu);
		MenuItem menuSecure = menu.findItem(R.id.action_security);
		MenuItem menuArchive = menu.findItem(R.id.action_archive);
		MenuItem menuMucDetails = menu.findItem(R.id.action_muc_details);
		MenuItem menuContactDetails = menu.findItem(R.id.action_contact_details);
		MenuItem menuAttach = menu.findItem(R.id.action_attach_file);
		MenuItem menuAdd = menu.findItem(R.id.action_add);
		MenuItem menuInviteContact = menu.findItem(R.id.action_invite);
		MenuItem menuMute = menu.findItem(R.id.action_mute);
		MenuItem menuUnmute = menu.findItem(R.id.action_unmute);
		MenuItem menuTimeout = menu.findItem(R.id.action_timeout);

		if (isConversationsOverviewVisable()
				&& isConversationsOverviewHideable()) {
			menuArchive.setVisible(false);
			menuMucDetails.setVisible(false);
			menuContactDetails.setVisible(false);
			menuSecure.setVisible(false);
			menuInviteContact.setVisible(false);
			menuAttach.setVisible(false);
			menuMute.setVisible(false);
			menuUnmute.setVisible(false);
			menuTimeout.setVisible(false);
		} else {
			menuAdd.setVisible(!isConversationsOverviewHideable());
			if (this.getSelectedConversation() != null) {
				if (this.getSelectedConversation().getMode() == Conversation.MODE_MULTI) {
					menuContactDetails.setVisible(false);
					menuAttach.setVisible(false);
                    menuSecure.setVisible(false);
				} else {
					menuMucDetails.setVisible(false);
					menuInviteContact.setVisible(false);
					if (this.getSelectedConversation().isOtrFingerprintVerified()) {
						menuSecure.setIcon(R.drawable.ic_action_secure);
					} else {
						menuSecure.setIcon(R.drawable.ic_action_not_secure);
					}
				}
				if (this.getSelectedConversation().isMuted()) {
					menuMute.setVisible(false);
				} else {
					menuUnmute.setVisible(false);
				}
			}
		}
		return true;
	}

	private void selectPresenceToAttachFile(final int attachmentChoice) {
		selectPresence(getSelectedConversation(), new OnPresenceSelected() {

			@Override
			public void onPresenceSelected() {
				if (attachmentChoice == ATTACHMENT_CHOICE_TAKE_PHOTO) {
					mPendingImageUri = xmppConnectionService.getFileBackend()
							.getTakePhotoUri();
					Intent takePictureIntent = new Intent(
							MediaStore.ACTION_IMAGE_CAPTURE);
					takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT,
							mPendingImageUri);
					if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
						startActivityForResult(takePictureIntent,
								REQUEST_IMAGE_CAPTURE);
					}
				} else if (attachmentChoice == ATTACHMENT_CHOICE_CHOOSE_IMAGE) {
					Intent attachFileIntent = new Intent();
					attachFileIntent.setType("image/*");
					attachFileIntent.setAction(Intent.ACTION_GET_CONTENT);
					Intent chooser = Intent.createChooser(attachFileIntent,
							getString(R.string.attach_file));
					startActivityForResult(chooser, REQUEST_ATTACH_IMAGE_DIALOG);
				} else if (attachmentChoice == ATTACHMENT_CHOICE_CHOOSE_FILE) {
					Intent attachFileIntent = new Intent();
					//attachFileIntent.setType("file/*");
					attachFileIntent.setType("*/*");
					attachFileIntent.addCategory(Intent.CATEGORY_OPENABLE);
					attachFileIntent.setAction(Intent.ACTION_GET_CONTENT);
					Intent chooser = Intent.createChooser(attachFileIntent,
							getString(R.string.attach_file));
					startActivityForResult(chooser, REQUEST_ATTACH_FILE_DIALOG);
				}
			}
		});
	}

	private void attachFile(final int attachmentChoice) {
		final Conversation conversation = getSelectedConversation();
		if (conversation.getNextEncryption(forceEncryption()) == Message.ENCRYPTION_PGP) {
			if (hasPgp()) {
				if (conversation.getContact().getPgpKeyId() != 0) {
					xmppConnectionService.getPgpEngine().hasKey(
							conversation.getContact(),
							new UiCallback<Contact>() {

								@Override
								public void userInputRequried(PendingIntent pi,
															  Contact contact) {
									ConversationActivity.this.runIntent(pi,
											attachmentChoice);
								}

								@Override
								public void success(Contact contact) {
									selectPresenceToAttachFile(attachmentChoice);
								}

								@Override
								public void error(int error, Contact contact) {
									displayErrorDialog(error);
								}
							});
				} else {
					final ConversationFragment fragment = (ConversationFragment) getFragmentManager()
							.findFragmentByTag("conversation");
					if (fragment != null) {
						fragment.showNoPGPKeyDialog(false,
								new OnClickListener() {

									@Override
									public void onClick(DialogInterface dialog,
														int which) {
										conversation
												.setNextEncryption(Message.ENCRYPTION_NONE);
										xmppConnectionService.databaseBackend
												.updateConversation(conversation);
										selectPresenceToAttachFile(attachmentChoice);
									}
								});
					}
				}
			} else {
				showInstallPgpDialog();
			}
		} else if (getSelectedConversation().getNextEncryption(
				forceEncryption()) == Message.ENCRYPTION_NONE) {
			selectPresenceToAttachFile(attachmentChoice);
		} else {
			selectPresenceToAttachFile(attachmentChoice);
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == android.R.id.home) {
			showConversationsOverview();
			return true;
		} else if (item.getItemId() == R.id.action_add) {
			startActivity(new Intent(this, StartConversationActivity.class));
			return true;
		} else if (getSelectedConversation() != null) {
			switch (item.getItemId()) {
				case R.id.action_attach_file:
					attachFileDialog();
					break;
				case R.id.action_timeout:
					chooseMessageTimeoutDialog(findViewById(R.id.action_timeout));
					break;
				case R.id.action_archive:
                    this.xmppConnectionService.clearConversationHistory(getSelectedConversation());
					this.endConversation(getSelectedConversation());
					break;
				case R.id.action_contact_details:
					Contact contact = this.getSelectedConversation().getContact();
					if (contact.showInRoster()) {
						switchToContactDetails(contact);
					} else {
						showAddToRosterDialog(getSelectedConversation());
					}
					break;
				case R.id.action_muc_details:
					Intent intent = new Intent(this,
							ConferenceDetailsActivity.class);
					intent.setAction(ConferenceDetailsActivity.ACTION_VIEW_MUC);
					intent.putExtra("uuid", getSelectedConversation().getUuid());
					startActivity(intent);
					break;
				case R.id.action_invite:
					inviteToConversation(getSelectedConversation());
					break;
				case R.id.action_security:
					if (getSelectedConversation().getMode() == Conversation.MODE_MULTI) {
						showEncryptionSummary(getSelectedConversation());
					} else {
						verifyOtrSessionDialog(getSelectedConversation(),findViewById(R.id.action_security));
					}
					break;
				case R.id.action_mute:
					muteConversationDialog(getSelectedConversation());
					break;
				case R.id.action_unmute:
					unmuteConversation(getSelectedConversation());
					break;
				default:
					break;
			}
			return super.onOptionsItemSelected(item);
		} else {
			return super.onOptionsItemSelected(item);
		}
	}

    private void showEncryptionSummary(Conversation conversation) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.encryptionsummary_title));
        builder.setIconAttribute(android.R.attr.alertDialogIcon);

        if (conversation.getMode() == Conversation.MODE_MULTI) {
            builder.setMessage(getString(R.string.encryptionsummary_tls));
        }
        else {
            builder.setMessage(getString(R.string.encryptionsummary_otr));
        }

        /*builder.setPositiveButton(getString(R.string.delete),
                new OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        xmppConnectionService.deleteAccount(account);
                        selectedAccount = null;
                    }
                });
        builder.setNegativeButton(getString(R.string.cancel), null);*/
        builder.create().show();
    }

	public void endConversation(Conversation conversation) {
		conversation.setStatus(Conversation.STATUS_ARCHIVED);
		showConversationsOverview();
		xmppConnectionService.archiveConversation(conversation);
		if (conversationList.size() > 0) {
			setSelectedConversation(conversationList.get(0));
			this.mConversationFragment.reInit(getSelectedConversation());
		} else {
			setSelectedConversation(null);
		}
	}

	protected void attachFileDialog() {
		View menuAttachFile = findViewById(R.id.action_attach_file);
		if (menuAttachFile == null) {
			return;
		}
		PopupMenu attachFilePopup = new PopupMenu(this, menuAttachFile);
		attachFilePopup.inflate(R.menu.attachment_choices);
		attachFilePopup.setOnMenuItemClickListener(new OnMenuItemClickListener() {

			@Override
			public boolean onMenuItemClick(MenuItem item) {
				switch (item.getItemId()) {
					case R.id.attach_choose_picture:
						attachFile(ATTACHMENT_CHOICE_CHOOSE_IMAGE);
						break;
					case R.id.attach_take_picture:
						attachFile(ATTACHMENT_CHOICE_TAKE_PHOTO);
						break;
					case R.id.action_choose_file:
						attachFile(ATTACHMENT_CHOICE_CHOOSE_FILE);
						break;
				}
				return false;
			}
		});
		attachFilePopup.show();
	}

	protected void chooseMessageTimeoutDialog(View view) {
		PopupMenu timeoutPopup = new PopupMenu(this,view);
		timeoutPopup.inflate(R.menu.timeout_choices);
		MenuItem never = timeoutPopup.getMenu().findItem(R.id.timeout_never);
		MenuItem thirtyMinutes = timeoutPopup.getMenu().findItem(R.id.timeout_30min);
		MenuItem oneHour = timeoutPopup.getMenu().findItem(R.id.timeout_1h);
		MenuItem sixHours = timeoutPopup.getMenu().findItem(R.id.timeout_6h);
		MenuItem twelveHours = timeoutPopup.getMenu().findItem(R.id.timeout_12h);
		MenuItem oneDay = timeoutPopup.getMenu().findItem(R.id.timeout_1d);
		MenuItem oneWeek = timeoutPopup.getMenu().findItem(R.id.timeout_1w);
		MenuItem oneMonth = timeoutPopup.getMenu().findItem(R.id.timeout_1m);
		switch(getSelectedConversation().getNextTimeout()) {
			case 0:
				never.setChecked(true);
				break;
			case 60 * 30:
				thirtyMinutes.setChecked(true);
				break;
			case 60 * 60:
				oneHour.setChecked(true);
				break;
			case 60 * 60 * 6:
				sixHours.setChecked(true);
				break;
			case 60 * 60 * 12:
				twelveHours.setChecked(true);
				break;
			case 60 * 60 * 24:
				oneDay.setChecked(true);
				break;
			case 60 * 60 * 24 * 7:
				oneWeek.setChecked(true);
				break;
			case 60 * 60 * 24 * 30:
				oneMonth.setChecked(true);
				break;
			default:
				never.setChecked(true);
				break;
		}
		timeoutPopup.setOnMenuItemClickListener(new OnMenuItemClickListener() {
			@Override
			public boolean onMenuItemClick(MenuItem menuItem) {
				switch (menuItem.getItemId()) {
					case R.id.timeout_never:
						getSelectedConversation().setNextTimeout(0);
						break;
					case R.id.timeout_30min:
						getSelectedConversation().setNextTimeout(60 * 30);
						break;
					case R.id.timeout_1h:
						getSelectedConversation().setNextTimeout(60 * 60);
						break;
					case R.id.timeout_6h:
						getSelectedConversation().setNextTimeout(60 * 60 * 6);
						break;
					case R.id.timeout_12h:
						getSelectedConversation().setNextTimeout(60 * 60 * 12);
						break;
					case R.id.timeout_1d:
						getSelectedConversation().setNextTimeout(60 * 60 * 24);
						break;
					case R.id.timeout_1w:
						getSelectedConversation().setNextTimeout(60 * 60 * 24 * 7);
						break;
					case R.id.timeout_1m:
						getSelectedConversation().setNextTimeout(60 * 60 * 24 * 30);
						break;
					default:
						getSelectedConversation().setNextTimeout(0);
						break;
				}
				return false;
			}
		});
		timeoutPopup.show();
	}

	public void verifyOtrSessionDialog(final Conversation conversation, View view) {
		if (!conversation.hasValidOtrSession() || conversation.getOtrSession().getSessionStatus() != SessionStatus.ENCRYPTED) {
			Toast.makeText(this, R.string.otr_session_not_started, Toast.LENGTH_LONG).show();
			return;
		}
		if (view == null) {
			return;
		}
		PopupMenu popup = new PopupMenu(this, view);
		popup.inflate(R.menu.verification_choices);
		popup.setOnMenuItemClickListener(new OnMenuItemClickListener() {
			@Override
			public boolean onMenuItemClick(MenuItem menuItem) {
				Intent intent = new Intent(ConversationActivity.this, VerifyOTRActivity.class);
				intent.setAction(VerifyOTRActivity.ACTION_VERIFY_CONTACT);
				intent.putExtra("contact", conversation.getContact().getJid().toBareJid().toString());
				intent.putExtra("account", conversation.getAccount().getJid().toBareJid().toString());
				switch (menuItem.getItemId()) {
					case R.id.show_fingerprint:
						intent.putExtra("mode",VerifyOTRActivity.MODE_SHOW_FINGERPRINT);
						break;
					case R.id.scan_fingerprint:
						intent.putExtra("mode",VerifyOTRActivity.MODE_SCAN_FINGERPRINT);
						break;
					case R.id.ask_question:
						intent.putExtra("mode",VerifyOTRActivity.MODE_ASK_QUESTION);
						break;
					case R.id.manual_verification:
						intent.putExtra("mode",VerifyOTRActivity.MODE_MANUAL_VERIFICATION);
						break;
				}
				startActivity(intent);
				return true;
			}
		});
		popup.show();
	}

	protected void selectEncryptionDialog(final Conversation conversation) {
		View menuItemView = findViewById(R.id.action_security);
		if (menuItemView == null) {
			return;
		}
		PopupMenu popup = new PopupMenu(this, menuItemView);
		final ConversationFragment fragment = (ConversationFragment) getFragmentManager()
				.findFragmentByTag("conversation");
		if (fragment != null) {
			popup.setOnMenuItemClickListener(new OnMenuItemClickListener() {

				@Override
				public boolean onMenuItemClick(MenuItem item) {
					switch (item.getItemId()) {
						case R.id.encryption_choice_none:
							conversation.setNextEncryption(Message.ENCRYPTION_NONE);
							item.setChecked(true);
							break;
						case R.id.encryption_choice_otr:
							conversation.setNextEncryption(Message.ENCRYPTION_OTR);
							item.setChecked(true);
							break;
						case R.id.encryption_choice_pgp:
							if (hasPgp()) {
								if (conversation.getAccount().getKeys()
										.has("pgp_signature")) {
									conversation
											.setNextEncryption(Message.ENCRYPTION_PGP);
									item.setChecked(true);
								} else {
									announcePgp(conversation.getAccount(),
											conversation);
								}
							} else {
								showInstallPgpDialog();
							}
							break;
						default:
							conversation.setNextEncryption(Message.ENCRYPTION_NONE);
							break;
					}
					xmppConnectionService.databaseBackend
							.updateConversation(conversation);
					fragment.updateChatMsgHint();
					return true;
				}
			});
			popup.inflate(R.menu.encryption_choices);
			MenuItem otr = popup.getMenu().findItem(R.id.encryption_choice_otr);
			MenuItem none = popup.getMenu().findItem(
					R.id.encryption_choice_none);
			if (conversation.getMode() == Conversation.MODE_MULTI) {
				otr.setEnabled(false);
			} else {
				if (forceEncryption()) {
					none.setVisible(false);
				}
			}
			switch (conversation.getNextEncryption(forceEncryption())) {
				case Message.ENCRYPTION_NONE:
					none.setChecked(true);
					break;
				case Message.ENCRYPTION_OTR:
					otr.setChecked(true);
					break;
				case Message.ENCRYPTION_PGP:
					popup.getMenu().findItem(R.id.encryption_choice_pgp)
							.setChecked(true);
					break;
				default:
					popup.getMenu().findItem(R.id.encryption_choice_none)
							.setChecked(true);
					break;
			}
			popup.show();
		}
	}

	protected void muteConversationDialog(final Conversation conversation) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(R.string.disable_notifications);
		final int[] durations = getResources().getIntArray(
				R.array.mute_options_durations);
		builder.setItems(R.array.mute_options_descriptions,
				new OnClickListener() {

					@Override
					public void onClick(DialogInterface dialog, int which) {
						long till;
						if (durations[which] == -1) {
							till = Long.MAX_VALUE;
						} else {
							till = SystemClock.elapsedRealtime()
									+ (durations[which] * 1000);
						}
						conversation.setMutedTill(till);
						ConversationActivity.this.xmppConnectionService.databaseBackend
								.updateConversation(conversation);
						updateConversationList();
						ConversationActivity.this.mConversationFragment.updateMessages();
						invalidateOptionsMenu();
					}
				});
		builder.create().show();
	}

	public void unmuteConversation(final Conversation conversation) {
		conversation.setMutedTill(0);
		this.xmppConnectionService.databaseBackend.updateConversation(conversation);
		updateConversationList();
		ConversationActivity.this.mConversationFragment.updateMessages();
		invalidateOptionsMenu();
	}

	@Override
	public boolean onKeyDown(final int keyCode, final KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK) {
			if (!isConversationsOverviewVisable()) {
				showConversationsOverview();
				return false;
			}
		}
		return super.onKeyDown(keyCode, event);
	}

	@Override
	protected void onNewIntent(final Intent intent) {
		if (xmppConnectionServiceBound) {
			if (intent == null) return;
            if (VIEW_CONVERSATION.equals(intent.getType())) handleViewConversationIntent(intent);
			else if (VIEW_CONVERSATIONS.equals(intent.getType())) showConversationsOverview();
		} else {
			setIntent(intent);
		}
	}

	@Override
	public void onStart() {
		super.onStart();
		if (this.xmppConnectionServiceBound) {
			this.onBackendConnected();
		}
		if (conversationList.size() >= 1) {
			this.onConversationUpdate();
		}
		this.isRunning = true;
		this.updateHandler.postDelayed(this.refreshUi,REFRESH_UI_INTERVAL);
	}

	@Override
	public void onStop() {
		super.onStop();
		this.isRunning = false;
	}

	@Override
	public void onSaveInstanceState(final Bundle savedInstanceState) {
		Conversation conversation = getSelectedConversation();
		if (conversation != null) {
			savedInstanceState.putString(STATE_OPEN_CONVERSATION,
					conversation.getUuid());
		}
		savedInstanceState.putBoolean(STATE_PANEL_OPEN,
				isConversationsOverviewVisable());
		if (this.mPendingImageUri != null) {
			savedInstanceState.putString(STATE_PENDING_URI, this.mPendingImageUri.toString());
		}
		super.onSaveInstanceState(savedInstanceState);
	}

	@Override
	void onBackendConnected() {
		updateConversationList();
		if (xmppConnectionService.getAccounts().size() == 0) {
			startActivity(new Intent(this, EditAccountActivity.class));
		} else if (conversationList.size() <= 0) {
			startActivity(new Intent(this, StartConversationActivity.class));
			finish();
		} else if (getIntent() != null
				&& VIEW_CONVERSATION.equals(getIntent().getType())) {
			handleViewConversationIntent(getIntent());
		} else if (mOpenConverstaion != null) {
			selectConversationByUuid(mOpenConverstaion);
			if (mPanelOpen) {
				showConversationsOverview();
			} else {
				if (isConversationsOverviewHideable()) {
					openConversation();
				}
			}
			this.mConversationFragment.reInit(getSelectedConversation());
			mOpenConverstaion = null;
		} else if (getSelectedConversation() != null) {
			this.mConversationFragment.updateMessages();
		} else {
			showConversationsOverview();
			mPendingImageUri = null;
			mPendingFileUri = null;
			setSelectedConversation(conversationList.get(0));
			this.mConversationFragment.reInit(getSelectedConversation());
		}

		if (mPendingImageUri != null) {
			attachImageToConversation(getSelectedConversation(),mPendingImageUri);
			mPendingImageUri = null;
		} else if (mPendingFileUri != null) {
			attachFileToConversation(getSelectedConversation(),mPendingFileUri);
			mPendingFileUri = null;
		}
		ExceptionHelper.checkForCrash(this, this.xmppConnectionService);
		setIntent(new Intent());
	}

	private void handleViewConversationIntent(Intent intent) {
		String uuid = (String) intent.getExtras().get(CONVERSATION);
		String text = intent.getExtras().getString(TEXT, "");
		String nick = intent.getExtras().getString(NICK,null);
		selectConversationByUuid(uuid);
		this.mConversationFragment.reInit(getSelectedConversation());
		if (nick!=null) {
			this.mConversationFragment.highlightInConference(nick);
		} else {
			this.mConversationFragment.appendText(text);
		}
		hideConversationsOverview();
		if (mContentView instanceof SlidingPaneLayout) {
			openConversation();
		}
	}

	private void selectConversationByUuid(String uuid) {
        for (Conversation aConversationList : conversationList) {
            if (aConversationList.getUuid().equals(uuid)) {
                setSelectedConversation(aConversationList);
            }
        }
	}

	@Override
	protected void unregisterListeners() {
		super.unregisterListeners();
		xmppConnectionService.getNotificationService().setOpenConversation(null);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode,
									final Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (resultCode == RESULT_OK) {
			if (requestCode == REQUEST_DECRYPT_PGP) {
				ConversationFragment selectedFragment = (ConversationFragment) getFragmentManager()
						.findFragmentByTag("conversation");
				if (selectedFragment != null) {
					selectedFragment.hideSnackbar();
					selectedFragment.updateMessages();
				}
			} else if (requestCode == REQUEST_ATTACH_IMAGE_DIALOG) {
				mPendingImageUri = data.getData();
				if (xmppConnectionServiceBound) {
					attachImageToConversation(getSelectedConversation(),
							mPendingImageUri);
					mPendingImageUri = null;
				}
			} else if (requestCode == REQUEST_ATTACH_FILE_DIALOG) {
				mPendingFileUri = data.getData();
				if (xmppConnectionServiceBound) {
					attachFileToConversation(getSelectedConversation(),
							mPendingFileUri);
					mPendingFileUri = null;
				}
			} else if (requestCode == REQUEST_SEND_PGP_IMAGE) {

			} else if (requestCode == ATTACHMENT_CHOICE_CHOOSE_IMAGE) {
				attachFile(ATTACHMENT_CHOICE_CHOOSE_IMAGE);
			} else if (requestCode == ATTACHMENT_CHOICE_TAKE_PHOTO) {
				attachFile(ATTACHMENT_CHOICE_TAKE_PHOTO);
			} else if (requestCode == REQUEST_ANNOUNCE_PGP) {
				announcePgp(getSelectedConversation().getAccount(),
						getSelectedConversation());
			} else if (requestCode == REQUEST_ENCRYPT_MESSAGE) {
				// encryptTextMessage();
			} else if (requestCode == REQUEST_IMAGE_CAPTURE && mPendingImageUri != null) {
				if (xmppConnectionServiceBound) {
					attachImageToConversation(getSelectedConversation(),
							mPendingImageUri);
					mPendingImageUri = null;
				}
				Intent intent = new Intent(
						Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
				intent.setData(mPendingImageUri);
				sendBroadcast(intent);
			}
		} else {
			if (requestCode == REQUEST_IMAGE_CAPTURE) {
				mPendingImageUri = null;
			}
		}
	}

	private void attachFileToConversation(Conversation conversation, Uri uri) {
		prepareFileToast = Toast.makeText(getApplicationContext(),
				getText(R.string.preparing_file), Toast.LENGTH_LONG);
		prepareFileToast.show();
		xmppConnectionService.attachFileToConversation(conversation,uri, new UiCallback<Message>() {
			@Override
			public void success(Message message) {
				hidePrepareFileToast();
				xmppConnectionService.sendMessage(message);
			}

			@Override
			public void error(int errorCode, Message message) {
				displayErrorDialog(errorCode);
			}

			@Override
			public void userInputRequried(PendingIntent pi, Message message) {

			}
		});
	}

	private void attachImageToConversation(Conversation conversation, Uri uri) {
		prepareFileToast = Toast.makeText(getApplicationContext(),
				getText(R.string.preparing_image), Toast.LENGTH_LONG);
		prepareFileToast.show();
		xmppConnectionService.attachImageToConversation(conversation, uri,
				new UiCallback<Message>() {

					@Override
					public void userInputRequried(PendingIntent pi,
												  Message object) {
						hidePrepareFileToast();
						ConversationActivity.this.runIntent(pi,
								ConversationActivity.REQUEST_SEND_PGP_IMAGE);
					}

					@Override
					public void success(Message message) {
						xmppConnectionService.sendMessage(message);
					}

					@Override
					public void error(int error, Message message) {
						hidePrepareFileToast();
						displayErrorDialog(error);
					}
				});
	}

	private void hidePrepareFileToast() {
		if (prepareFileToast != null) {
			runOnUiThread(new Runnable() {

				@Override
				public void run() {
					prepareFileToast.cancel();
				}
			});
		}
	}

	public void updateConversationList() {
		xmppConnectionService.populateWithOrderedConversations(conversationList);
		listAdapter.notifyDataSetChanged();
		invalidateOptionsMenu();
	}

	public void runIntent(PendingIntent pi, int requestCode) {
		try {
			this.startIntentSenderForResult(pi.getIntentSender(), requestCode,
					null, 0, 0, 0);
		} catch (final SendIntentException ignored) {
		}
	}

	public void encryptTextMessage(Message message) {
		xmppConnectionService.getPgpEngine().encrypt(message,
				new UiCallback<Message>() {

					@Override
					public void userInputRequried(PendingIntent pi,
												  Message message) {
						ConversationActivity.this.runIntent(pi,
								ConversationActivity.REQUEST_SEND_MESSAGE);
					}

					@Override
					public void success(Message message) {
						message.setEncryption(Message.ENCRYPTION_DECRYPTED);
						xmppConnectionService.sendMessage(message);
					}

					@Override
					public void error(int error, Message message) {

					}
				});
	}

	public boolean forceEncryption() {
		return getPreferences().getBoolean("force_encryption", true);
	}

	public boolean useSendButtonToIndicateStatus() {
		return getPreferences().getBoolean("send_button_status", false);
	}

	public boolean indicateReceived() {
		return getPreferences().getBoolean("indicate_received", true);
	}

	@Override
	public void onAccountUpdate() {
		final ConversationFragment fragment = (ConversationFragment) getFragmentManager()
				.findFragmentByTag("conversation");
		if (fragment != null) {
			runOnUiThread(new Runnable() {

				@Override
				public void run() {
					fragment.updateMessages();
				}
			});
		}
	}

	@Override
	public void onConversationUpdate() {
		runOnUiThread(new Runnable() {

			@Override
			public void run() {
				updateConversationList();
				if (conversationList.size() == 0) {
					startActivity(new Intent(getApplicationContext(),
							StartConversationActivity.class));
					finish();
				}
				ConversationActivity.this.mConversationFragment.updateMessages();
			}
		});
	}

	@Override
	public void onRosterUpdate() {
		runOnUiThread(new Runnable() {

				@Override
				public void run() {
					ConversationActivity.this.mConversationFragment.updateMessages();
                    updateConversationList();
				}
			});
	}
}
