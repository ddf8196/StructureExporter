package com.ddf.strucexporter;
import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.iq80.leveldb.CompressionType;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.DBIterator;
import org.iq80.leveldb.Options;
import org.iq80.leveldb.impl.Iq80DBFactory;
import org.iq80.leveldb.table.BloomFilterPolicy;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.widget.ListView;

import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.FragmentActivity;

import com.permissionx.guolindev.PermissionX;
import com.permissionx.guolindev.callback.ExplainReasonCallback;
import com.permissionx.guolindev.callback.ForwardToSettingsCallback;
import com.permissionx.guolindev.callback.RequestCallback;
import com.permissionx.guolindev.request.ExplainScope;
import com.permissionx.guolindev.request.ForwardScope;

public class ExportStructureActivity extends FragmentActivity {
	private World world;
	private volatile DB db;
	private List<byte[]> structureKeys = new ArrayList<>();
	private String fileName;
	private byte[] data;

	private LoadStructuresTask task;
	private ListView structureListView;
	private StructureListAdapter adapter;
	private AlertDialog loadingDialog;
	private AlertDialog savingDialog;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		Serializable extra= getIntent().getSerializableExtra("world");
		if (!(extra instanceof World)) {
			showToast(R.string.failed_to_get_world);
			finish();
			return;
		}
		world = (World) extra;
		
		setContentView(R.layout.export_structure);
		((TextView) findViewById(R.id.world_name_text_view)).setText(world.getWorldName());
		((TextView) findViewById(R.id.world_path_text_view)).setText(world.getWorldDir().getAbsolutePath());
		structureListView = findViewById(R.id.structure_list_view);
		
