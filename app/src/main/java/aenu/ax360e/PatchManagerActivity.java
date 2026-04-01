// SPDX-License-Identifier: WTFPL
package aenu.ax360e;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PatchManagerActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private TextView emptyView;
    private EditText searchEditText;
    private PatchListAdapter adapter;
    private List<PatchInfo.GamePatchFile> allPatches;
    private List<PatchInfo.GamePatchFile> filteredPatches;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_patch_manager);

        setTitle("Patch Manager");

        recyclerView = findViewById(R.id.patch_recycler_view);
        emptyView = findViewById(R.id.empty_view);
        searchEditText = findViewById(R.id.search_edit_text);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        allPatches = new ArrayList<>();
        filteredPatches = new ArrayList<>();
        adapter = new PatchListAdapter(filteredPatches);
        recyclerView.setAdapter(adapter);

        setupSearch();
        loadPatches();
    }

    private void setupSearch() {
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterPatches(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void loadPatches() {
        executor.execute(() -> {
            File patchesDir = new File(Application.get_app_data_dir(), "patches");
            List<PatchInfo.GamePatchFile> patches = PatchTomlParser.getAllPatches(patchesDir);

            mainHandler.post(() -> {
                allPatches.clear();
                allPatches.addAll(patches);
                filteredPatches.clear();
                filteredPatches.addAll(patches);
                adapter.notifyDataSetChanged();
                updateEmptyView();
            });
        });
    }

    private void filterPatches(String query) {
        filteredPatches.clear();

        if (query.isEmpty()) {
            filteredPatches.addAll(allPatches);
        } else {
            String lowerQuery = query.toLowerCase();
            for (PatchInfo.GamePatchFile gamePatch : allPatches) {
                String titleName = gamePatch.titleName != null ? gamePatch.titleName : "";
                String titleId = gamePatch.titleId != null ? gamePatch.titleId : "";
                boolean matches = titleName.toLowerCase().contains(lowerQuery) ||
                                titleId.toLowerCase().contains(lowerQuery);

                if (!matches) {
                    // Check if any patch name matches
                    for (PatchInfo patch : gamePatch.patches) {
                        String patchName = patch.patchName != null ? patch.patchName : "";
                        if (patchName.toLowerCase().contains(lowerQuery)) {
                            matches = true;
                            break;
                        }
                    }
                }

                if (matches) {
                    filteredPatches.add(gamePatch);
                }
            }
        }

        adapter.notifyDataSetChanged();
        updateEmptyView();
    }

    private void updateEmptyView() {
        if (filteredPatches.isEmpty()) {
            recyclerView.setVisibility(View.GONE);
            emptyView.setVisibility(View.VISIBLE);
        } else {
            recyclerView.setVisibility(View.VISIBLE);
            emptyView.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }

    // RecyclerView Adapter
    private class PatchListAdapter extends RecyclerView.Adapter<PatchListAdapter.GamePatchViewHolder> {
        private final List<PatchInfo.GamePatchFile> gamePatches;

        PatchListAdapter(List<PatchInfo.GamePatchFile> gamePatches) {
            this.gamePatches = gamePatches;
        }

        @NonNull
        @Override
        public GamePatchViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_game_patches, parent, false);
            return new GamePatchViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull GamePatchViewHolder holder, int position) {
            holder.bind(gamePatches.get(position));
        }

        @Override
        public int getItemCount() {
            return gamePatches.size();
        }

        class GamePatchViewHolder extends RecyclerView.ViewHolder {
            private final TextView gameTitle;
            private final TextView titleId;
            private final LinearLayout patchesContainer;

            GamePatchViewHolder(View itemView) {
                super(itemView);
                gameTitle = itemView.findViewById(R.id.game_title);
                titleId = itemView.findViewById(R.id.title_id);
                patchesContainer = itemView.findViewById(R.id.patches_container);
            }

            void bind(PatchInfo.GamePatchFile gamePatch) {
                gameTitle.setText(gamePatch.titleName);
                titleId.setText("Title ID: " + gamePatch.titleId);

                patchesContainer.removeAllViews();

                for (PatchInfo patch : gamePatch.patches) {
                    View patchView = LayoutInflater.from(itemView.getContext())
                            .inflate(R.layout.item_patch_entry, patchesContainer, false);

                    CheckBox checkBox = patchView.findViewById(R.id.patch_checkbox);
                    TextView patchName = patchView.findViewById(R.id.patch_name);
                    TextView patchDesc = patchView.findViewById(R.id.patch_description);
                    TextView patchAuthor = patchView.findViewById(R.id.patch_author);
                    TextView dataCount = patchView.findViewById(R.id.data_count);

                    patchName.setText(patch.patchName);
                    patchDesc.setText(patch.getShortDescription());
                    patchAuthor.setText("By: " + (patch.author != null ? patch.author : "Unknown"));
                    dataCount.setText(patch.getDataEntryCount() + " changes");

                    checkBox.setChecked(patch.isEnabled);
                    checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                        onPatchToggled(patch, isChecked);
                    });

                    // Click on entire row to see details
                    patchView.setOnClickListener(v -> {
                        showPatchDetails(patch);
                    });

                    patchesContainer.addView(patchView);
                }
            }
        }
    }

    private void onPatchToggled(PatchInfo patch, boolean isEnabled) {
        executor.execute(() -> {
            File file = new File(patch.filePath);
            boolean success = PatchTomlParser.updatePatchEnabled(file, patch.patchName, isEnabled);

            mainHandler.post(() -> {
                if (success) {
                    patch.isEnabled = isEnabled;
                    Toast.makeText(this,
                        patch.patchName + (isEnabled ? " enabled" : " disabled"),
                        Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this,
                        "Failed to update patch",
                        Toast.LENGTH_SHORT).show();
                    // Reload to revert UI
                    loadPatches();
                }
            });
        });
    }

    private void showPatchDetails(PatchInfo patch) {
        StringBuilder details = new StringBuilder();
        details.append("Name: ").append(patch.patchName).append("\n\n");
        details.append("Description: ").append(patch.description != null ? patch.description : "None").append("\n\n");
        details.append("Author: ").append(patch.author != null ? patch.author : "Unknown").append("\n\n");
        details.append("Data Changes: ").append(patch.getDataEntryCount()).append("\n\n");

        if (!patch.dataEntries.isEmpty()) {
            details.append("Memory Modifications:\n");
            int count = 0;
            for (PatchInfo.PatchDataEntry entry : patch.dataEntries) {
                if (count++ < 10) { // Limit to first 10
                    details.append("  • ").append(entry.dataType)
                            .append(" @ ").append(entry.address)
                            .append(" = ").append(entry.value).append("\n");
                }
            }
            if (patch.dataEntries.size() > 10) {
                details.append("  ... and ").append(patch.dataEntries.size() - 10).append(" more\n");
            }
        }

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Patch Details")
                .setMessage(details.toString())
                .setPositiveButton("OK", null)
                .show();
    }
}
