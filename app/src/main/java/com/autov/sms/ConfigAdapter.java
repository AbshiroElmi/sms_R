package com.autov.sms;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.PopupMenu;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.materialswitch.MaterialSwitch;
import java.util.ArrayList;
import java.util.List;

public class ConfigAdapter extends RecyclerView.Adapter<ConfigAdapter.ViewHolder> {

    public interface OnConfigChangeListener {
        void onToggle(SmsDatabaseHelper.Config config, boolean isActive);
        void onDelete(SmsDatabaseHelper.Config config);
        void onEdit(SmsDatabaseHelper.Config config);
    }

    private List<SmsDatabaseHelper.Config> items = new ArrayList<>();
    private final OnConfigChangeListener listener;

    public ConfigAdapter(OnConfigChangeListener listener) {
        this.listener = listener;
    }

    public void setItems(List<SmsDatabaseHelper.Config> items) {
        this.items = items;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_config, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SmsDatabaseHelper.Config config = items.get(position);
        holder.tvTitle.setText(config.title);
        
        String sim = config.simIndex == 0 ? "Both" : (config.simIndex == 1 ? "SIM 1" : "SIM 2");
        String server = config.serverType == 1 ? "Server" : "Custom Server";
        if (config.configType == 1) {
            holder.tvDetails.setText("Outgoing • " + sim + " • " + server);
        } else {
            String wl = (config.whitelist == null || config.whitelist.isEmpty()) ? "All senders" : "Whitelist";
            holder.tvDetails.setText("Incoming • " + sim + " • " + server + " • " + wl);
        }
        
        holder.swActive.setOnCheckedChangeListener(null);
        holder.swActive.setChecked(config.isActive);
        holder.swActive.setOnCheckedChangeListener((b, checked) -> listener.onToggle(config, checked));
        
        holder.btnMenu.setOnClickListener(v -> {
            androidx.appcompat.widget.PopupMenu popup = new androidx.appcompat.widget.PopupMenu(v.getContext(), v, android.view.Gravity.END);
            popup.getMenuInflater().inflate(R.menu.menu_config_item, popup.getMenu());
            popup.setOnMenuItemClickListener(item -> {
                int id = item.getItemId();
                if (id == R.id.action_edit) {
                    listener.onEdit(config);
                    return true;
                } else if (id == R.id.action_delete) {
                    listener.onDelete(config);
                    return true;
                }
                return false;
            });
            popup.show();
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvDetails;
        MaterialSwitch swActive;
        ImageButton btnMenu;

        ViewHolder(View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvConfigTitle);
            tvDetails = itemView.findViewById(R.id.tvConfigDetails);
            swActive = itemView.findViewById(R.id.swConfigActive);
            btnMenu = itemView.findViewById(R.id.btnMenuConfig);
        }
    }
}
