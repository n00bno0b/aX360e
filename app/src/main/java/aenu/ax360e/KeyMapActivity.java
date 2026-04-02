// SPDX-License-Identifier: WTFPL

package aenu.ax360e;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.KeyEvent;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import aenu.view.SVListView;

public class KeyMapActivity extends AppCompatActivity {

	static class KeyConfig {
        public int name_res_id;
        public int bind_value;
		public int keyCode;
		public int pref_id;
		KeyConfig(int name_res_id,int bind_value,int keyCode,int pref_id){
			this.name_res_id=name_res_id;
			this.bind_value=bind_value;
			this.keyCode=keyCode;
			this.pref_id=pref_id;
		}
    }
	static KeyConfig key_configs[]={
			new KeyConfig(R.string.left,0,KeyEvent.KEYCODE_DPAD_LEFT,0x60000001),
			new KeyConfig(R.string.up,1,KeyEvent.KEYCODE_DPAD_UP,0x60000002),
			new KeyConfig(R.string.right,2,KeyEvent.KEYCODE_DPAD_RIGHT,0x60000003),
			new KeyConfig(R.string.down,3,KeyEvent.KEYCODE_DPAD_DOWN,0x60000004),
			new KeyConfig(R.string.a,4,96,0x60000005),
			new KeyConfig(R.string.b,5,97,0x60000006),
			new KeyConfig(R.string.x,6,99,0x60000007),
			new KeyConfig(R.string.y,7,100,0x60000008),
			new KeyConfig(R.string.back,8,109,0x60000009),
			new KeyConfig(R.string.start,9,108,0x6000000a),
			new KeyConfig(R.string.lshoulder,10,102,0x6000000b),
			new KeyConfig(R.string.rshoulder,11,103,0x6000000c),
			new KeyConfig(R.string.lthumbpress,12,104,0x6000000d),
			new KeyConfig(R.string.rthumbpress,13,105,0x6000000e),
			new KeyConfig(R.string.ltrigger,14,0,0x6000000f),
			new KeyConfig(R.string.rtrigger,15,0,0x60000010),
	};
    
