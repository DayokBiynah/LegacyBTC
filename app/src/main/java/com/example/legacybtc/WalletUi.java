package com.example.legacybtc;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.text.DateFormat;

/** Shared presentation helpers; all wallet and recovery decisions stay in the existing services. */
final class WalletUi {
    private WalletUi() { }

    /**
     * I strip the correct network-specific prefix and extension from a public storage filename
     * and show a short identifier using the Android String resource. This avoids showing the
     * same mainnet prefix for every Mainnet wallet.
     * @param context Context supplied by the calling workflow as described above
     * @param file String supplied by the calling workflow as described above
     * @return a short public filename identifier formatted with an Android resource
     */
    static String walletName(Context context, String file) {
        String identifier = file.replace("legacybtc_wallet_mainnet_", "").replace("legacybtc_wallet_", "").replace(".enc", "");
        return context.getString(R.string.wallet_identifier, identifier.substring(0, Math.min(8, identifier.length())));
    }

    /**
     * I inflate the shared empty-state XML row and place the requested Android String resource
     * into its message field. The returned View is added to the wallet, plan, or transaction
     * container without creating artificial data.
     * @param parent ViewGroup supplied by the calling workflow as described above
     * @param message int supplied by the calling workflow as described above
     * @return the inflated XML empty-state row
     */
    static View empty(ViewGroup parent, int message) {
        View row = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_empty_state, parent, false);
        ((TextView) row.findViewById(R.id.emptyMessage)).setText(message);
        return row;
    }

    /**
     * I inflate one XML transaction row from a public BitcoinTransaction, display its type,
     * signed amount, confirmation status, date, and ID, and connect the details Runnable. The
     * returned View goes into History; amount sign and text communicate direction in addition
     * to color.
     * @param parent ViewGroup supplied by the calling workflow as described above
     * @param record BitcoinTransaction supplied by the calling workflow as described above
     * @param details Runnable supplied by the calling workflow as described above
     * @return the populated XML transaction row with its details listener
     */
    static View transaction(ViewGroup parent, BitcoinTransaction record, Runnable details) {
        View row = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_transaction, parent, false);
        ((TextView) row.findViewById(R.id.transactionTypeText)).setText(record.getType());
        TextView amount = row.findViewById(R.id.transactionAmountText);
        amount.setText(parent.getContext().getString(R.string.btc_amount, record.getAmount().toPlainString()));
        // The type and signed amount communicate direction even without color.
        amount.setTextColor(parent.getContext().getColor(record.getAmount().isPositive() ? R.color.ui_success : R.color.ui_text));
        ((TextView) row.findViewById(R.id.transactionStatusText)).setText(record.getStatus());
        ((TextView) row.findViewById(R.id.transactionDateText)).setText(DateFormat.getDateTimeInstance().format(record.getDate()));
        ((TextView) row.findViewById(R.id.transactionIdText)).setText(record.getId());
        row.findViewById(R.id.transactionDetailsButton).setOnClickListener(view -> details.run());
        return row;
    }

    /**
     * I inflate one XML plan row with the beneficiary name and covered BTC amount, then attach
     * the selected-plan Runnable. The returned View goes into the setup or recovery list.
     * Displaying a row does not grant recovery permission.
     * @param parent ViewGroup supplied by the calling workflow as described above
     * @param plan InheritancePlan supplied by the calling workflow as described above
     * @param select Runnable supplied by the calling workflow as described above
     * @return the populated XML plan row with its selection listener
     */
    static View plan(ViewGroup parent, InheritancePlan plan, Runnable select) {
        View row = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_plan, parent, false);
        ((TextView) row.findViewById(R.id.planNameText)).setText(plan.getBeneficiary().getName());
        ((TextView) row.findViewById(R.id.planAmountText)).setText(parent.getContext().getString(R.string.btc_amount, plan.getAmount().toPlainString()));
        row.findViewById(R.id.selectPlanButton).setOnClickListener(view -> select.run());
        return row;
    }

    /**
     * I map the InheritancePlan.Status enum to public text for the dashboard and recovery
     * screen. Selection distinguishes draft, unverified, locked, available, pending spend,
     * spent, and cancelled states. The underlying service, not this formatter, determines
     * eligibility.
     * @param status InheritancePlan.Status supplied by the calling workflow as described above
     * @return public text corresponding to the verified status enum
     */
    static String status(InheritancePlan.Status status) {
        switch (status) {
            case DRAFT: return "Draft · Not funded";
            case UNVERIFIED: return "Awaiting verification";
            case FUNDING_PENDING: return "Funding awaiting confirmation";
            case TIMELOCKED: return "Locked by Timelock";
            case AVAILABLE: return "Timelock satisfied";
            case SPEND_PENDING: return "Spend awaiting confirmation";
            case SPENT: return "Funds spent";
            case CANCELLED: return "Cancelled";
            default: return "Unknown";
        }
    }
}
