package aenu.ax360e;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    public static final int REQUEST_SELECT_GAME_DIR=6004;
    static final int DELAY_ON_CREATE=0xaeae0000;
    public static final String PREF_GAME_DIR="game_dir";

    ProgressBar progress;
    ProgressTask progress_task;
    Emulator.Config config;
    MainActivityHelper helper;
    GameListAdapter gameListAdapter;
    GameMetadataManager metadataManager;
    Dialog delay_dialog=null;
    final Handler delay_on_create=new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(@NonNull Message msg) {
            if(msg.what!=DELAY_ON_CREATE) return false;
            if(delay_dialog!=null){
                delay_dialog.dismiss();
                delay_dialog=null;
            }
            on_create();
            return true;
        }
    });

    void show_device_unsupport_vulkan_dialog(){

        AlertDialog.Builder ab=new AlertDialog.Builder(this);
        ab.setPositiveButton(R.string.quit, new DialogInterface.OnClickListener(){

            @Override
            public void onClick(DialogInterface p1, int p2)
            {
                p1.cancel();
                finish();
            }


        });
        Dialog d=ab.create();
        d.setCanceledOnTouchOutside(false);
        d.setOnKeyListener(new DialogInterface.OnKeyListener(){
            @Override
            public boolean onKey(DialogInterface p1, int p2, KeyEvent p3){
                return true;
            }
        });
        d.show();
    }

    void _on_create(){
        setContentView(R.layout.activity_main);

        // Set the MaterialToolbar as the support action bar so the options menu appears
        com.google.android.material.appbar.MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Initialize metadata manager and adapter
        metadataManager = new GameMetadataManager(this);
        gameListAdapter = new GameListAdapter(this, metadataManager);
        gameListAdapter.setOnGameClickListener(new GameListAdapter.OnGameClickListener() {
            @Override
            public void onGameClick(Emulator.GameInfo game) {
                Intent intent = new Intent("aenu.intent.action.AX360E");
                intent.setPackage(getPackageName());
                intent.putExtra(EmulatorActivity.EXTRA_GAME_URI, game.uri);
                startActivity(intent);
            }

            @Override
            public void onGameLongClick(Emulator.GameInfo game) {
                String[] options = {
                    getString(R.string.create_shortcut_label),
                    getString(R.string.game_info_label),
                    "Download Cover Art",
                    "Set Cover Art from URL"
                };
                new AlertDialog.Builder(MainActivity.this)
                    .setTitle(game.name)
                    .setItems(options, (dialog, which) -> {
                        if (which == 0) {
                            createShortcutForGame(game);
                        } else if (which == 1) {
                            String uriDisplay = game.uri != null ? game.uri : "No URI";
                            new AlertDialog.Builder(MainActivity.this)
                                .setTitle(game.name)
                                .setMessage(uriDisplay)
                                .setPositiveButton(android.R.string.ok, null)
                                .show();
                        } else if (which == 2) {
                            downloadCoverArt(game);
                        } else if (which == 3) {
                            showCoverArtUrlDialog(game);
                        }
                    })
                    .show();
            }
        });

        // Initialize Material3 helper and wire up all UI components
        helper = new MainActivityHelper(this);
        helper.initializeViews(findViewById(android.R.id.content));
        helper.setAdapter(gameListAdapter);

        // Wire up the "Set Game Directory" button in the empty state
        View btnSetGameDir = findViewById(R.id.btn_set_game_dir);
        if (btnSetGameDir != null) {
            btnSetGameDir.setOnClickListener(v -> request_game_dir_select(MainActivity.this));
        }

        if(getPackageName().equals("aenu.ax360e")) {
            // Context menu registered on adapter items via long-click above
        }
        show_game_list();
    }

    void on_create(){
        _on_create();
        checkStoragePermission();
    }

    private void checkStoragePermission() {
        android.util.Log.d("MainActivity", "checkStoragePermission called");
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            boolean isManager = android.os.Environment.isExternalStorageManager();
            android.util.Log.d("MainActivity", "isExternalStorageManager: " + isManager);
            if (!isManager) {
                android.util.Log.d("MainActivity", "Showing storage permission dialog");
                new AlertDialog.Builder(this)
                        .setTitle("Storage Permission Required")
                        .setMessage("This app requires 'All Files Access' to read game files from your internal storage on Android 11 and above.\n\nPlease grant this permission in the next screen.")
                        .setPositiveButton("Grant", (dialog, which) -> {
                            try {
                                Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                                intent.addCategory("android.intent.category.DEFAULT");
                                intent.setData(Uri.parse(String.format("package:%s", getPackageName())));
                                startActivity(intent);
                            } catch (Exception e) {
                                Intent intent = new Intent();
                                intent.setAction(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                                startActivity(intent);
                            }
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            }
        }
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if(!Application.device_support_vulkan()){
            show_device_unsupport_vulkan_dialog();
            return;
        }

        if(!Application.should_delay_load()){
            on_create();
            return;
        }

        delay_dialog=ProgressTask.create_progress_dialog( this,getString(R.string.loading));
        delay_dialog.show();
        new Thread(){
            @Override
            public void run() {
                try {
                    Thread.sleep(500);
                    Emulator.load_library();
                    Thread.sleep(100);
                    delay_on_create.sendEmptyMessage(DELAY_ON_CREATE);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }.start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(progress_task!=null){
            progress_task.force_close();
            progress_task=null;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu){
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.main,menu);
        return true;
    }

    void createShortcutForGame(Emulator.GameInfo meta_info) {
        ShortcutManager shortcutManager=getSystemService(ShortcutManager.class);
        Bitmap icon;
        if(meta_info.icon!=null)
            icon=BitmapFactory.decodeByteArray(meta_info.icon,0,meta_info.icon.length);
        else
            icon=BitmapFactory.decodeResource(getResources(),R.drawable.app_icon);

        Intent intent=new Intent(this,EmulatorActivity.class);
        intent.setAction(Intent.ACTION_VIEW);
        intent.putExtra(EmulatorActivity.EXTRA_GAME_URI, meta_info.uri);

        shortcutManager.requestPinShortcut(new ShortcutInfo.Builder(this, meta_info.name)
                .setShortLabel(meta_info.name)
                .setIcon(Icon.createWithBitmap(icon))
                .setIntent(intent)
                .build(), null);
    }

    void downloadCoverArt(Emulator.GameInfo game) {
        CoverArtScraper scraper = new CoverArtScraper(this);

        // Show progress dialog
        AlertDialog progressDialog = new AlertDialog.Builder(this)
            .setTitle("Downloading Cover Art")
            .setMessage("Please wait...")
            .setCancelable(false)
            .create();
        progressDialog.show();

        scraper.downloadCoverArt(game.name, game.uri, new CoverArtScraper.CoverArtCallback() {
            @Override
            public void onSuccess(File coverArtFile) {
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    metadataManager.setCoverArtPath(game.uri, coverArtFile.getAbsolutePath());
                    gameListAdapter.notifyDataSetChanged();
                    new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Success")
                        .setMessage("Cover art downloaded successfully")
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
                });
            }

            @Override
            public void onFailure(String error) {
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Download Failed")
                        .setMessage("Could not download cover art automatically. You can try setting it from a URL instead.")
                        .setPositiveButton(android.R.string.ok, null)
                        .setNegativeButton("Set from URL", (dialog, which) -> showCoverArtUrlDialog(game))
                        .show();
                });
            }
        });
    }

    void showCoverArtUrlDialog(Emulator.GameInfo game) {
        android.widget.EditText input = new android.widget.EditText(this);
        input.setHint("Enter image URL");

        new AlertDialog.Builder(this)
            .setTitle("Set Cover Art from URL")
            .setMessage("Enter the URL of the cover art image:")
            .setView(input)
            .setPositiveButton("Download", (dialog, which) -> {
                String url = input.getText().toString().trim();
                if (!url.isEmpty()) {
                    downloadCoverArtFromUrl(game, url);
                }
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    void downloadCoverArtFromUrl(Emulator.GameInfo game, String imageUrl) {
        CoverArtScraper scraper = new CoverArtScraper(this);

        AlertDialog progressDialog = new AlertDialog.Builder(this)
            .setTitle("Downloading Cover Art")
            .setMessage("Please wait...")
            .setCancelable(false)
            .create();
        progressDialog.show();

        scraper.downloadCoverArtFromUrl(imageUrl, game.uri, new CoverArtScraper.CoverArtCallback() {
            @Override
            public void onSuccess(File coverArtFile) {
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    metadataManager.setCoverArtPath(game.uri, coverArtFile.getAbsolutePath());
                    gameListAdapter.notifyDataSetChanged();
                    new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Success")
                        .setMessage("Cover art downloaded successfully")
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
                });
            }

            @Override
            public void onFailure(String error) {
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Download Failed")
                        .setMessage("Failed to download image: " + error)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
                });
            }
        });
    }


    static Intent get_file_manager_intent(String pkg_name)
    {
        Intent it=new Intent(Intent.ACTION_VIEW);
        it.setClassName(pkg_name, "com.android.documentsui.files.FilesActivity");
        it.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return it;
    }

    static void open_file_manager(Activity activity)
    {

        try{
            activity.startActivity(get_file_manager_intent("com.android.documentsui"));
            return;
        }
        catch(Exception e){
        }
        try{
            activity.startActivity(get_file_manager_intent("com.google.android.documentsui"));
            return;
        }
        catch(Exception e){
        }
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item){

        int item_id=item.getItemId();
        if(item_id==R.id.menu_refresh_list){
            refresh_game_list();
            return true;
        }
        else if(item_id==R.id.menu_key_mappers){
            startActivity(new Intent(this,KeyMapActivity.class));
            return true;
        }
        else if(item_id==R.id.menu_set_game_dir){
            request_game_dir_select(this);
            return true;
        }
        else if(item_id==R.id.menu_open_file_mgr){
            open_file_manager( this);
            return true;
        }
        else if(item_id==R.id.menu_about){
            startActivity(new Intent(this,AboutActivity.class));
            return true;
        }
        else if(item_id==R.id.menu_settings){
            startActivity(new Intent(this,EmulatorSettings.class));
            return true;
        }
        else if(item_id==R.id.menu_virtual_pad_edit){
            startActivity(new Intent(this,VirtualControlEdit.class));
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;

        Uri uri = data.getData();

        if(requestCode == REQUEST_SELECT_GAME_DIR){
            save_pref_game_dir(this,uri);
            refresh_game_list();
            return;
        }
    }
    static void request_game_dir_select(Activity activity){
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        activity.startActivityForResult(intent, REQUEST_SELECT_GAME_DIR);
    }
    GameMetaInfoAdapter adapter=null;
    void refresh_game_list(){

        (progress_task=new ProgressTask( this)
                .set_progress_message(getString( R.string.game_list_loading))
                .set_done_task(new ProgressTask.UI_Task() {
                    @Override
                    public void run() {
                        show_game_list();
                        progress_task=null;
                    }
                }))
                .call( new ProgressTask.Task() {
                    @Override
                    public void run(ProgressTask task) {
                        adapter=null;
                        ArrayList<Emulator.GameInfo> metas=GameMetaInfoAdapter.refresh_game_list( MainActivity.this);
                        adapter=new GameMetaInfoAdapter(MainActivity.this,metas);

                        /*File json_file=get_game_list_file();
                        if(json_file.exists())
                            json_file.delete();
                        GameMetaInfoAdapter.save_game_list_to_json_file(json_file,GameMetaInfoAdapter.refresh_game_list(MainActivity.this));
                        */task.task_handler.sendEmptyMessage(ProgressTask.TASK_DONE);
                    }
                });
        //show_game_list();
    }

    private void show_game_list(){
        if(adapter==null){
            refresh_game_list();
            return;
        }
        // Convert to list and set on RecyclerView adapter
        if (gameListAdapter != null) {
            gameListAdapter.setGameList(adapter.metas);
        }
        // Show/hide empty state
        if (helper != null) {
            helper.showEmptyState(adapter.metas == null || adapter.metas.isEmpty());
        }
    }
    static void save_pref_game_dir(Context ctx,Uri uri){
        try{
            ctx.getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);
            SharedPreferences.Editor editor= PreferenceManager.getDefaultSharedPreferences(ctx).edit();
            editor.putString(PREF_GAME_DIR,uri.toString());
            editor.apply();
        }
        catch(Exception e){
            android.util.Log.e("ax360e", "Failed to save game dir preference", e);
        }
    }

    static Uri load_pref_game_dir(Context ctx){
        try{
            String uri_str=PreferenceManager.getDefaultSharedPreferences(ctx).getString(PREF_GAME_DIR,null);
            if(uri_str==null)
                return null;
            Uri uri= Uri.parse(uri_str);
            ctx.getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);
            return uri;
        }
        catch(Exception e){
            android.util.Log.e("ax360e", "Failed to load game dir preference", e);
            return null;
        }
    }
    private static class GameMetaInfoAdapter extends BaseAdapter {

        private static class Filter{

            static boolean is_god_game(String file_name){
                /*int file_name_len = file_name.length();
                if(file_name_len == 20||file_name_len==42) {
                    final String HEX_CHARS = "0123456789ABCDEF";
                    for(int i = 0; i < file_name_len; i++){
                        char c = file_name.charAt(i);
                        if(HEX_CHARS.indexOf(c) == -1) return false;
                    }
                    return true;
                }
                else return false;*/
                return file_name.indexOf('.')==-1;
            }

            static boolean is_iso_file(String file_name){
                return file_name.endsWith(".iso");
            }

            static boolean is_zar_file(String file_name){
                return file_name.endsWith(".zar");
            }

            static DocumentFile get_default_xex_file(DocumentFile dir){
                DocumentFile[] files=dir.listFiles();
                if(files == null) return null;
                if(files.length == 0) return null;
                for(DocumentFile file:files){
                    if(!file.isFile()) continue;
                    if(file.getName().toLowerCase().equals("default.xex")) return file;
                }
                return null;
            }

            static boolean is_xex_game(DocumentFile dir){
                return get_default_xex_file(dir) != null;
            }
        };

        ArrayList<Emulator.GameInfo> metas;
        private final MainActivity context_;
        private GameMetaInfoAdapter(MainActivity context,ArrayList<Emulator.GameInfo> metas){
            context_=context;
            this.metas=metas;
        }

        static String load_file_as_string(File file) throws IOException {
            try (FileInputStream fis=new FileInputStream(file);
                 ByteArrayOutputStream baos=new ByteArrayOutputStream()) {
                byte[] buffer=new byte[16384];
                int n;
                while ((n=fis.read(buffer))!=-1){
                    baos.write(buffer,0,n);
                }
                return baos.toString();
            }
        }
        /*static ArrayList<Emulator.GameInfo> load_game_list_from_json_file(File json) throws JSONException, IOException {
            String json_str=load_file_as_string(json);
            return load_game_list_from_json(json_str);
        }

        static ArrayList<Emulator.GameInfo> load_game_list_from_json(String json_str) throws JSONException {
            ArrayList<Emulator.GameInfo> metas=new ArrayList<Emulator.GameInfo>();
            JSONArray game_list=new JSONArray(json_str);
            for(int i=0;i<game_list.length();i++){
                JSONObject game_info=game_list.getJSONObject(i);
                Emulator.GameInfo meta=Emulator.GameInfo.from_json(game_info);
                metas.add(meta);
            }
            return metas;
        }

        static void save_game_list_to_json_file(File json,ArrayList<Emulator.GameInfo> metas){
            try (FileOutputStream fos=new FileOutputStream(json)) {
                fos.write(save_game_list_to_json(metas).getBytes());
            } catch (IOException | JSONException e) {
                android.util.Log.e("ax360e", "Failed to save game list", e);
            }
        }

        static String save_game_list_to_json(ArrayList<Emulator.GameInfo> metas) throws JSONException {
            JSONArray game_list=new JSONArray();
            for(Emulator.GameInfo meta:metas){
                game_list.put(Emulator.GameInfo.to_json(meta));
            }
            return game_list.toString();
        }*/



        static ArrayList<Emulator.GameInfo> refresh_game_list(MainActivity context){
            ArrayList<Emulator.GameInfo> metas=new ArrayList<Emulator.GameInfo>();
            Uri uri=context.load_pref_game_dir(context);
            if(uri==null)
                return metas;
            DocumentFile iso_dir=DocumentFile.fromTreeUri(context, uri);
            if(iso_dir==null||!iso_dir.exists())
                return metas;
            DocumentFile[] files=iso_dir.listFiles();
            for(DocumentFile file:files){
                String fileName = file.getName();
                if(fileName == null) continue;
                if(file.isDirectory()){
                    DocumentFile default_xex_file=Filter.get_default_xex_file(file);
                    if(default_xex_file==null) continue;
                    Emulator.GameInfo meta=new Emulator.GameInfo();
                    meta.uri=default_xex_file.getUri().toString();
                    meta.name=fileName;
                    metas.add(meta);
                }
                else{
                    if(Filter.is_iso_file(fileName)){
                        Emulator.GameInfo meta=new Emulator.GameInfo();
                        meta.name=fileName.length()>=4?fileName.substring(0,fileName.length()-4):fileName;
                        meta.uri=file.getUri().toString();
                        metas.add(meta);
                    }
                    if(Filter.is_zar_file(fileName)){
                        Emulator.GameInfo meta=new Emulator.GameInfo();
                        meta.name=fileName.length()>=4?fileName.substring(0,fileName.length()-4):fileName;
                        meta.uri=file.getUri().toString();
                        metas.add(meta);
                    }
                    else if(Filter.is_god_game(fileName)){
                        Emulator.GameInfo meta=Emulator.get.meta_info_from_god_game(context,file.getUri().toString());
                        if(meta!=null)
                        metas.add(meta);
                    }
                }
            }

            return metas;
        }


        public Emulator.GameInfo getMetaInfo(int pos){
            return metas.get(pos);
        }



        @Override
        public int getCount(){
            return metas.size();
        }

        @Override
        public Object getItem(int p1){
            return null;
        }

        @Override
        public long getItemId(int p1){
            return p1;
        }

        private LayoutInflater getLayoutInflater(){
            return (LayoutInflater)context_.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        /*Bitmap to_gray_bmp(Bitmap src){
            int w=src.getWidth();
            int h=src.getHeight();
            Bitmap bmp=Bitmap.createBitmap(w,h,src.getConfig());
            ColorMatrix cm=new ColorMatrix();
            cm.setSaturation(0);
            Canvas canvas=new Canvas(bmp);
            Paint paint=new Paint();
            paint.setColorFilter(new ColorMatrixColorFilter(cm));
            canvas.drawBitmap(src,0,0,paint);
            return bmp;
        }*/

        @Override
        public View getView(int pos,View curView,ViewGroup p3){

            if(curView==null){
                curView=getLayoutInflater().inflate(R.layout.game_item,null);
            }

            Emulator.GameInfo mi=metas.get(pos);

            ImageView icon=(ImageView)curView.findViewById(R.id.game_icon);
            if(mi.icon==null)
                icon.setImageResource(R.drawable.app_icon);
            else {
                Bitmap icon_bmp= BitmapFactory.decodeByteArray(mi.icon,0,mi.icon.length);
                icon.setImageBitmap(icon_bmp);
            }

            TextView name=(TextView)curView.findViewById(R.id.game_name);
            if(mi.name!=null)
                name.setText(mi.name);
            else{
                DocumentFile file=DocumentFile.fromSingleUri(context_,Uri.parse(mi.uri));
                name.setText(file.getName());
            }

            return curView;
        }
    }//!FileAdapter
}