		adapter = new StructureListAdapter();
		structureListView.setAdapter(adapter);
		structureListView.setOnItemClickListener(new StructureListItemClickListener());
		loadStructures();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		try {
			if (db != null) {
				db.close();
			}
		} catch (IOException ignored) {}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if (resultCode == Activity.RESULT_OK && requestCode == 1) {
			Uri uri = data.getData();
			Uri fileUri;
			try {
				fileUri = DocumentFile.fromTreeUri(this, uri).createFile("*/*", fileName).getUri();
			} catch (Exception e) {
				showToast(getString(R.string.failed_to_create_file, e.toString()));
				e.printStackTrace();
				return;
			}
			OutputStream os;
			try {
				os = getContentResolver().openOutputStream(fileUri);
			} catch (Exception e) {
				showToast(getString(R.string.failed_to_open_output_stream, e.toString()));
				e.printStackTrace();
				return;
			}

		}
	}


	private void showToast(String msg) {
		Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
	}
	
	private void showToast(int resId) {
		Toast.makeText(this, resId, Toast.LENGTH_LONG).show();
	}

	private void saveStructure(String fileName) {
		if (!fileName.endsWith(".mcstructure")) {
			fileName += ".mcstructure";
		}
		this.fileName = fileName;
		Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
		startActivityForResult(intent, 1);
	}

	class StructureListAdapter extends BaseAdapter {
		private LayoutInflater layoutInflater = LayoutInflater.from(ExportStructureActivity.this);

		@Override
		public int getCount() {
			return structureKeys.size();
		}

		@Override
		public byte[] getItem(int position) {
			return structureKeys.get(position);
		}

		@Override
		public long getItemId(int position) {
			return position;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			if (convertView == null) {
				convertView = layoutInflater.inflate(android.R.layout.simple_list_item_1, null);
			}
			
			TextView text1 = convertView.findViewById(android.R.id.text1);
			text1.setText(new String(getItem(position)).substring(18));
			return convertView;
		}
	}

	Handler loadStructuresHandler = new Handler(Looper.myLooper()) {
		@Override
		@SuppressWarnings("unchecked")
		public void handleMessage(Message msg) {
			switch (msg.what) {
				case 0:
					Map.Entry<DB, List<byte[]>> obj = (Map.Entry<DB, List<byte[]>>) msg.obj;
					db = obj.getKey();
					structureKeys.clear();
					structureKeys.addAll(obj.getValue());
					loadingDialog.dismiss();
					adapter.notifyDataSetChanged();
					break;
				case 1:
					structureKeys.clear();
					adapter.notifyDataSetChanged();
					break;
				case 2:
					showToast(R.string.failed_to_open_db);
					finish();
					break;
			}
		}
	};

	private void loadStructures() {
		PermissionX.init(ExportStructureActivity.this)
				.permissions(
						Manifest.permission.READ_EXTERNAL_STORAGE,
						Manifest.permission.WRITE_EXTERNAL_STORAGE)
				.onExplainRequestReason(new ExplainReasonCallback() {
					@Override
					public void onExplainReason(ExplainScope scope, List<String> deniedList) {
						scope.showRequestReasonDialog(deniedList, "需要申请以下权限以加载结构", "确定");
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
							loadingDialog = new AlertDialog.Builder(ExportStructureActivity.this)
									.setCancelable(false)
									.setTitle(R.string.please_wait)
									.setView(R.layout.loading_structures_dialog)
									.setNegativeButton(R.string.cancel, null)
									.create();
							loadingDialog.show();
							loadingDialog
									.getButton(DialogInterface.BUTTON_NEGATIVE)
									.setOnClickListener(new View.OnClickListener() {
										@Override
										public void onClick(View view) {
											loadingDialog.dismiss();
											if (task != null) {
												task.cancel();
											}
										}
									});
							if (task != null) {
								task.cancel();
							}
							task = new LoadStructuresTask(loadStructuresHandler, world);
							task.start();
						}
					}
				});
	}

	static class LoadStructuresTask extends Thread {
		private final Handler handler;
		private final World world;
		private volatile DB db;
		private volatile boolean canceled;
		LoadStructuresTask(Handler handler, World world) {
			this.handler = handler;
			this.world = world;
		}

		@Override
		public void run() {
			if (canceled) return;
			Options options = new Options();
			options.filterPolicy(new BloomFilterPolicy(10));
			options.writeBufferSize(4 * 1024 * 1024);
			options.compressionType(CompressionType.ZLIB_RAW);
			try {
				db = Iq80DBFactory.factory.open(world.getDbDir(), options);
			} catch (Exception e) {
				if (canceled) return;
				handler.sendEmptyMessage(2);
				return;
			}
			List<byte[]> keys = new ArrayList<>();
			DBIterator iterator = db.iterator();
			while (true) {
				Map.Entry<byte[], byte[]> entry;
				synchronized (this) {
					if (canceled) return;
					if (!iterator.hasNext()) break;
					entry = iterator.next();
				}

				if (Util.isStructureTemplateKey(entry.getKey())) {
					keys.add(entry.getKey());
				}
			}

			Message message = new Message();
			message.what = 0;
			message.obj = new AbstractMap.SimpleEntry<>(db, keys);

			if (canceled) return;
			handler.sendMessage(message);
		}

		public synchronized void cancel() {
			if (db != null) {
				try {
					db.close();
				} catch (Exception ignored) { }
			}
			canceled = true;
			handler.sendEmptyMessage(1);
		}
	}

	Handler saveStructureHandler = new Handler(Looper.myLooper()) {
		@Override
		public void handleMessage(Message msg) {
			savingDialog.dismiss();
			switch (msg.what) {
				case 0:
					showToast(getString(R.string.structure_has_been_saved_to, msg.obj));
					break;
				case 2:
					showToast(getString(R.string.failed_to_save_structure, msg.obj.toString()));
					break;
			}
		}
	};

	private void saveStructure(OutputStream stream, String path, byte[] data) {
		savingDialog = new AlertDialog.Builder(this)
				.setCancelable(false)
				.setTitle(R.string.please_wait)
				.setView(R.layout.saving_structure_dialog)
				.show();
		new SaveStructureTask(saveStructureHandler, stream, path, data).start();
	}

	static class SaveStructureTask extends Thread {
		private final Handler handler;
		private final OutputStream stream;
		private final String path;
		private final byte[] data;

		public SaveStructureTask(Handler handler, OutputStream stream, String path, byte[] data) {
			this.handler = handler;
			this.stream = stream;
			this.path = path;
			this.data = data;
		}

		@Override
		public void run() {
			try {
				stream.write(data);
			} catch (Exception e) {
				Message message = new Message();
				message.what = 2;
				message.obj = e;
				handler.sendMessage(message);
			} finally {
				try {
					stream.close();
				} catch (Exception ignored) { }
			}
			Message message = new Message();
			message.what = 0;
			message.obj = path;
			handler.sendMessage(message);
		}
	}

	class StructureListItemClickListener implements AdapterView.OnItemClickListener {
		@Override
		@SuppressWarnings("deprecation")
		public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
			AlertDialog dialog = new AlertDialog.Builder(ExportStructureActivity.this)
					.setTitle(new String(structureKeys.get(position), StandardCharsets.UTF_8).substring(18))
					.setItems(R.array.export_structure_dialog_items, new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							switch (which) {
								case 0:
									PermissionX.init(ExportStructureActivity.this)
											.permissions(
													Manifest.permission.READ_EXTERNAL_STORAGE,
													Manifest.permission.WRITE_EXTERNAL_STORAGE)
											.onExplainRequestReason(new ExplainReasonCallback() {
												@Override
												public void onExplainReason(ExplainScope scope, List<String> deniedList) {
													scope.showRequestReasonDialog(deniedList, "需要申请以下权限以保存结构", "确定");
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
														File dir = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/games/com.mojang/structures/");
														dir.mkdirs();
														File file = new File(dir.getAbsolutePath() + File.separator + new String(structureKeys.get(position), StandardCharsets.UTF_8).substring(18).replaceAll(":", "_") + ".mcstructure");
														if (file.exists()) {
															file.delete();
														}
														try {
															file.createNewFile();
														} catch (Exception e) {
															e.printStackTrace();
															showToast(getString(R.string.failed_to_create_file, e.toString()));
															return;
														}
														FileOutputStream fos;
														try {
															fos = new FileOutputStream(file);
														} catch (Exception e) {
															e.printStackTrace();
															showToast(getString(R.string.failed_to_open_output_stream, e.toString()));
															return;
														}
														saveStructure(fos, file.getAbsolutePath(), db.get(structureKeys.get(position)));
													} else {
														//showToast();
													}
												}
											});
									break;
								case 1:
									Intent intent = new Intent(ExportStructureActivity.this, SelectWorldActivity.class);
									intent.putExtra("structureKey", structureKeys.get(position));
									intent.putExtra("structureValue", db.get(structureKeys.get(position)));
									intent.putExtra("ignoredWorld", world);
									intent.putExtra("title", getString(R.string.select_target_world));
									startActivity(intent);
									break;
							}
						}
					})
					.setNegativeButton(R.string.cancel, null)
					.show();
		}
	}
}
