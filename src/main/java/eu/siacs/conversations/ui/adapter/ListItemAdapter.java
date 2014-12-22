package eu.siacs.conversations.ui.adapter;

import java.util.List;

import info.upperechelon.chat.R;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.ListItem;
import eu.siacs.conversations.entities.Presences;
import eu.siacs.conversations.ui.XmppActivity;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

public class ListItemAdapter extends ArrayAdapter<ListItem> {

	protected XmppActivity activity;
	protected boolean showDynamicTags = false;

	public ListItemAdapter(XmppActivity activity, List<ListItem> objects) {
		super(activity, 0, objects);
		this.activity = activity;
		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(activity);
		this.showDynamicTags = preferences.getBoolean("show_dynamic_tags",false);
	}

	@Override
	public View getView(int position, View view, ViewGroup parent) {
		LayoutInflater inflater = (LayoutInflater) getContext()
				.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		ListItem item = getItem(position);
		if (view == null) {
			view = inflater.inflate(R.layout.contact, parent, false);
		}
		TextView name = (TextView) view.findViewById(R.id.contact_display_name);
		TextView jid = (TextView) view.findViewById(R.id.contact_jid);
		ImageView picture = (ImageView) view.findViewById(R.id.contact_photo);
		SurfaceView statusBar = (SurfaceView) view.findViewById(R.id.contact_status);
		LinearLayout tagLayout = (LinearLayout) view.findViewById(R.id.tags);

		List<ListItem.Tag> tags = item.getTags();
		if (tags.size() == 0 || !this.showDynamicTags) {
			tagLayout.setVisibility(View.GONE);
		} else {
			tagLayout.setVisibility(View.VISIBLE);
			tagLayout.removeAllViewsInLayout();
			for(ListItem.Tag tag : tags) {
				TextView tv = (TextView) inflater.inflate(R.layout.list_item_tag,tagLayout,false);
				tv.setText(tag.getName());
				tv.setBackgroundColor(tag.getColor());
				tagLayout.addView(tv);
			}
		}

		if (item instanceof Contact) {
			final Contact contact = (Contact) item;
			statusBar.setVisibility(View.VISIBLE);
			switch (contact.getMostAvailableStatus()) {
				case Presences.CHAT:
				case Presences.ONLINE:
					statusBar.setBackgroundColor(activity.mColorOnline);
					break;
				case Presences.AWAY:
				case Presences.XA:
					statusBar.setBackgroundColor(activity.mColorOrange);
					break;
				case Presences.DND:
					statusBar.setBackgroundColor(activity.mColorRed);
					break;
				case Presences.OFFLINE:
				default:
					statusBar.setBackgroundColor(activity.mColorGray);
					break;
			}
		} else {
			statusBar.setVisibility(View.GONE);
		}

		jid.setText(item.getJid().toString());
		name.setText(item.getDisplayName());
		picture.setImageBitmap(activity.avatarService().get(item,
				activity.getPixel(48)));
		return view;
	}

}
