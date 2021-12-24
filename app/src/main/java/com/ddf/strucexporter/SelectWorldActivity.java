package com.ddf.strucexporter;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import java.io.File;
import java.io.FileFilter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;

import com.permissionx.guolindev.PermissionX;
import com.permissionx.guolindev.callback.ExplainReasonCallback;
import com.permissionx.guolindev.callback.ForwardToSettingsCallback;
import com.permissionx.guolindev.callback.RequestCallback;
import com.permissionx.guolindev.request.ExplainScope;
import com.permissionx.guolindev.request.ForwardScope;

import org.iq80.leveldb.CompressionType;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.Options;
import org.iq80.leveldb.impl.Iq80DBFactory;
import org.iq80.leveldb.table.BloomFilterPolicy;

public class SelectWorldActivity extends FragmentActivity {
	private final List<World> worlds = new ArrayList<>();
	
	private LinearLayout loadingLinearLayout;
	private ListView worldListView;
	private WorldListAdapter adapter;
	private LoadWorldsTask task;

	private byte[] structureKey;
	private byte[] structureValue;
	private World ignoredWorld;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.select_world);
		loadingLinearLayout = findViewById(R.id.loading_linear_layout);
		worldListView = findViewById(R.id.world_list_view);
		adapter = new WorldListAdapter();
		worldListView.setAdapter(adapter);
		worldListView.setOnItemClickListener(new WorldsListItemClickListener());

		Intent intent = getIntent();
		structureKey = intent.getByteArrayExtra("structureKey");
		structureValue = intent.getByteArrayExtra("structureValue");
		ignoredWorld = (World) intent.getSerializableExtra("ignoredWorld");

		String title = intent.getStringExtra("title");
		if (title != null) {
			setTitle(title);
			getActionBar().setDisplayHomeAsUpEnabled(true);
		}
		if (structureKey != null && structureValue != null) {
			return;
		}

		SharedPreferences preferences = getPreferences(Context.MODE_PRIVATE);
		if (preferences.getBoolean("firstRun", true)) {
			showWarningDialog();
			preferences.edit().putBoolean("firstRun", false).apply();
		}
    }

    @SuppressWarnings("deprecation")
	@Override
	protected void onResume() {
		super.onResume();
		PermissionX.init(this)
				.permissions(Manifest.permission.READ_EXTERNAL_STORAGE)
				.explainReasonBeforeRequest()
				.onExplainRequestReason(new ExplainReasonCallback() {
					@Override
					public void onExplainReason(ExplainScope scope, List<String> deniedList) {
						scope.showRequestReasonDialog(deniedList, "需要申请以下权限以加载存档", "确定");
					}
				})
				.onForwardToSettings(new ForwardToSettingsCallback() {
					@Override
					public void onForwardToSettings(ForwardScope scope, List<String> deniedList) {
						scope.showForwardToSettingsDialog(deniedList, "请到设置中手动授予相关权限", "确定");
					}
				})
				.request(new RequestCallback() {
					@Override
					public void onResult(boolean allGranted, List<String> grantedList, List<String> deniedList) {
						if (allGranted) {
							loadWorlds(new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/games/com.mojang/minecraftWorlds"));
						} else {

						}
					}
				});
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		task.cancel();
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if(item.getItemId() == android.R.id.home) {
			finish();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}


	private void showWarningDialog() {
		new AlertDialog.Builder(this)
				.setCancelable(false)
				.setTitle(R.string.warning)
				.setMessage(R.string.warning_content)
				.setPositiveButton(R.string.confirm, null)
				.show();
	}

	Handler handler = new Handler(Looper.myLooper()) {
		@Override
		@SuppressWarnings("unchecked")
		public void handleMessage(Message msg) {
			switch (msg.what) {
				case 0:
					worlds.clear();
					worlds.addAll((List<World>) msg.obj);
					Collections.sort(worlds);
					loadingLinearLayout.setVisibility(View.GONE);
					worldListView.setVisibility(View.VISIBLE);
					adapter.notifyDataSetChanged();
					break;
				case 1:
					worlds.clear();
					adapter.notifyDataSetChanged();
					break;
			}

		}
	};

	private void loadWorlds(File... dirs) {
		worldListView.setVisibility(View.GONE);
		loadingLinearLayout.setVisibility(View.VISIBLE);
		if (task != null) {
			task.cancel();
		}
		task = new LoadWorldsTask(handler, ignoredWorld, dirs);
		task.start();
	}

	static class LoadWorldsTask extends Thread {
		private final Handler handler;
		private final File[] dirs;
		private World ignored;
		private volatile boolean canceled = false;

		LoadWorldsTask(Handler handler, World ignored, File... dirs) {
			this.handler = handler;
			this.ignored = ignored;
			this.dirs = dirs;
		}

		@Override
		public void run() {
			if (canceled) return;
			List<World> worldList = new ArrayList<>();
			for (File file : dirs) {
				if (canceled) return;
				File[] worldDirs = file.listFiles(new FileFilter() {
					@Override
					public boolean accept(File file) {
						return file.isDirectory();
					}
				});
				if (worldDirs == null) {
					handler.sendEmptyMessage(1);
					return;
				}
				for (File worldDir : worldDirs) {
					if (canceled) return;
					//Read world name
					String worldName = null;
					File worldnametxt = new File(worldDir, "levelname.txt");
					File leveldat = new File(worldDir, "level.dat");
					if (worldnametxt.exists()) {
						worldName = new String(Util.readAll(worldnametxt), StandardCharsets.UTF_8);
					} else if (leveldat.exists()) {
						//TODO: Read world name from level.dat
					} else {
						continue;
					}

					//Find db
					File db = new File(worldDir, "db");
					if (!db.exists() || !db.isDirectory()) {
						continue;
					}

					World world = new World(worldDir, leveldat, worldName, db);
					if (world.equals(ignored)) {
						continue;
					}
					worldList.add(world);
				}
			}

			Message message = new Message();
			message.what = 0;
			message.obj = worldList;
			if (canceled) return;
			handler.sendMessage(message);
		}

		public void cancel() {
			canceled = true;
			handler.sendEmptyMessage(1);
		}
	}
	
	class WorldListAdapter extends BaseAdapter {
		private final LayoutInflater layoutInflater = LayoutInflater.from(SelectWorldActivity.this);
		
		@Override
		public int getCount() {
			return worlds.size();
		}

		@Override
		public World getItem(int position) {
			return worlds.get(position);
		}

		@Override
		public long getItemId(int position) {
			 return position;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			if (convertView == null) {
				convertView = layoutInflater.inflate(android.R.layout.simple_list_item_2, null);
			}
			TextView text1 = convertView.findViewById(android.R.id.text1);
			TextView text2 = convertView.findViewById(android.R.id.text2);
			text1.setText(getItem(position).getWorldName());
			text2.setText(getItem(position).getWorldDir().getName());
			return convertView;
		}
	}
	
	class WorldsListItemClickListener implements AdapterView.OnItemClickListener {
		@Override
		public void onItemClick(AdapterView<?> adapterView, View view, int position, long id) {
			World world = worlds.get(position);
			if (structureKey != null && structureValue != null) {
				Options options = new Options();
				options.filterPolicy(new BloomFilterPolicy(10));
				options.writeBufferSize(4 * 1024 * 1024);
				options.compressionType(CompressionType.ZLIB_RAW);

				try (DB db = Iq80DBFactory.factory.open(world.getDbDir(), options)){
					db.put(structureKey, structureValue);
				} catch (Exception ignored) {}

				finish();
				return;
			}
			Intent intent = new Intent(SelectWorldActivity.this, ExportStructureActivity.class);
			intent.putExtra("world", world);
			SelectWorldActivity.this.startActivity(intent);
		}
	}
	
}
