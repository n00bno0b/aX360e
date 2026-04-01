package aenu.ax360e;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.search.SearchBar;

public class MainActivityHelper {
    
    private final MainActivity activity;
    private RecyclerView gameListRecycler;
    private GameListAdapter adapter;
    private SwipeRefreshLayout swipeRefresh;
    private MaterialButton viewToggleButton;
    private MaterialButton sortButton;
    private SearchBar searchBar;
    private Chip chipAllGames;
    private Chip chipFavorites;
    private Chip chipRecent;
    private LinearLayout emptyState;
    
    private boolean isGridView = false;
    
    public MainActivityHelper(MainActivity activity) {
        this.activity = activity;
    }
    
    public void initializeViews(View rootView) {
        gameListRecycler = rootView.findViewById(R.id.game_list);
        swipeRefresh = rootView.findViewById(R.id.swipe_refresh);
        viewToggleButton = rootView.findViewById(R.id.btn_view_toggle);
        sortButton = rootView.findViewById(R.id.btn_sort);
        searchBar = rootView.findViewById(R.id.search_bar);
        chipAllGames = rootView.findViewById(R.id.chip_all_games);
        chipFavorites = rootView.findViewById(R.id.chip_favorites);
        chipRecent = rootView.findViewById(R.id.chip_recent);
        emptyState = rootView.findViewById(R.id.empty_state);
        
        setupRecyclerView();
        setupSwipeRefresh();
        setupViewToggle();
        setupSortButton();
        setupFilterChips();
        setupSearchBar(searchBar);
    }
    
    private void setupRecyclerView() {
        if (gameListRecycler != null) {
            gameListRecycler.setLayoutManager(new LinearLayoutManager(activity));
            gameListRecycler.setHasFixedSize(true);
        }
    }
    
    public void setAdapter(GameListAdapter adapter) {
        this.adapter = adapter;
        if (gameListRecycler != null) {
            gameListRecycler.setAdapter(adapter);
        }
    }
    
    private void setupSwipeRefresh() {
        if (swipeRefresh != null) {
            swipeRefresh.setOnRefreshListener(() -> {
                // Trigger refresh in MainActivity
                activity.refresh_game_list();
                swipeRefresh.setRefreshing(false);
            });
        }
    }
    
    private void setupViewToggle() {
        if (viewToggleButton != null) {
            viewToggleButton.setOnClickListener(v -> {
                isGridView = !isGridView;
                if (adapter != null && gameListRecycler != null) {
                    adapter.setGridView(isGridView);
                    
                    if (isGridView) {
                        gameListRecycler.setLayoutManager(new GridLayoutManager(activity, 2));
                        viewToggleButton.setIconResource(R.drawable.ic_list_view);
                    } else {
                        gameListRecycler.setLayoutManager(new LinearLayoutManager(activity));
                        viewToggleButton.setIconResource(R.drawable.ic_grid_view);
                    }
                }
            });
        }
    }
    
    private void setupSortButton() {
        if (sortButton != null) {
            sortButton.setOnClickListener(v -> showSortDialog());
        }
    }
    
    private void showSortDialog() {
        String[] sortOptions = {
            "Sort by Name",
            "Sort by Recently Played",
            "Sort by Play Time"
        };
        
        new MaterialAlertDialogBuilder(activity)
            .setTitle("Sort Games")
            .setItems(sortOptions, (dialog, which) -> {
                if (adapter != null) {
                    switch (which) {
                        case 0:
                            adapter.sortByName();
                            break;
                        case 1:
                            adapter.sortByRecentlyPlayed();
                            break;
                        case 2:
                            adapter.sortByPlayTime();
                            break;
                    }
                }
            })
            .show();
    }
    
    private void setupFilterChips() {
        if (chipAllGames != null) {
            chipAllGames.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked && adapter != null) {
                    adapter.clearFilters();
                    if (chipFavorites != null) chipFavorites.setChecked(false);
                    if (chipRecent != null) chipRecent.setChecked(false);
                }
            });
        }
        
        if (chipFavorites != null) {
            chipFavorites.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked && adapter != null) {
                    adapter.filterFavorites();
                    if (chipAllGames != null) chipAllGames.setChecked(false);
                    if (chipRecent != null) chipRecent.setChecked(false);
                }
            });
        }
        
        if (chipRecent != null) {
            chipRecent.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked && adapter != null) {
                    adapter.filterRecent();
                    if (chipAllGames != null) chipAllGames.setChecked(false);
                    if (chipFavorites != null) chipFavorites.setChecked(false);
                }
            });
        }
    }
    
    public void setupSearchBar(SearchBar searchBar) {
        // SearchBar's built-in EditText handles text input
        if (searchBar != null) {
            EditText searchEditText = searchBar.findViewById(
                    com.google.android.material.R.id.open_search_view_edit_text);
            if (searchEditText != null) {
                searchEditText.addTextChangedListener(new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                        if (adapter != null) {
                            adapter.filter(s.toString());
                        }
                    }

                    @Override
                    public void afterTextChanged(Editable s) {}
                });
            }
        }
    }
    
    public void showEmptyState(boolean show) {
        if (emptyState != null) {
            emptyState.setVisibility(show ? View.VISIBLE : View.GONE);
        }
        if (gameListRecycler != null) {
            gameListRecycler.setVisibility(show ? View.GONE : View.VISIBLE);
        }
    }
}