	SharedPreferences sp;
    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
		sp=getSharedPreferences();
		update_config();
		setContentView(R.layout.activity_keymap);
		((SVListView)findViewById(R.id.keymap_list)).setOnItemClickListener(click_l);
		((Button)findViewById(R.id.keymap_reset)).setOnClickListener(reset_l);
		((CheckBox)findViewById(R.id.enable_vibrator)).setChecked(sp.getBoolean("enable_vibrator",false));
		((CheckBox)findViewById(R.id.enable_vibrator)).setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener(){
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				sp.edit().putBoolean("enable_vibrator",isChecked).commit();
			}
		});
		/*
		final String vibrator_duration_hiht=getString(R.string.vibrator_duration)+":  ";
		((TextView)findViewById(R.id.vibrator_duration_label)).setText(vibrator_duration_hiht+sp.getInt("vibrator_duration",25));
		((SeekBar)findViewById(R.id.vibrator_duration)).setProgress(sp.getInt("vibrator_duration",25));
		((SeekBar)findViewById(R.id.vibrator_duration)).setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
			@Override
			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
				((TextView)findViewById(R.id.vibrator_duration_label)).setText(vibrator_duration_hiht+progress);
			}
			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {}
			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {}
		});*/

		refresh_view();
    }
	
	void refresh_view(){
		((SVListView)findViewById(R.id.keymap_list)).setAdapter(new KeyListAdapter(this,KeyMapConfig.KEY_NAMEIDS,get_all_key_mapper_values()));
	}
	
	void update_config(){
		final SharedPreferences sPrefs = PreferenceManager.getDefaultSharedPreferences(KeyMapActivity.this);
		SharedPreferences.Editor sPrefsEditor = sPrefs.edit();

		for(int i=0;i<KeyMapConfig.KEY_NAMEIDS.length;i++){
			String key_n=Integer.toString(KeyMapConfig.KEY_NAMEIDS[i]);
			int default_v=KeyMapConfig.DEFAULT_KEYMAPPERS[i];
			int key_v=sPrefs.getInt(key_n,default_v);
			sPrefsEditor.putInt(key_n,key_v);
		}
		
		sPrefsEditor.commit();
	}
	
	int[] get_all_key_mapper_values(){
		final SharedPreferences sPrefs = PreferenceManager.getDefaultSharedPreferences(KeyMapActivity.this);
		
		int[] key_values=new int[KeyMapConfig.KEY_NAMEIDS.length];
		for(int i=0;i<KeyMapConfig.KEY_NAMEIDS.length;i++){
			String key_n=Integer.toString(KeyMapConfig.KEY_NAMEIDS[i]);
			key_values[i]=sPrefs.getInt(key_n,0);
		}
		return key_values;
	}
	
	private final AdapterView.OnItemClickListener click_l=new AdapterView.OnItemClickListener(){
		@Override
		public void onItemClick(final AdapterView<?> l, View v, final int position,long id)
		{
			AlertDialog.Builder builder = new AlertDialog.Builder(KeyMapActivity.this);
            builder.setMessage(R.string.press_a_key);
            builder.setNegativeButton(R.string.clear, new DialogInterface.OnClickListener(){
					@Override
					public void onClick(DialogInterface p1, int p2)
					{
						final SharedPreferences sPrefs = PreferenceManager.getDefaultSharedPreferences(KeyMapActivity.this);
						SharedPreferences.Editor sPrefsEditor = sPrefs.edit();
						sPrefsEditor.putInt((String)l.getItemAtPosition(position),0);
						sPrefsEditor.commit();
						refresh_view();
					}
				});
			builder.setOnKeyListener(new DialogInterface.OnKeyListener(){
					@Override
					public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event)
					{
						if (event.getAction() == KeyEvent.ACTION_DOWN) {
							final SharedPreferences sPrefs = PreferenceManager.getDefaultSharedPreferences(KeyMapActivity.this);
							SharedPreferences.Editor sPrefsEditor = sPrefs.edit();
							sPrefsEditor.putInt((String)l.getItemAtPosition(position),keyCode);
							sPrefsEditor.commit();
							dialog.dismiss();
							refresh_view();
							return true;
						}
						return false;
					}
			});
            AlertDialog dialog = builder.create();
            dialog.show();  
		}
	};

	private final View.OnClickListener reset_l=new View.OnClickListener(){
		@Override
		public void onClick(View v)
		{
			for(int i=0;i<KeyMapConfig.KEY_NAMEIDS.length;i++){
				String key_n=Integer.toString(KeyMapConfig.KEY_NAMEIDS[i]);
				int default_v=KeyMapConfig.DEFAULT_KEYMAPPERS[i];
				sp.edit().putInt(key_n,default_v).commit();
			}
			refresh_view();
		}
	};

    private SharedPreferences getSharedPreferences() {
        return PreferenceManager.getDefaultSharedPreferences(this);
    }
	
	private static class KeyListAdapter extends BaseAdapter {

        private int[] keyNameIdList_;
		private int[] valueList_;
        private Context context_; 

        private KeyListAdapter(Context context,int[] keyList,int[] valueList){
            context_=context;
			this.keyNameIdList_=keyList;
			this.valueList_=valueList;
		}

        public String getKey(int pos){
            return Integer.toString(keyNameIdList_[pos]);
        }

		public String getKeyName(int pos){
            return context_.getString(keyNameIdList_[pos]);
        }

        @Override
        public int getCount(){
            return keyNameIdList_.length;
        }

        @Override
        public Object getItem(int p1){
            return getKey(p1);
        }

        @Override
        public long getItemId(int p1){
            return p1;
        }

        @Override
        public View getView(int pos,View curView,ViewGroup p3){

            
            if(curView==null){
                curView=new TextView(context_,null,androidx.appcompat.R.attr.textAppearanceListItem);
            }
			
			TextView text=(TextView)curView;

            text.setText(getKeyName(pos)+":    "+valueList_[pos]);

            return curView;
        } 
    }//!FileAdapter
}
