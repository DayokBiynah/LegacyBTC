package com.example.legacybtc;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.common.BitMatrix;

/**
 * I show a public receiving address and a QR code containing the standard bitcoin: URI. ZXing
 * encodes the URI on the worker and a nested pixel loop produces an Android Bitmap for the
 * ImageView. Copy and Share expose only public information. Generating an address persists new
 * keys before display; previously generated addresses remain in the same wallet.
 */
public class ReceiveBitcoinActivity extends SelectedWalletActivity {
    private String displayedAddress = "";
    /**
     * Android calls this lifecycle method when creating or recreating the component. I
     * initialize the parent behavior, connect Java listeners to the XML controls or register
     * application callbacks, and restore only the workflow state explicitly handled below.
     * Button callbacks capture input on the UI thread and delegate expensive wallet work to
     * the service. Errors are displayed instead of silently continuing.
     * @param state Bundle supplied by the calling workflow as described above
     */
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); setContentView(R.layout.activity_receive);
        findViewById(R.id.copyAddressButton).setOnClickListener(view -> {
            if (displayedAddress.isEmpty()) return;
            ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText(WalletPolicy.NETWORK_LABEL + " address", displayedAddress));
            showMessage(getString(R.string.address_copied));
        });
        findViewById(R.id.generateAddressButton).setOnClickListener(view -> {
            if (walletReady) work(service::generateAddress, address -> refresh());
        });
        findViewById(R.id.shareButton).setOnClickListener(view -> {
            if (displayedAddress.isEmpty()) return;
            Intent intent = new Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, "Bitcoin " + WalletPolicy.NETWORK_LABEL + " receiving address\nbitcoin:" + displayedAddress);
            startActivity(Intent.createChooser(intent, getString(R.string.share_address)));
        });
        findViewById(R.id.backButton).setOnClickListener(view -> finish());
        loadWallet();
    }
    /**
     * I read the selected wallet's public data and update this screen's displayed address,
     * balance, plan status, or history. The conditions below avoid redundant or overlapping
     * work. Expensive repository/script operations use the worker, and their results return to
     * the main thread. Refresh does not authorize a payment or claim.
     */
    @Override protected void refresh() {
        String address = service.requireWallet().getAddress();
        if (displayedAddress.equals(address)) return;
        displayedAddress = address;
        ((TextView) findViewById(R.id.walletAddressText)).setText(address);
        ((TextView) findViewById(R.id.beneficiaryPublicKeyText)).setText(service.requireWallet().getWallet().currentReceiveKey().getPublicKeyAsHex());
        findViewById(R.id.copyBeneficiaryButton).setOnClickListener(view -> {
            String info = WalletPolicy.NETWORK_LABEL + " SegWit address: " + address + "\nCompressed public key: "
                    + service.requireWallet().getWallet().currentReceiveKey().getPublicKeyAsHex();
            ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Beneficiary information", info));
            showMessage("Public beneficiary information copied. Keep your Recovery Phrase private.");
        });
        work(() -> {
            BitMatrix matrix = new QRCodeWriter().encode("bitcoin:" + address, BarcodeFormat.QR_CODE, 512, 512);
            Bitmap bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888);
            for (int y = 0; y < 512; y++) for (int x = 0; x < 512; x++)
                bitmap.setPixel(x, y, matrix.get(x, y) ? Color.BLACK : Color.WHITE);
            return bitmap;
        }, bitmap -> ((ImageView) findViewById(R.id.qrImage)).setImageBitmap(bitmap));
    }
}
