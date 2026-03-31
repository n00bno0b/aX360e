package aenu.ax360e;

import android.app.Activity;
import android.view.View;
import android.widget.LinearLayout;

import androidx.appcompat.widget.SearchView;
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
    }
    
    private void setupRecyclerView() {
        gameListRecycler.setLayoutManager(new LinearLayoutManager(activity));
        gameListRecycler.setHasFixedSize(true);
    }
    
    public void setAdapter(GameListAdapter adapter) {
        this.adapter = adapter;
        gameListRecycler.setAdapter(adapter);
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
                if (adapter != null) {
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
                    chipFavorites.setChecked(false);
                    chipRecent.setChecked(false);
                }
            });
        }
        
        if (chipFavorites != null) {
            chipFavorites.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked && adapter != null) {
                    adapter.filterFavorites();
                    chipAllGames.setChecked(false);
                    chipRecent.setChecked(false);
                }
            });
        }
        
        if (chipRecent != null) {
            chipRecent.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked && adapter != null) {
                    adapter.filterRecent();
                    chipAllGames.setChecked(false);
                    chipFavorites.setChecked(false);
                }
            });
        }
    }
    
    public void setupSearchBar(SearchBar searchBar) {
        if (searchBar != null) {
            searchBar.setOnClickListener(v -> {
                // TODO: Implement search view expansion
            });
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
