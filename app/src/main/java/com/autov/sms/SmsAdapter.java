package com.autov.sms;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;



import java.util.ArrayList;
import java.util.List;

public class SmsAdapter extends RecyclerView.Adapter<SmsAdapter.ViewHolder> {

    public interface OnResendClickListener {
        void onResendClick(SmsRecord record);
        void onDetailClick(SmsRecord record);
    }

    private List<SmsRecord> items = new ArrayList<>();
    private OnResendClickListener listener;

    public SmsAdapter(OnResendClickListener listener) {
        this.listener = listener;
    }

    public void setItems(List<SmsRecord> items) {
        this.items = items;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_sms, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SmsRecord record = items.get(position);
        holder.tvFrom.setText("From: " + record.from);
        holder.tvBody.setText(record.body);
        holder.tvTime.setText(record.isoDate);

        holder.ivDropdown.setVisibility(View.VISIBLE);
        holder.ivDropdown.setOnClickListener(v -> showStatusMenu(v, record));

        if (record.status == SmsDatabaseHelper.STATUS_SENT) {
            holder.tvStatus.setText("SENT");
            holder.tvStatus.setBackgroundColor(Color.parseColor("#4CAF50"));
        } else if (record.status == SmsDatabaseHelper.STATUS_PENDING) {
            holder.tvStatus.setText("PENDING");
            holder.tvStatus.setBackgroundColor(Color.parseColor("#FF9800"));
        } else {
            holder.tvStatus.setText("FAILED");
            holder.tvStatus.setBackgroundColor(Color.parseColor("#F44336"));
        }
    }

    private void showStatusMenu(View v, SmsRecord record) {
        androidx.appcompat.widget.PopupMenu popup = new androidx.appcompat.widget.PopupMenu(v.getContext(), v, android.view.Gravity.END);
        popup.getMenuInflater().inflate(R.menu.menu_sms_item, popup.getMenu());
        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_detail) {
                if (listener != null) listener.onDetailClick(record);
                return true;
            } else if (id == R.id.action_resend) {
                if (listener != null) listener.onResendClick(record);
                return true;
            }
            return false;
        });
        popup.show();
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvFrom, tvBody, tvStatus, tvTime;
        android.widget.ImageView ivDropdown;


        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvFrom = itemView.findViewById(R.id.tvFrom);
            tvBody = itemView.findViewById(R.id.tvBody);
            tvStatus = itemView.findViewById(R.id.tvStatus);
            ivDropdown = itemView.findViewById(R.id.ivDropdown);
            tvTime = itemView.findViewById(R.id.tvTime);

        }
    }

    public static class SmsRecord {
        long id;
        String from;
        String body;
        long timestamp;
        int status;
        int simId;
        String isoDate;
        String response;
        String url;

        public SmsRecord(long id, String from, String body, long timestamp, int status, int simId, String isoDate, String response, String url) {
            this.id = id;
            this.from = from;
            this.body = body;
            this.timestamp = timestamp;
            this.status = status;
            this.simId = simId;
            this.isoDate = isoDate;
            this.response = response;
            this.url = url;
        }
    }
}
