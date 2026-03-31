package aenu.ax360e;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;

import java.util.ArrayList;
import java.util.List;

public class GameListAdapter extends RecyclerView.Adapter<GameListAdapter.GameViewHolder> {

    private final Context context;
    private List<Emulator.GameInfo> gameList;
    private List<Emulator.GameInfo> filteredGameList;
    private OnGameClickListener clickListener;
    private boolean isGridView = false;
    private GameMetadataManager metadataManager;

    public interface OnGameClickListener {
        void onGameClick(Emulator.GameInfo game);
        void onGameLongClick(Emulator.GameInfo game);
    }

    public GameListAdapter(Context context, GameMetadataManager metadataManager) {
        this.context = context;
        this.gameList = new ArrayList<>();
        this.filteredGameList = new ArrayList<>();
        this.metadataManager = metadataManager;
    }

    public void setGameList(List<Emulator.GameInfo> games) {
        this.gameList = games;
        this.filteredGameList = new ArrayList<>(games);
        notifyDataSetChanged();
    }

    public void setOnGameClickListener(OnGameClickListener listener) {
        this.clickListener = listener;
    }

    public void setGridView(boolean isGridView) {
        this.isGridView = isGridView;
        notifyDataSetChanged();
    }

    public void filter(String query) {
        filteredGameList.clear();
        if (query.isEmpty()) {
            filteredGameList.addAll(gameList);
        } else {
            String lowerCaseQuery = query.toLowerCase();
            for (Emulator.GameInfo game : gameList) {
                if (game.name != null && game.name.toLowerCase().contains(lowerCaseQuery)) {
                    filteredGameList.add(game);
                }
            }
        }
        notifyDataSetChanged();
    }

    public void filterFavorites() {
        filteredGameList.clear();
        for (Emulator.GameInfo game : gameList) {
            GameMetadata metadata = metadataManager.getMetadata(game.uri);
            if (metadata.isFavorite) {
                filteredGameList.add(game);
            }
        }
        notifyDataSetChanged();
    }

    public void filterRecent() {
        filteredGameList.clear();
        for (Emulator.GameInfo game : gameList) {
            GameMetadata metadata = metadataManager.getMetadata(game.uri);
            if (metadata.lastPlayed > 0) {
                filteredGameList.add(game);
            }
        }
        notifyDataSetChanged();
    }

    public void sortByName() {
        filteredGameList.sort((g1, g2) -> {
            if (g1.name == null) return 1;
            if (g2.name == null) return -1;
            return g1.name.compareToIgnoreCase(g2.name);
        });
        notifyDataSetChanged();
    }

    public void sortByRecentlyPlayed() {
        filteredGameList.sort((g1, g2) -> {
            GameMetadata m1 = metadataManager.getMetadata(g1.uri);
            GameMetadata m2 = metadataManager.getMetadata(g2.uri);
            return Long.compare(m2.lastPlayed, m1.lastPlayed);
        });
        notifyDataSetChanged();
    }

    public void sortByPlayTime() {
        filteredGameList.sort((g1, g2) -> {
            GameMetadata m1 = metadataManager.getMetadata(g1.uri);
            GameMetadata m2 = metadataManager.getMetadata(g2.uri);
            return Long.compare(m2.totalPlayTime, m1.totalPlayTime);
        });
        notifyDataSetChanged();
    }

    public void clearFilters() {
        filteredGameList.clear();
        filteredGameList.addAll(gameList);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public GameViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layoutId = isGridView ? R.layout.game_item_grid : R.layout.game_item;
        View view = LayoutInflater.from(context).inflate(layoutId, parent, false);
        return new GameViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull GameViewHolder holder, int position) {
        Emulator.GameInfo game = filteredGameList.get(position);
        GameMetadata metadata = metadataManager.getMetadata(game.uri);

        // Set game name
        if (game.name != null) {
            holder.gameName.setText(game.name);
        } else {
            DocumentFile file = DocumentFile.fromSingleUri(context, Uri.parse(game.uri));
            if (file != null) {
                holder.gameName.setText(file.getName());
            }
        }

        // Set game icon
        if (game.icon != null) {
            Bitmap iconBmp = BitmapFactory.decodeByteArray(game.icon, 0, game.icon.length);
            holder.gameIcon.setImageBitmap(iconBmp);
        } else {
            holder.gameIcon.setImageResource(R.drawable.app_icon);
        }

        // Set metadata (if not in grid view)
        if (!isGridView && holder.gameMetadata != null) {
            if (metadata.lastPlayed > 0) {
                String metadataText = "Last played: " + metadata.getFormattedLastPlayed();
                if (metadata.totalPlayTime > 0) {
                    metadataText += " • " + metadata.getFormattedPlayTime();
                }
                holder.gameMetadata.setText(metadataText);
                holder.gameMetadata.setVisibility(View.VISIBLE);
            } else {
                holder.gameMetadata.setVisibility(View.GONE);
            }
        }

        // Set favorite icon
        if (holder.favoriteIcon != null) {
            holder.favoriteIcon.setVisibility(metadata.isFavorite ? View.VISIBLE : View.GONE);
        }

        // Set compatibility badge (if not in grid view)
        if (!isGridView && holder.compatibilityBadge != null) {
            if (!metadata.compatibilityRating.equals("unknown")) {
                holder.compatibilityBadge.setText(getCompatibilityText(metadata.compatibilityRating));
                holder.compatibilityBadge.setChipBackgroundColorResource(getCompatibilityColor(metadata.compatibilityRating));
                holder.compatibilityBadge.setVisibility(View.VISIBLE);
            } else {
                holder.compatibilityBadge.setVisibility(View.GONE);
            }
        }

        // Click listeners
        holder.cardView.setOnClickListener(v -> {
            if (clickListener != null) {
                clickListener.onGameClick(game);
            }
        });

        holder.cardView.setOnLongClickListener(v -> {
            if (clickListener != null) {
                clickListener.onGameLongClick(game);
            }
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return filteredGameList.size();
    }

    private String getCompatibilityText(String rating) {
        switch (rating) {
            case "excellent": return "Excellent";
            case "good": return "Good";
            case "playable": return "Playable";
            case "issues": return "Issues";
            default: return "";
        }
    }

    private int getCompatibilityColor(String rating) {
        switch (rating) {
            case "excellent": return R.color.performance_excellent;
            case "good": return R.color.performance_good;
            case "playable": return R.color.performance_moderate;
            case "issues": return R.color.performance_poor;
            default: return R.color.performance_moderate;
        }
    }

    static class GameViewHolder extends RecyclerView.ViewHolder {
        MaterialCardView cardView;
        ImageView gameIcon;
        TextView gameName;
        TextView gameMetadata;
        Chip compatibilityBadge;
        ImageView favoriteIcon;

        public GameViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = (MaterialCardView) itemView;
            gameIcon = itemView.findViewById(R.id.game_icon);
            gameName = itemView.findViewById(R.id.game_name);
            gameMetadata = itemView.findViewById(R.id.game_metadata);
            compatibilityBadge = itemView.findViewById(R.id.compatibility_badge);
            favoriteIcon = itemView.findViewById(R.id.favorite_icon);
        }
    }
}
